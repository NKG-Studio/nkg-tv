package studio.nkg.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Rect;
import android.media.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Offline, full-frame neural pipeline: decode every frame, infer at source
 * resolution, encode 2x with original timestamps, remux original audio.
 * Never downsample or silently drop frames. No real-time claim.
 */
final class VideoEnhancer {
    interface Progress { void update(int frames,long ptsUs,long durationUs,long elapsedMs); }
    private final Context context;
    private final AtomicBoolean cancelled;
    VideoEnhancer(Context context,AtomicBoolean cancelled){this.context=context;this.cancelled=cancelled;}
    private void check() throws IOException {if(cancelled.get())throw new IOException("已取消增强");}
    private MediaExtractor open(String source)throws IOException{
        MediaExtractor ex=new MediaExtractor();
        try{
            if(source.equals("asset:demo.mp4")){try(AssetFileDescriptor fd=context.getAssets().openFd("demo.mp4")){ex.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}}
            else if(source.equals("asset:enhance-test.mp4")){try(AssetFileDescriptor fd=context.getAssets().openFd("enhance-test.mp4")){ex.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}}
            else {Net.checked(source);ex.setDataSource(source,new HashMap<>());}
            return ex;
        }catch(Exception e){ex.release();throw new IOException("无法打开渐进式 MP4："+e.getMessage(),e);}
    }
    File run(String source,Progress progress)throws Exception{
        check();
        File dir=new File(context.getFilesDir(),"enhanced");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建输出目录");
        File output=File.createTempFile("nkg-2x-",".mp4",dir);
        MediaExtractor video=null,audio=null;MediaCodec decoder=null,encoder=null;MediaMuxer muxer=null;
        boolean ok=false,muxStarted=false;int videoOut=-1,audioOut=-1;
        try{
            video=open(source);if(video.getDrmInitData()!=null)throw new IOException("不支持 DRM 内容");
            int track=-1,audioTrack=-1;MediaFormat format=null,audioFormat=null;
            for(int i=0;i<video.getTrackCount();i++){MediaFormat f=video.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/")&&track<0){track=i;format=f;}if(mime!=null&&mime.startsWith("audio/")&&audioTrack<0){audioTrack=i;audioFormat=f;}}
            if(format==null)throw new IOException("没有视频轨");
            int w=format.getInteger(MediaFormat.KEY_WIDTH),h=format.getInteger(MediaFormat.KEY_HEIGHT);
            if(w<2||h<2||w>320||h>180||(w&1)!=0||(h&1)!=0)throw new IOException("当前完整帧增强仅支持偶数尺寸、最高 320×180；不会先降低原片分辨率");
            int transfer=format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)?format.getInteger(MediaFormat.KEY_COLOR_TRANSFER):MediaFormat.COLOR_TRANSFER_SDR_VIDEO;
            if(transfer==MediaFormat.COLOR_TRANSFER_ST2084||transfer==MediaFormat.COLOR_TRANSFER_HLG)throw new IOException("暂不支持 HDR");
            long duration=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):0;
            if(duration<=0||duration>3600_000_000L)throw new IOException("需要时长有效且不超过一小时的渐进式 MP4");
            long estimate=duration/1_000_000*300_000+20_000_000;if(dir.getUsableSpace()<estimate)throw new IOException("增强副本所需空间不足");
            boolean bt709=format.containsKey(MediaFormat.KEY_COLOR_STANDARD)&&format.getInteger(MediaFormat.KEY_COLOR_STANDARD)==MediaFormat.COLOR_STANDARD_BT709;
            boolean full=format.containsKey(MediaFormat.KEY_COLOR_RANGE)&&format.getInteger(MediaFormat.KEY_COLOR_RANGE)==MediaFormat.COLOR_RANGE_FULL;
            video.selectTrack(track);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));decoder.configure(format,null,null,0);decoder.start();
            MediaFormat enc=MediaFormat.createVideoFormat("video/avc",w*2,h*2);
            enc.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            enc.setInteger(MediaFormat.KEY_BIT_RATE,2_000_000);enc.setInteger(MediaFormat.KEY_FRAME_RATE,format.containsKey(MediaFormat.KEY_FRAME_RATE)?format.getInteger(MediaFormat.KEY_FRAME_RATE):24);enc.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            enc.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709);enc.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED);enc.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            encoder=MediaCodec.createEncoderByType("video/avc");encoder.configure(enc,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();
            muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if(format.containsKey(MediaFormat.KEY_ROTATION))muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION));
            boolean inputDone=false,decodeDone=false,encodeDone=false,encoderEos=false;int frames=0;long lastPts=0,start=SystemClock.elapsedRealtime(),lastActivity=start;
            MediaCodec.BufferInfo decoded=new MediaCodec.BufferInfo(),encoded=new MediaCodec.BufferInfo();
            while(!encodeDone){
                check();boolean worked=false;
                // Drain encoder first to avoid filling both codec queues.
                int eo=encoder.dequeueOutputBuffer(encoded,1000);
                if(eo==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    videoOut=muxer.addTrack(encoder.getOutputFormat());if(audioFormat!=null)audioOut=muxer.addTrack(audioFormat);muxer.start();muxStarted=true;worked=true;
                }else if(eo>=0){
                    if(encoded.size>0&&(encoded.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){if(!muxStarted)throw new IOException("编码格式尚未就绪");muxer.writeSampleData(videoOut,encoder.getOutputBuffer(eo),encoded);}
                    encodeDone=(encoded.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;encoder.releaseOutputBuffer(eo,false);worked=true;
                }
                if(!inputDone){
                    int in=decoder.dequeueInputBuffer(1000);if(in>=0){ByteBuffer buf=decoder.getInputBuffer(in);int size=video.readSampleData(buf,0);
                        if(size<0){decoder.queueInputBuffer(in,0,0,lastPts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                        else{lastPts=video.getSampleTime();decoder.queueInputBuffer(in,0,size,lastPts,flags(video.getSampleFlags()));video.advance();}worked=true;}
                }
                if(!decodeDone){
                    int out=decoder.dequeueOutputBuffer(decoded,1000);
                    if(out>=0){
                        if(decoded.size>0){
                            int[] rgb;try(Image image=decoder.getOutputImage(out)){if(image==null)throw new IOException("解码器不提供可读 YUV 帧");rgb=readImage(image,w,h,bt709,full);}
                            long pts=decoded.presentationTimeUs;decoder.releaseOutputBuffer(out,false);out=-1;
                            int[] enhanced=Core.upscaleAnime(rgb,w,h);check();if(enhanced==null)throw new IOException("神经网络推理失败");
                            int ei;long queueDeadline=SystemClock.elapsedRealtime()+30000;while((ei=encoder.dequeueInputBuffer(1000))<0){check();if(SystemClock.elapsedRealtime()>queueDeadline)throw new IOException("编码输入超时");int o=encoder.dequeueOutputBuffer(encoded,1000);
                                if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){videoOut=muxer.addTrack(encoder.getOutputFormat());if(audioFormat!=null)audioOut=muxer.addTrack(audioFormat);muxer.start();muxStarted=true;}
                                else if(o>=0){if(encoded.size>0&&(encoded.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0)muxer.writeSampleData(videoOut,encoder.getOutputBuffer(o),encoded);encoder.releaseOutputBuffer(o,false);}
                            }
                            try(Image image=encoder.getInputImage(ei)){if(image==null)throw new IOException("编码器不支持 YUV 图像输入");writeImage(image,enhanced,w*2,h*2);}
                            encoder.queueInputBuffer(ei,0,w*h*6,pts,0);frames++;progress.update(frames,pts,duration,SystemClock.elapsedRealtime()-start);
                        }
                        if((decoded.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){decodeDone=true;}
                        if(out>=0)decoder.releaseOutputBuffer(out,false);worked=true;
                    }
                }
                if(decodeDone&&!inputDone)throw new IOException("异常解码结束");
                if(decodeDone&&!encoderEos){int ei=encoder.dequeueInputBuffer(1000);if(ei>=0){encoder.queueInputBuffer(ei,0,0,lastPts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);encoderEos=true;worked=true;}}
                if(worked)lastActivity=SystemClock.elapsedRealtime();
                if(SystemClock.elapsedRealtime()-lastActivity>30000)throw new IOException("编解码器等待超时");
            }
            if(audioOut>=0){
                audio=open(source);audio.selectTrack(audioTrack);ByteBuffer buffer=ByteBuffer.allocateDirect(2*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                while(true){check();int size=audio.readSampleData(buffer,0);if(size<0)break;info.set(0,size,audio.getSampleTime(),flags(audio.getSampleFlags()));muxer.writeSampleData(audioOut,buffer,info);audio.advance();}
            }
            muxer.stop();muxStarted=false;ok=true;return output;
        }finally{
            if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}
            if(encoder!=null){try{encoder.stop();}catch(Exception ignored){}encoder.release();}
            if(video!=null)video.release();if(audio!=null)audio.release();
            if(muxer!=null){if(muxStarted)try{muxer.stop();}catch(Exception ignored){}muxer.release();}
            if(!ok)output.delete();
        }
    }
    private static int flags(int flags)throws IOException{if((flags&(MediaExtractor.SAMPLE_FLAG_ENCRYPTED|MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME))!=0)throw new IOException("不支持加密或不完整样本");return (flags&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0;}
    private static int clamp(double x){return Math.max(0,Math.min(255,(int)Math.round(x)));}
    private static int sample(Image.Plane p,int x,int y){return p.getBuffer().get(y*p.getRowStride()+x*p.getPixelStride())&255;}
    static int[] readImage(Image image,int w,int h,boolean bt709,boolean full)throws IOException{
        Rect crop=image.getCropRect();if(crop.width()!=w||crop.height()!=h)throw new IOException("不支持动态分辨率或裁剪变化");
        Image.Plane[] p=image.getPlanes();int[] out=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int sx=x+crop.left,sy=y+crop.top;double yy=sample(p[0],sx,sy),u=sample(p[1],sx/2,sy/2)-128,v=sample(p[2],sx/2,sy/2)-128;
            if(!full){yy=(yy-16)*255/219;u=u*255/224;v=v*255/224;}
            int r=clamp(yy+(bt709?1.5748:1.402)*v),g=clamp(yy-(bt709?.187324:.344136)*u-(bt709?.468124:.714136)*v),b=clamp(yy+(bt709?1.8556:1.772)*u);
            out[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }return out;
    }
    private static void put(Image.Plane p,int x,int y,int value){p.getBuffer().put(y*p.getRowStride()+x*p.getPixelStride(),(byte)value);}
    static void writeImage(Image image,int[] rgb,int w,int h){
        Image.Plane[] p=image.getPlanes();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=rgb[y*w+x];double r=(c>>16)&255,g=(c>>8)&255,b=c&255;put(p[0],x,y,clamp(16+(.2126*r+.7152*g+.0722*b)*219/255));}
        for(int y=0;y<h;y+=2)for(int x=0;x<w;x+=2){double r=0,g=0,b=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int c=rgb[(y+dy)*w+x+dx];r+=((c>>16)&255)/4.;g+=((c>>8)&255)/4.;b+=(c&255)/4.;}put(p[1],x/2,y/2,clamp(128+(-.114572*r-.385428*g+.5*b)*224/255));put(p[2],x/2,y/2,clamp(128+(.5*r-.454153*g-.045847*b)*224/255));}
    }
}

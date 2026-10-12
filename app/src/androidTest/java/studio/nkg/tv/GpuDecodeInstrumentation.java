package studio.nkg.tv;
import android.app.Instrumentation;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
/** Decoder-original YUV -> Vulkan color conversion/CNN -> readback correctness.
 * This runner does not present video or audio and cannot pass playback FPS.
 */
public final class GpuDecodeInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();int code=0;MediaExtractor ex=null;MediaCodec decoder=null;GpuCore gpu=null;
  try{
   boolean rejected=false;try(GpuCore hardware=new GpuCore(getTargetContext(),false)){hardware.device();}catch(IllegalStateException expected){rejected=expected.getMessage().contains("software Vulkan");}
   gpu=new GpuCore(getTargetContext(),true);JSONObject device=new JSONObject(gpu.device());if(device.getBoolean("software")&&!rejected)throw new AssertionError("software passed hardware gate");
   ex=new MediaExtractor();try(AssetFileDescriptor fd=getTargetContext().getAssets().openFd("motion-cut-test.mp4")){ex.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
   int track=-1;MediaFormat format=null;for(int i=0;i<ex.getTrackCount();i++){MediaFormat f=ex.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("video/")){track=i;format=f;break;}}
   if(format==null)throw new AssertionError("no video");ex.selectTrack(track);int w=format.getInteger(MediaFormat.KEY_WIDTH),h=format.getInteger(MediaFormat.KEY_HEIGHT);int[] color=VideoEnhancer.color(format,new int[]{MediaFormat.COLOR_STANDARD_BT601_PAL,MediaFormat.COLOR_RANGE_LIMITED,MediaFormat.COLOR_TRANSFER_SDR_VIDEO});
   format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));decoder.configure(format,null,null,0);decoder.start();boolean inDone=false,outDone=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();JSONArray frames=new JSONArray();long lastPts=-1;int maxError=0;long wallStart=SystemClock.elapsedRealtime(),idle=wallStart;
   while(!outDone){boolean worked=false;
    if(!inDone){int in=decoder.dequeueInputBuffer(1000);if(in>=0){int n=ex.readSampleData(decoder.getInputBuffer(in),0);if(n<0){decoder.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true;}else{decoder.queueInputBuffer(in,0,n,ex.getSampleTime(),0);ex.advance();}worked=true;}}
    int out=decoder.dequeueOutputBuffer(info,1000);if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){color=VideoEnhancer.color(decoder.getOutputFormat(),color);worked=true;}
    else if(out>=0){if(info.size>0){long pts=info.presentationTimeUs;if(pts<=lastPts)throw new AssertionError("duplicate/out of order source PTS");lastPts=pts;
      int[] actual,reference;long elapsed;try(Image image=decoder.getOutputImage(out)){if(image==null)throw new AssertionError("decoder image missing");long t=SystemClock.elapsedRealtime();actual=gpu.infer(image,w,h,color);elapsed=SystemClock.elapsedRealtime()-t;int[] rgb=VideoEnhancer.readImage(image,w,h,color[0]==MediaFormat.COLOR_STANDARD_BT709,color[1]==MediaFormat.COLOR_RANGE_FULL);reference=Core.upscale(rgb,w,h);}
      if(actual==null||reference==null||actual.length!=w*h*4||reference.length!=actual.length)throw new AssertionError("output shape");int error=0;for(int i=0;i<actual.length;i++)for(int shift:new int[]{0,8,16,24})error=Math.max(error,Math.abs(((actual[i]>>>shift)&255)-((reference[i]>>>shift)&255)));maxError=Math.max(maxError,error);
      if(error>2)throw new AssertionError("GPU YUV/Rust reference mismatch "+error);frames.put(new JSONObject().put("pts_us",pts).put("gpu_upload_compute_readback_ms",elapsed).put("max_channel_error",error).put("pss_kib",Debug.getPss()));
      Bundle progress=new Bundle();progress.putString("stream","GPU_FRAME "+frames.length()+" pts="+pts+" error="+error+"\n");sendStatus(0,progress);
     }outDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;decoder.releaseOutputBuffer(out,false);worked=true;}
    if(worked)idle=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-idle>30000)throw new AssertionError("decoder timeout");
   }
   if(frames.length()!=16)throw new AssertionError("not every source frame processed");
   result.putString("stream",new JSONObject().put("passed",true).put("device",device).put("software_rejected_by_hardware_gate",rejected).put("model","original synthetic-data TinySR2 FP32 prototype").put("input_width",w).put("input_height",h).put("output_width",w*2).put("output_height",h*2).put("frames",frames).put("max_channel_error",maxError).put("total_with_reference_validation_ms",SystemClock.elapsedRealtime()-wallStart).put("presented_frames",JSONObject.NULL).put("presented_fps",JSONObject.NULL).put("playback_acceptance",false).put("scope","MediaCodec original YUV -> Vulkan conversion and CNN -> CPU readback. No CPU RGB conversion in GPU path. CPU reference validation is separate. Audio/render pipeline and target hardware performance not validated.").toString());
  }catch(Throwable e){code=1;result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));}
  finally{if(gpu!=null)gpu.close();if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}if(ex!=null)ex.release();}
  finish(code,result);
 }
}

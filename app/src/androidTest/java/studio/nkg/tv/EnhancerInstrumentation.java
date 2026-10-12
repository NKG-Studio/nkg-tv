package studio.nkg.tv;
import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
public final class EnhancerInstrumentation extends Instrumentation {
 private Bundle arguments;
 @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
 private JSONArray tracks(MediaExtractor ex)throws Exception{
  JSONArray result=new JSONArray();
  for(int i=0;i<ex.getTrackCount();i++){
   ex.selectTrack(i);MediaFormat f=ex.getTrackFormat(i);JSONArray pts=new JSONArray();MessageDigest hash=MessageDigest.getInstance("SHA-256");ByteBuffer b=ByteBuffer.allocate(1024*1024);
   while(true){int n=ex.readSampleData(b,0);if(n<0)break;byte[] data=new byte[n];b.position(0);b.get(data);hash.update(data);pts.put(ex.getSampleTime());ex.advance();b.clear();}
   StringBuilder h=new StringBuilder();for(byte v:hash.digest())h.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
   JSONObject o=new JSONObject().put("mime",f.getString(MediaFormat.KEY_MIME)).put("pts",pts).put("sha256",h.toString());if(f.containsKey(MediaFormat.KEY_WIDTH))o.put("width",f.getInteger(MediaFormat.KEY_WIDTH)).put("height",f.getInteger(MediaFormat.KEY_HEIGHT));result.put(o);
   ex.unselectTrack(i);ex.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
  }return result;
 }
 @Override public void onStart(){
  Bundle result=new Bundle();int code=0;File out=null;
  try{
   Context c=getTargetContext();File dir=new File(c.getFilesDir(),"test-models");dir.mkdirs();
   for(String n:new String[]{"realesr-animevideov3-x2.param","realesr-animevideov3-x2.bin"})try(InputStream in=c.getAssets().open("models/"+n);OutputStream o=new FileOutputStream(new File(dir,n))){byte[] b=new byte[8192];int size;while((size=in.read(b))!=-1)o.write(b,0,size);}
   if(!Core.initModel(dir.getAbsolutePath()))throw new AssertionError("model load");
   boolean stress=arguments!=null&&"true".equals(arguments.getString("stress"));
   final String fixture=stress?"motion-cut-test.mp4":"enhance-test.mp4";
   final int expectedFrames=stress?16:4;
   JSONObject failureChecks=new JSONObject();
   if(stress){
    File outputs=new File(c.getFilesDir(),"enhanced");int baseline=outputs.list()==null?0:outputs.list().length;
    AtomicBoolean stop=new AtomicBoolean();long cancelledStart=SystemClock.elapsedRealtime();
    boolean stopped=false;try{new VideoEnhancer(c,stop).run("asset:"+fixture,(frames,p,d,elapsed)->{if(frames==2)stop.set(true);});}catch(IOException expected){stopped=expected.getMessage().contains("取消");}
    if(!stopped||(outputs.list()==null?0:outputs.list().length)!=baseline)throw new AssertionError("mid-run cancel cleanup");
    failureChecks.put("cancel_after_frames",2).put("cancel_elapsed_ms",SystemClock.elapsedRealtime()-cancelledStart).put("cancel_partial_file_removed",true);
    boolean failed=false;try{new VideoEnhancer(c,new AtomicBoolean()).run("asset:missing.mp4",(a,b,d,e)->{});}catch(IOException expected){failed=true;}
    if(!failed||outputs.list().length!=baseline)throw new AssertionError("failure cleanup");failureChecks.put("invalid_source_partial_file_removed",true);
   }
   long start=SystemClock.elapsedRealtime();AtomicInteger count=new AtomicInteger();
   AtomicBoolean sampleRunning=new AtomicBoolean(true);AtomicLong peakPss=new AtomicLong(Debug.getPss());AtomicInteger samples=new AtomicInteger();
   Thread sampler=new Thread(()->{while(sampleRunning.get()){peakPss.accumulateAndGet(Debug.getPss(),Math::max);samples.incrementAndGet();try{Thread.sleep(1000);}catch(InterruptedException e){return;}}},"enhancer-memory-sampler");sampler.setDaemon(true);sampler.start();
   long processCpuStart=android.os.Process.getElapsedCpuTime();
   if(arguments!=null&&arguments.containsKey("reuse")){out=new File(arguments.getString("reuse"));count.set(4);}else out=new VideoEnhancer(c,new AtomicBoolean()).run("asset:"+fixture,(frames,p,d,elapsed)->{count.set(frames);Bundle b=new Bundle();b.putString("stream","ENHANCED frame="+frames+" pts="+p+" elapsed="+elapsed+"\n");sendStatus(0,b);});
   sampleRunning.set(false);sampler.interrupt();sampler.join(2000);long inferenceElapsed=SystemClock.elapsedRealtime()-start;
   MediaExtractor source=new MediaExtractor(),output=new MediaExtractor();try(AssetFileDescriptor f=c.getAssets().openFd(fixture)){source.setDataSource(f.getFileDescriptor(),f.getStartOffset(),f.getLength());}
   output.setDataSource(out.getAbsolutePath());JSONArray before=tracks(source),after=tracks(output);source.release();output.release();
   if(count.get()!=expectedFrames)throw new AssertionError("must process every frame");
   JSONObject inVideo=before.getJSONObject(0),outVideo=after.getJSONObject(0);
   if(outVideo.getInt("width")!=128||outVideo.getInt("height")!=72)throw new AssertionError("2x dimensions");
   if(!inVideo.getJSONArray("pts").toString().equals(outVideo.getJSONArray("pts").toString()))throw new AssertionError("video timestamps changed");
   if(!before.getJSONObject(1).getString("sha256").equals(after.getJSONObject(1).getString("sha256")))throw new AssertionError("audio packet payload changed");
   if(!before.getJSONObject(1).getJSONArray("pts").toString().equals(after.getJSONObject(1).getJSONArray("pts").toString()))throw new AssertionError("audio timestamps changed");
   MediaMetadataRetriever retriever=new MediaMetadataRetriever();retriever.setDataSource(out.getAbsolutePath());
   android.graphics.Bitmap frame=retriever.getFrameAtTime(250000,MediaMetadataRetriever.OPTION_CLOSEST);retriever.release();
   if(frame==null||frame.getWidth()!=128||frame.getHeight()!=72)throw new AssertionError("output video decode");frame.recycle();
   boolean cancellation=false;try{new VideoEnhancer(c,new AtomicBoolean(true)).run("asset:enhance-test.mp4",(a,b,d,e)->{});}catch(IOException expected){cancellation=expected.getMessage().contains("取消");}
   if(!cancellation)throw new AssertionError("pre-cancel");
   result.putString("stream",new JSONObject().put("output_decoded",true).put("pre_cancel_passed",true).put("passed",true).put("frames",count.get()).put("fixture",fixture).put("elapsed_ms",inferenceElapsed).put("peak_sampled_pss_kib",peakPss.get()).put("pss_samples",samples.get()).put("process_cpu_ms",android.os.Process.getElapsedCpuTime()-processCpuStart).put("failure_checks",failureChecks).put("before",before).put("after",after).put("output",out.getAbsolutePath()).toString());
  }catch(Throwable e){code=1;result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));}
  finish(code,result);
 }
}

package studio.nkg.tv;
import android.app.Instrumentation;
import android.media.MediaFormat;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
public final class PolicyInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle b=new Bundle();int code=0;JSONArray checks=new JSONArray();try{
  int[] previous={MediaFormat.COLOR_STANDARD_BT601_PAL,MediaFormat.COLOR_RANGE_LIMITED,MediaFormat.COLOR_TRANSFER_SDR_VIDEO};
  MediaFormat decoder=new MediaFormat();decoder.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709);decoder.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_FULL);
  int[] result=VideoEnhancer.color(decoder,previous);if(result[0]!=1||result[1]!=1||result[2]!=3)throw new AssertionError("decoder format not preferred");checks.put("decoder output standard/range override extractor; absent transfer retained");
  decoder.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT2020);boolean rejected=false;try{VideoEnhancer.color(decoder,previous);}catch(IOException expected){rejected=true;}if(!rejected)throw new AssertionError("decoder BT2020 accepted");checks.put("decoder SDR BT2020 rejected");
  File dir=new File(getTargetContext().getFilesDir(),"enhanced");dir.mkdirs();int baseline=dir.list().length;
  for(String fixture:new String[]{"multi-audio-test.mp4","bt2020-test.mp4"}){
   boolean failed=false;String error="";try{new VideoEnhancer(getTargetContext(),new AtomicBoolean()).run("asset:"+fixture,(a,c,d,e)->{throw new AssertionError("unsupported input reached inference");});}catch(IOException expected){failed=true;error=expected.getMessage();}
   if(!failed||dir.list().length!=baseline)throw new AssertionError("policy or cleanup "+fixture);
   if(fixture.startsWith("multi")&&!error.contains("多音轨"))throw new AssertionError(error);
   if(fixture.startsWith("bt2020")&&!error.contains("色彩空间"))throw new AssertionError(error);
   checks.put(new JSONObject().put("fixture",fixture).put("rejected_before_inference",true).put("partial_output_removed",true).put("error",error));
  }
  android.media.MediaExtractor original=new android.media.MediaExtractor();try(android.content.res.AssetFileDescriptor fd=getTargetContext().getAssets().openFd("motion-cut-test.mp4")){original.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}for(int i=0;i<original.getTrackCount();i++)checks.put(new JSONObject().put("source_track_format",original.getTrackFormat(i).toString()));original.release();
  b.putString("stream",new JSONObject().put("passed",true).put("checks",checks).toString());
 }catch(Throwable t){code=1;b.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));}finish(code,b);}
}

package studio.nkg.tv;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
/** Captures the actual Android compositor after allowing TCG rendering to settle. */
public final class VisualInstrumentation extends Instrumentation {
 private MainActivity activity;private final JSONArray shots=new JSONArray();
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 private void main(Runnable r){AtomicReference<Throwable> e=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable t){e.set(t);}});if(e.get()!=null)throw new AssertionError(e.get());}
 private void call(String name,Class<?> type,Object arg){main(()->{try{Method m=type==null?MainActivity.class.getDeclaredMethod(name):MainActivity.class.getDeclaredMethod(name,type);m.setAccessible(true);if(type==null)m.invoke(activity);else m.invoke(activity,arg);}catch(Exception e){throw new RuntimeException(e);}});}
 private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
 private void shot(String name)throws Exception{Thread.sleep(3000);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("screenshot");File dir=new File(getTargetContext().getFilesDir(),"screenshots");dir.mkdirs();try(OutputStream o=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}shots.put(new JSONObject().put("file",name+".png").put("width",b.getWidth()).put("height",b.getHeight()));b.recycle();}
 @Override public void onStart(){Bundle b=new Bundle();int code=0;try{
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));shot("android-home");
  call("detail",Catalog.Video.class,new Catalog.Video("long",new String(new char[256]).replace('\0','长'),"完整标题位于滚动区域，按钮可继续向下滚动访问。","https://example.org/a.mp4","布局测试"));shot("android-long-title");
  call("sources",null,null);shot("android-sources");
  call("saved",boolean.class,true);shot("android-favorites-restored");
  File corrupt=new File(getTargetContext().getFilesDir(),"enhanced/corrupt-test.mp4");corrupt.getParentFile().mkdirs();try(OutputStream o=new FileOutputStream(corrupt)){o.write(new byte[4096]);}
  call("play",Catalog.Video.class,new Catalog.Video("corrupt","损坏媒体","",android.net.Uri.fromFile(corrupt).toString(),"原创错误夹具"));Thread.sleep(3000);main(()->{if(((android.widget.Button)field("playButton")).isEnabled())throw new AssertionError("error control enabled");});shot("android-corrupt-error");
  call("play",Catalog.Video.class,new Catalog.Video("nkg-demo-v1","像素实验室 · 离线测试片","","asset:demo.mp4","NKG 原创测试素材"));Thread.sleep(4000);main(()->{if(!(boolean)field("prepared"))throw new AssertionError("not prepared");((android.widget.Button)field("playButton")).performClick();if(!((android.widget.TextView)field("playbackStatus")).getText().toString().equals("已暂停"))throw new AssertionError("pause status stale");((android.widget.Button)field("playButton")).performClick();if(!((android.widget.TextView)field("playbackStatus")).getText().toString().contains("原片播放"))throw new AssertionError("resume status stale");if((activity.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)==0)throw new AssertionError("resume screen flag");});shot("android-player");
  b.putString("stream",new JSONObject().put("passed",true).put("scope","Android 10 QEMU compositor screenshots; not physical device").put("screenshots",shots).toString());
 }catch(Throwable t){code=1;b.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));}if(activity!=null)main(()->activity.finish());finish(code,b);}
}

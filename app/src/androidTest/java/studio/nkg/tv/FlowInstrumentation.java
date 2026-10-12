package studio.nkg.tv;

import android.app.*;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.*;

/** Direct instrumentation: video frames must not require Accessibility's global idle. */
public final class FlowInstrumentation extends Instrumentation {
    private MainActivity activity;
    private final JSONArray checks=new JSONArray(), samples=new JSONArray();
    private final long started=SystemClock.elapsedRealtime();
    interface Condition { boolean get() throws Exception; }
    private void main(Runnable r){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private TextView find(View view,String text){
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){TextView v=find(g.getChildAt(i),text);if(v!=null)return v;}}
        return null;
    }
    private boolean has(String s){AtomicBoolean found=new AtomicBoolean();main(()->found.set(find(activity.findViewById(android.R.id.content),s)!=null));return found.get();}
    private TextView action(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().contains(text))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView result=action(g.getChildAt(i),text);if(result!=null)return result;}}return null;}
    private void click(String text){main(()->{TextView v=action(activity.findViewById(android.R.id.content),text);if(v==null||!v.performClick())throw new AssertionError("No clickable "+text);});}
    private void until(String label,Condition c,long timeout) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<deadline){if(c.get()){checks.put(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);return;}Thread.sleep(100);}throw new AssertionError("Timeout: "+label);
    }
    private int position(){AtomicInteger v=new AtomicInteger();main(()->{MediaPlayer p=(MediaPlayer)field("player");v.set(p==null?-1:p.getCurrentPosition());});return v.get();}
    private boolean playing(){AtomicBoolean v=new AtomicBoolean();main(()->{MediaPlayer p=(MediaPlayer)field("player");v.set(p!=null&&p.isPlaying());});return v.get();}
    private void invoke(String method,Class<?> type,Object value){main(()->{try{Method m=MainActivity.class.getDeclaredMethod(method,type);m.setAccessible(true);m.invoke(activity,value);}catch(Exception e){throw new RuntimeException(e);}});}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=0;
        try{
            getTargetContext().getSharedPreferences("nkg",0).edit().clear().commit();
            Intent launch=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(MainActivity)startActivitySync(launch);
            until("home",()->has("你的私人放映室"),15000);
            main(()->{ViewGroup body=(ViewGroup)field("body");for(int i=0;i<body.getChildCount();i++)if(body.getChildAt(i) instanceof EditText)((EditText)body.getChildAt(i)).setText("not-found");});
            click("搜索");until("search empty",()->has("没有找到结果"),5000);
            main(()->{ViewGroup body=(ViewGroup)field("body");for(int i=0;i<body.getChildCount();i++)if(body.getChildAt(i) instanceof EditText)((EditText)body.getChildAt(i)).setText("");});click("搜索");
            click("查看详情");click("收藏影片");until("favorite add",()->has("取消收藏"),5000);
            click("开始播放");until("prepared and advancing",()->playing()&&position()>500,30000);
            click("暂停");int paused=position();Thread.sleep(700);if(Math.abs(position()-paused)>150)throw new AssertionError("pause advanced");
            checks.put("pause freezes clock");click("继续");until("resume",()->playing()&&position()>paused+200,10000);
            main(()->{MediaPlayer p=(MediaPlayer)field("player");p.seekTo(5000);});
            until("seek 5s",()->position()>=4800&&position()<10000,15000);
            // Exercise actual seek widget listener as well.
            main(()->{SeekBar seek=(SeekBar)field("seek");long t=SystemClock.uptimeMillis();float x=seek.getPaddingLeft()+(seek.getWidth()-seek.getPaddingLeft()-seek.getPaddingRight())*7000f/seek.getMax();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,seek.getHeight()/2f,0),up=MotionEvent.obtain(t,t+20,MotionEvent.ACTION_UP,x,seek.getHeight()/2f,0);seek.dispatchTouchEvent(down);seek.dispatchTouchEvent(up);down.recycle();up.recycle();});
            until("seek control 7s",()->position()>=6800,15000);
            main(()->activity.onBackPressed());until("back to detail",()->has("继续播放 ·"),5000);
            click("继续播放 ·");until("resume stored position",()->playing()&&position()>5000,30000);
            main(()->{MediaPlayer p=(MediaPlayer)field("player");p.seekTo(p.getDuration()-800);});until("completion",()->has("播放结束"),60000);click("重播");until("replay clock reset",()->playing()&&position()<5000,10000);
            main(()->activity.onBackPressed());
            main(()->{try{Method m=MainActivity.class.getDeclaredMethod("play",Catalog.Video.class);m.setAccessible(true);m.invoke(activity,field("demo"));callActivityOnPause(activity);}catch(Exception e){throw new RuntimeException(e);}});
            until("prepared while background",()->{AtomicBoolean ready=new AtomicBoolean();main(()->ready.set((boolean)field("prepared")));return ready.get();},30000);
            if(playing())throw new AssertionError("background autostart");checks.put("no playback after background preparation");
            main(()->callActivityOnResume(activity));click("继续");until("foreground explicit resume",()->playing(),10000);
            main(()->((MediaPlayer)field("player")).setLooping(true));
            for(int i=0;i<30;i++){
                Thread.sleep(1000);JSONObject sample=new JSONObject().put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("position_ms",position()).put("playing",playing()).put("pss_kib",Debug.getPss()).put("process_cpu_ms",android.os.Process.getElapsedCpuTime());samples.put(sample);
                if(!sample.getBoolean("playing"))throw new AssertionError("sustained playback stopped");
            }
            checks.put("30s sustained looping playback");
            main(()->activity.onBackPressed());click("历史");until("history contains demo",()->has("像素实验室"),5000);
            click("收藏");until("favorite persisted",()->has("像素实验室"),5000);
            // Local missing asset exercises real preparation error without any network route.
            invoke("play",Catalog.Video.class,new Catalog.Video("missing","失败测试","","asset:missing.mp4","instrumentation"));
            until("invalid source error",()->has("播放失败"),10000);
            main(()->activity.onBackPressed());until("error back navigation",()->has("失败测试"),5000);
            result.putString("stream",new JSONObject().put("passed",true).put("checks",checks).put("samples",samples).toString()+"\n");
        }catch(Throwable t){code=1;result.putString("stream","FAIL "+t+"\n"+android.util.Log.getStackTraceString(t)+"\nchecks="+checks+"\nsamples="+samples);}
        if(activity!=null)main(()->activity.finish());finish(code,result);
    }
}

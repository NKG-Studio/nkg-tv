package studio.nkg.tv;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final int BG=Color.rgb(11,18,32), PANEL=Color.rgb(24,35,53), MINT=Color.rgb(110,231,194), MUTED=Color.rgb(165,182,203);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor(), inference=Executors.newSingleThreadExecutor();
    private Store store; private LinearLayout root, body; private TextView status;
    private List<Catalog.Video> catalog=new ArrayList<>();
    private final Catalog.Video demo=new Catalog.Video("nkg-demo-v1","像素实验室 · 离线测试片","12 秒原创程序生成画面和测试音。可离线验证解码、进度、收藏与实验超分。","asset:demo.mp4","NKG 原创测试素材");
    private String screen="home", query="", feedError="订阅尚未刷新";
    private Catalog.Video current; private MediaPlayer player; private Surface surface; private TextureView texture; private ImageView enhanced;
    private Switch aiToggle; private TextView playbackStatus; private SeekBar seek; private Button playButton; private boolean prepared, aiEnabled, aiBusy, completed;
    private int generation, slowFrames, requestId; private boolean modelReady; private long lastFrame;
    private int frameWidth=160,frameHeight=90;
    private final Runnable ticker=new Runnable() { public void run() {
        if(player!=null && prepared && !completed) try { int pos=player.getCurrentPosition(); seek.setProgress(pos); store.progress(current,pos); } catch(IllegalStateException ignored) {}
        ui.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new Store(getSharedPreferences("nkg",MODE_PRIVATE));
        try { if(!store.cached().isEmpty()) catalog=Catalog.parse(store.cached()); } catch(Exception ignored) { }
        home(); ui.post(ticker);
        if(Core.AVAILABLE) inference.execute(()-> { boolean ok=false;try {
            java.io.File dir=new java.io.File(getFilesDir(),"models"); if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("model directory");
            for(String name:new String[]{"realesr-animevideov3-x2.param","realesr-animevideov3-x2.bin"}) {
                try(java.io.InputStream in=getAssets().open("models/"+name);java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) {byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            }
            ok=Core.initModel(dir.getAbsolutePath());
        }catch(Exception|UnsatisfiedLinkError ignored){} final boolean ready=ok; ui.post(()->{modelReady=ready;if(aiToggle!=null)aiToggle.setEnabled(ready);}); });
    }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int sp,int color) { TextView v=new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setPadding(dp(4),dp(6),dp(4),dp(6)); return v; }
    private Button button(String label,Runnable run) { Button b=new Button(this); b.setText(label); b.setTextColor(MINT); b.setTextSize(14); b.setAllCaps(false); android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(PANEL);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),Color.rgb(45,68,84));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(48,91,84)),shape,null)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.setMargins(dp(3),dp(5),dp(3),dp(5));b.setLayoutParams(lp); b.setOnClickListener(v->run.run()); return b; }
    private void page(String title) {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(12),dp(18),dp(12)); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(dp(18),dp(12)+insets.getSystemWindowInsetTop(),dp(18),dp(12)+insets.getSystemWindowInsetBottom()); return insets; });
        root.addView(text("NKG / TV",15,MINT)); root.addView(text(title,28,Color.WHITE));
        ScrollView scroll=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private void nav() { LinearLayout row=new LinearLayout(this); for(String label:new String[]{"发现","收藏","历史","来源"}) { Button b=button(label,()->{ if(label.equals("发现")) home(); else if(label.equals("来源")) sources(); else saved(label.equals("收藏")); }); row.addView(b,new LinearLayout.LayoutParams(0,-2,1)); } root.addView(row); }
    private void home() {
        screen="home"; page("你的私人放映室"); body.addView(text("搜索自己的内容，随时继续观看。",14,MUTED));
        EditText input=new EditText(this); input.setSingleLine(true); input.setHint("搜索影片、描述或来源"); input.setText(query); body.addView(input);
        body.addView(button("搜索",()->{query=input.getText().toString(); home();}));
        status=text(catalog.isEmpty()?feedError:"已缓存 "+catalog.size()+" 部影片",13,MUTED); body.addView(status);
        List<Catalog.Video> all=new ArrayList<>(); all.add(demo); all.addAll(catalog); List<Catalog.Video> hits=Catalog.search(all,query);
        if(hits.isEmpty()) body.addView(text("没有找到结果。试试其他关键词或刷新订阅。",16,MUTED));
        for(Catalog.Video video:hits) card(video); nav();
    }
    private void card(Catalog.Video v) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(PANEL); box.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(7),0,dp(7)); body.addView(box,lp);
        box.addView(text(v.source,12,MINT)); box.addView(text(v.title,20,Color.WHITE)); box.addView(text(v.description,13,MUTED)); box.addView(button("查看详情",()->detail(v)));
    }
    private void detail(Catalog.Video v) {
        current=v; screen="detail"; page(v.title); body.addView(text(v.source,14,MINT)); body.addView(text(v.description,16,MUTED));
        body.addView(button(store.position(v)>0?"继续播放 · "+store.position(v)/1000+" 秒":"开始播放",()->play(v)));
        body.addView(button(store.favorite(v)?"取消收藏":"收藏影片",()->{store.toggle(v); detail(v);}));
        body.addView(text("播放地址："+v.url,12,MUTED)); body.addView(text("仅播放你有权访问的内容。支持系统解码器可播放的 MP4/HLS；不绕过 DRM。",13,MUTED)); body.addView(button("返回发现",this::home)); nav();
    }
    private void saved(boolean favorites) {
        screen=favorites?"favorites":"history"; page(favorites?"我的收藏":"继续观看"); List<Catalog.Video> items=store.saved(favorites);
        if(items.isEmpty()) body.addView(text(favorites?"还没有收藏。打开影片详情即可收藏。":"还没有观看记录。",16,MUTED));
        for(Catalog.Video v:items) card(v);
        if(!favorites) body.addView(button("清空历史",()->new AlertDialog.Builder(this).setMessage("清除本机观看历史和续播位置？").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{store.clearHistory();saved(false);}).show())); nav();
    }
    private void sources() {
        screen="sources"; page("内容来源"); body.addView(text("默认订阅由你指定。只会在点击刷新后联网，失败会保留上次有效内容。",14,MUTED));
        EditText url=new EditText(this); url.setText(store.feed()); url.setSingleLine(true); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI); body.addView(url);
        status=text(feedError,14,MUTED); body.addView(status);
        body.addView(button("保存并刷新",()-> { try { String value=Net.checked(url.getText().toString()).toString(); store.feed(value); refresh(value); } catch(Exception e) {status.setText(e.getMessage());} }));
        body.addView(text("尚未核实默认订阅的实际 JSON 格式：云端 CONNECT 403。当前可导入 nkg-catalog-v1 JSON，格式说明见源码 README。不会执行订阅中的脚本或插件。",13,MUTED));
        body.addView(button("粘贴目录 JSON",()-> { EditText json=new EditText(this); json.setMinLines(5); new AlertDialog.Builder(this).setTitle("导入自有内容目录").setView(json).setNegativeButton("取消",null).setPositiveButton("导入",(d,w)->{try {String s=json.getText().toString(); if(s.length()>Net.MAX_BYTES) throw new IllegalArgumentException("内容过大"); List<Catalog.Video> parsed=Catalog.parse(s); requestId++; catalog=parsed;store.cache(s);feedError="导入成功："+catalog.size()+" 部影片";sources();} catch(Exception e){message("导入失败",e.getMessage());}}).show(); }));
        body.addView(text("动画超分：Real-ESRGAN animevideov3 ×2 / ncnn CPU。"+(modelReady?"模型已加载。":"模型尚未就绪或 ABI 不支持，功能不可用。")+"默认关闭；低分辨率 SDR 动画预览，不保证真人影视画质提升。Rust TinySR2 另用于计算核心测试。",13,MUTED));
        body.addView(button("隐私与许可",()->message("隐私与许可","无账号、无分析 SDK。订阅、收藏、历史仅存本机；访问内容时服务器会看到网络地址。\n\n流程参考 LibreSpark/LibreTV（AGPL-3.0），未复制其代码或源列表。\nReal-ESRGAN 动画模型和 ncnn 采用 BSD-3-Clause，官方模型随 APK 提供；Rust TinySR2 为原创合成数据实验。完整许可位于 APK assets/licenses 和源码 THIRD_PARTY.md。"))); nav();
    }
    private void refresh(String url) {
        final int token=++requestId; status.setText("正在刷新…（连接 8 秒 / 读取 10 秒超时）");
        network.execute(()-> { try {String json=Net.get(url); List<Catalog.Video> parsed=Catalog.parse(json); ui.post(()->{if(token!=requestId||isFinishing())return;catalog=parsed;store.cache(json);feedError=parsed.isEmpty()?"订阅为空，没有影片":"刷新成功："+parsed.size()+" 部影片";if(screen.equals("sources"))sources();else if(screen.equals("home"))home();});}
            catch(Exception e){ ui.post(()->{if(token!=requestId||isFinishing())return;feedError="刷新失败："+safeError(e)+"。已保留缓存，可修改地址后重试。";if(screen.equals("sources"))sources();else if(screen.equals("home"))home();});} });
    }
    private String safeError(Exception e) { if(e instanceof java.net.SocketTimeoutException)return "连接或读取超时"; String m=e.getMessage(); return m==null?e.getClass().getSimpleName():m; }
    private void message(String title,String value) { new AlertDialog.Builder(this).setTitle(title).setMessage(value).setPositiveButton("知道了",null).show(); }
    private void play(Catalog.Video v) {
        releasePlayer(); completed=false; current=v; screen="player"; page(v.title); final int token=++generation;
        FrameLayout stage=new FrameLayout(this); texture=new TextureView(this); enhanced=new ImageView(this); enhanced.setScaleType(ImageView.ScaleType.FIT_CENTER); enhanced.setVisibility(View.INVISIBLE); stage.addView(texture,new FrameLayout.LayoutParams(-1,-1));stage.addView(enhanced,new FrameLayout.LayoutParams(-1,-1));body.addView(stage,new LinearLayout.LayoutParams(-1,dp(220)));
        playbackStatus=text("正在准备播放…",13,MUTED);body.addView(playbackStatus);
        seek=new SeekBar(this);body.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){} public void onProgressChanged(SeekBar s,int p,boolean user){} public void onStopTrackingTouch(SeekBar s){if(prepared&&player!=null){generation++;completed=false;enhanced.setVisibility(View.INVISIBLE);player.seekTo(s.getProgress());}}});
        playButton=button("暂停",()-> { if(prepared&&player!=null) {if(player.isPlaying()){player.pause();playButton.setText("继续");}else {completed=false;player.start();playButton.setText("暂停");}} });body.addView(playButton);
        Switch ai=new Switch(this);aiToggle=ai;ai.setText("动画 AI 2× · 默认关闭");ai.setTextColor(MINT);ai.setEnabled(modelReady); body.addView(ai);
        body.addView(text("Real-ESRGAN animevideov3：真实神经网络逐帧推理，偏向动画。当前是 160 像素宽采样预览，最高约 8 帧/秒；不是全分辨率实时增强。真人片可能失真，连续慢帧自动恢复原片。",12,MUTED));
        ai.setOnCheckedChangeListener((b,checked)-> {aiEnabled=checked;slowFrames=0;generation++;enhanced.setVisibility(View.INVISIBLE);playbackStatus.setText(checked?"实验 AI 已启用，等待视频帧":"原片播放 · AI 关闭");});
        body.addView(button("返回详情",()->{releasePlayer();detail(v);}));
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){ if(screen.equals("player")) openPlayer(v,st,token); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){fitVideo();}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePlayer();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture st){ processFrame(ai); }
        });
    }
    private void openPlayer(Catalog.Video v,SurfaceTexture st,int token) {
        try {
            player=new MediaPlayer(); surface=new Surface(st);player.setSurface(surface);
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            if(v.url.equals("asset:demo.mp4")){try(AssetFileDescriptor fd=getAssets().openFd("demo.mp4")){player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}}
            else {Net.checked(v.url);player.setDataSource(v.url);}
            player.setOnPreparedListener(p->{if(p!=player)return;prepared=true;seek.setMax(p.getDuration());if(store.position(v)<p.getDuration()-1000)p.seekTo(store.position(v));fitVideo();p.start();playbackStatus.setText("原片播放 · AI 关闭");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});
            player.setOnVideoSizeChangedListener((p,w,h)->fitVideo());
            player.setOnCompletionListener(p->{completed=true;generation++;store.progress(v,0);playButton.setText("重播");enhanced.setVisibility(View.INVISIBLE);playbackStatus.setText("播放结束");});
            player.setOnErrorListener((p,what,extra)->{prepared=false;aiEnabled=false;generation++;enhanced.setVisibility(View.INVISIBLE);playbackStatus.setText("播放失败（"+what+"/"+extra+"）：地址不可用、格式不支持或需要授权。返回详情可重试。");return true;});
            final MediaPlayer pending=player; player.prepareAsync(); ui.postDelayed(()->{if(player==pending&&!prepared&&screen.equals("player")){releasePlayer();playbackStatus.setText("准备超时。请返回详情后重试，或更换来源。");}},20000);
        }catch(Exception e){releasePlayer();playbackStatus.setText("播放失败："+safeError(e));}
    }
    private void fitVideo() {
        if(player==null||texture==null)return;int w=player.getVideoWidth(),h=player.getVideoHeight();if(w<=0||h<=0)return;
        View container=(View)texture.getParent();float viewW=container.getWidth(),viewH=container.getHeight();if(viewW<=0||viewH<=0)return;
        float scale=Math.min(viewW/w,viewH/h);int fitW=Math.round(w*scale),fitH=Math.round(h*scale);if(texture.getWidth()!=fitW||texture.getHeight()!=fitH){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(fitW,fitH,Gravity.CENTER);texture.setLayoutParams(lp);}
        frameWidth=160;frameHeight=Math.max(1,Math.min(180,Math.round(160f*h/w)));
    }
    private void processFrame(Switch toggle) {
        long now=SystemClock.elapsedRealtime(); if(!aiEnabled||aiBusy||!prepared||player==null||!player.isPlaying()||now-lastFrame<125)return;
        lastFrame=now;final int token=generation;final int width=frameWidth,height=frameHeight;
        // TextureView readback is experimental and SDR-only, independent of audio clock.
        Bitmap frame=texture.getBitmap(width,height);if(frame==null)return; int[] input=new int[width*height];frame.getPixels(input,0,width,0,0,width,height);frame.recycle();aiBusy=true;
        inference.execute(()->{int[] out=null;try{out=Core.upscaleAnime(input,width,height);}catch(RuntimeException|UnsatisfiedLinkError ignored){}final int[] result=out;long elapsed=SystemClock.elapsedRealtime()-now;
            ui.post(()->{aiBusy=false;if(token!=generation||!aiEnabled||!screen.equals("player"))return;
                if(result==null||elapsed>350){slowFrames++;if(result==null||slowFrames>=3){toggle.setChecked(false);playbackStatus.setText("AI 已自动关闭：推理失败或连续超时，已恢复原片。");}return;}
                slowFrames=0;Bitmap bitmap=Bitmap.createBitmap(result,width*2,height*2,Bitmap.Config.ARGB_8888);enhanced.setImageBitmap(bitmap);enhanced.setVisibility(View.VISIBLE);playbackStatus.setText("实验 AI 2× · "+elapsed+" ms/帧 · 非实时原速画质");});});
    }
    private void releasePlayer() {
        generation++;aiEnabled=false;prepared=false;
        if(player!=null){try{if(!completed)store.progress(current,player.getCurrentPosition());}catch(IllegalStateException ignored){}player.release();player=null;}
        if(surface!=null){surface.release();surface=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void onBackPressed(){if(screen.equals("player")){Catalog.Video v=current;releasePlayer();detail(v);}else if(!screen.equals("home"))home();else super.onBackPressed();}
    @Override protected void onPause(){super.onPause();if(player!=null&&prepared){if(!completed)store.progress(current,player.getCurrentPosition());generation++;enhanced.setVisibility(View.INVISIBLE);player.pause();playButton.setText("继续");}}
    @Override protected void onDestroy(){releasePlayer();ui.removeCallbacksAndMessages(null);requestId++;network.shutdownNow();inference.shutdownNow();super.onDestroy();}
}

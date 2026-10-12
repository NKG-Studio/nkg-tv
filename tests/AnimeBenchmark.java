import studio.nkg.tv.Core;
import java.util.Arrays;
public class AnimeBenchmark {
 public static void main(String[] args){
  if(!Core.initModel(args[0]))throw new AssertionError("model");
  int w=320,h=180;int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff000000|((x*255/w)<<16)|((y*255/h)<<8)|(x+y)%256;
  long[] samples=new long[8];
  for(int i=0;i<10;i++){long start=System.nanoTime();int[] out=Core.upscaleAnime(pixels,w,h);if(out==null||out.length!=w*h*4)throw new AssertionError("shape");if(i>=2)samples[i-2]=System.nanoTime()-start;}
  Arrays.sort(samples);System.out.printf(java.util.Locale.ROOT,"{\"input_width\":320,\"input_height\":180,\"output_width\":640,\"output_height\":360,\"p50_ms\":%.3f,\"p95_ms\":%.3f,\"deadline_24fps_ms\":41.667,\"platform\":\"Linux cloud CPU, not Android hardware\"}%n",samples[4]/1e6,samples[7]/1e6);
 }
}

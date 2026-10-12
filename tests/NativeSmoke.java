import studio.nkg.tv.Core;
import java.io.*;
import java.util.Arrays;
public class NativeSmoke {
    public static void main(String[] args) throws Exception {
        if (!Core.AVAILABLE) throw new AssertionError("JNI library not loaded");
        if (Core.upscale(new int[1],0,1)!=null) throw new AssertionError("invalid dimensions accepted");
        int[] pixels=new int[160*90];
        for(int y=0;y<90;y++)for(int x=0;x<160;x++)pixels[y*160+x]=0xff000000|((x*255/159)<<16)|((y*255/89)<<8)|((x+y)%256);
        long[] samples=new long[20];int[] out=null;
        for(int n=0;n<25;n++){long t=System.nanoTime();out=Core.upscale(pixels,160,90);if(n>=5)samples[n-5]=System.nanoTime()-t;}
        if(out==null||out.length!=160*90*4)throw new AssertionError("output shape");
        Arrays.sort(samples);
        try(DataOutputStream stream=new DataOutputStream(new FileOutputStream(args[0]))){for(int p:out)stream.writeInt(p);}
        System.out.printf("JNI smoke passed. 160x90→320x180 host CPU p50=%.2fms p95=%.2fms%n",samples[10]/1e6,samples[18]/1e6);
    }
}

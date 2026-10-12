import studio.nkg.tv.Core;
import java.io.*;
public class AnimeSmoke {
    public static void main(String[] args) throws Exception {
        if(!Core.initModel(args[0]))throw new AssertionError("Model failed to load");
        int w=160,h=90;int[] p=new int[w*h];
        try(DataInputStream in=new DataInputStream(new FileInputStream(args[1]))){for(int i=0;i<p.length;i++)p[i]=0xff000000|(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();}
        long start=System.nanoTime();int[] out=Core.upscaleAnime(p,w,h);long ms=(System.nanoTime()-start)/1000000;
        if(out==null||out.length!=w*h*4)throw new AssertionError("Inference failed");
        try(DataOutputStream stream=new DataOutputStream(new FileOutputStream(args[2]))){for(int color:out)stream.writeInt(color);}
        System.out.println("Real-ESRGAN ncnn video-frame inference passed: "+ms+"ms (host CPU)");
    }
}

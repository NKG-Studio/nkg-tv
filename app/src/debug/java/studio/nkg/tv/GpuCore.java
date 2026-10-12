package studio.nkg.tv;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.*;
import android.os.Looper;
import java.io.*;
import java.nio.ByteBuffer;
/** Debug GPU pipeline prototype, never a production model/performance claim. */
final class GpuCore implements AutoCloseable {
 static {System.loadLibrary("nkg_gpu");}
 private long handle;
 private static void worker(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("GPU work must stay off the UI thread");}
 GpuCore(Context c,boolean allowSoftware)throws IOException {worker();byte[] bytes;try(InputStream in=c.getAssets().open("tinysr2.spv");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>256*1024)throw new IOException("shader size");}bytes=out.toByteArray();}ByteBuffer shader=ByteBuffer.allocateDirect(bytes.length);shader.put(bytes);shader.flip();handle=create(shader,allowSoftware);}
 synchronized String device(){worker();return info(handle);}
 synchronized int[] infer(Image image,int w,int h,int[] color)throws IOException {worker();if(handle==0)throw new IOException("GPU session closed");if(image.getFormat()!=ImageFormat.YUV_420_888)throw new IOException("YUV420 decoder output required");Rect crop=image.getCropRect();if(crop.width()!=w||crop.height()!=h)throw new IOException("source dimensions changed");EnhancementPolicy.color(color[0],color[1],color[2]);Image.Plane[] p=image.getPlanes();int[] strides=new int[6];for(int i=0;i<3;i++){strides[i*2]=p[i].getRowStride();strides[i*2+1]=p[i].getPixelStride();}return yuv(handle,p[0].getBuffer().slice(),p[1].getBuffer().slice(),p[2].getBuffer().slice(),strides,w,h,crop.left,crop.top,color[0]==MediaFormat.COLOR_STANDARD_BT709,color[1]==MediaFormat.COLOR_RANGE_FULL);}
 @Override public synchronized void close(){worker();if(handle!=0){destroy(handle);handle=0;}}
 private static native long create(ByteBuffer shader,boolean allowSoftware);
 private static native String info(long handle);
 private static native int[] yuv(long handle,ByteBuffer y,ByteBuffer u,ByteBuffer v,int[] strides,int w,int h,int cropX,int cropY,boolean bt709,boolean fullRange);
 private static native void destroy(long handle);
}

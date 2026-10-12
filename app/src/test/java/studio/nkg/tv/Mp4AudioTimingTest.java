package studio.nkg.tv;
import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class Mp4AudioTimingTest {
 @Test public void zeroDelayNoRewrite()throws Exception{File f=File.createTempFile("nkg",".mp4");try{Files.write(f.toPath(),new byte[]{1,2,3});Mp4AudioTiming.preserve(f,44100,0,0,4000000);assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(f.toPath()));}finally{f.delete();}}
 @Test(expected=IOException.class) public void rejectsInvalidTiming()throws Exception{Mp4AudioTiming.preserve(new File("unused"),0,1024,0,4000000);}
 @Test public void malformedDoesNotModify()throws Exception{File f=File.createTempFile("nkg",".mp4");try{byte[] data={0,0,0,99,109,111,111,118};Files.write(f.toPath(),data);try{Mp4AudioTiming.preserve(f,44100,1024,0,4000000);fail("accepted malformed box");}catch(IOException expected){}assertArrayEquals(data,Files.readAllBytes(f.toPath()));}finally{f.delete();}}
}

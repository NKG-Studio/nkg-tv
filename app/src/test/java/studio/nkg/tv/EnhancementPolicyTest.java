package studio.nkg.tv;
import android.media.MediaFormat;
import org.junit.Test;
import java.io.IOException;
public class EnhancementPolicyTest {
 @Test public void supportedColors()throws Exception{for(int standard:new int[]{1,2,4})for(int range:new int[]{1,2})EnhancementPolicy.color(standard,range,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);}
 @Test(expected=IOException.class) public void rejectsSDR2020()throws Exception{EnhancementPolicy.color(MediaFormat.COLOR_STANDARD_BT2020,2,3);}
 @Test(expected=IOException.class) public void rejectsUnknownStandard()throws Exception{EnhancementPolicy.color(0,2,3);}
 @Test(expected=IOException.class) public void rejectsUnknownRange()throws Exception{EnhancementPolicy.color(1,0,3);}
 @Test(expected=IOException.class) public void rejectsHDR()throws Exception{EnhancementPolicy.color(1,2,MediaFormat.COLOR_TRANSFER_ST2084);}
 @Test public void zeroOrOneAudio()throws Exception{EnhancementPolicy.audioTracks(0);EnhancementPolicy.audioTracks(1);}
 @Test(expected=IOException.class) public void rejectsMultipleAudio()throws Exception{EnhancementPolicy.audioTracks(2);}
}

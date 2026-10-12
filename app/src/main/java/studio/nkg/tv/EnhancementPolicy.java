package studio.nkg.tv;

import android.media.MediaFormat;
import java.io.IOException;

/** Explicitly bounded color/track policy; never relabel wide-gamut data as Rec.709. */
final class EnhancementPolicy {
    static void color(int standard,int range,int transfer)throws IOException {
        if(standard!=MediaFormat.COLOR_STANDARD_BT709 && standard!=MediaFormat.COLOR_STANDARD_BT601_PAL && standard!=MediaFormat.COLOR_STANDARD_BT601_NTSC)
            throw new IOException("不支持该色彩空间（"+standard+"）；当前仅支持 BT.601 / BT.709，未转换");
        if(range!=MediaFormat.COLOR_RANGE_FULL && range!=MediaFormat.COLOR_RANGE_LIMITED)
            throw new IOException("不支持该色彩范围（"+range+"），未转换");
        if(transfer!=MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            throw new IOException("不支持该传递函数或 HDR（"+transfer+"），未转换");
    }
    static void audioTracks(int count)throws IOException {
        if(count>1)throw new IOException("检测到多音轨；当前仅支持单音轨副本，已停止，未丢弃任何音轨。请先提供单音轨文件");
    }
}

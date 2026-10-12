package studio.nkg.tv;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Restores the simple AAC priming/trim edit exposed by MediaExtractor.
 * Operates only on our finalized MediaMuxer output; preserves all mdat offsets.
 * Unsupported structures fail the export instead of silently shifting audio.
 */
final class Mp4AudioTiming {
    private static final int LIMIT=16*1024*1024;
    private static ByteBuffer buf(byte[] b){return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN);}
    private static int u32(byte[] b,int p)throws IOException{long n=buf(b).getInt(p)&0xffffffffL;if(n>Integer.MAX_VALUE)throw new IOException("MP4 元数据字段过大");return (int)n;}
    private static String type(byte[] b,int p){return new String(b,p+4,4,StandardCharsets.US_ASCII);}
    private static List<byte[]> children(byte[] parent)throws IOException{
        ArrayList<byte[]> out=new ArrayList<>();int p=8;
        while(p<parent.length){if(parent.length-p<8)throw new IOException("MP4 子块不完整");int n=u32(parent,p);if(n<8||n>parent.length-p)throw new IOException("不支持的 MP4 子块");out.add(Arrays.copyOfRange(parent,p,p+n));p+=n;}
        return out;
    }
    private static byte[] box(String type,List<byte[]> children)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);int length=8;for(byte[] c:children)length=Math.addExact(length,c.length);if(length>LIMIT)throw new IOException("MP4 元数据过大");d.writeInt(length);d.writeBytes(type);for(byte[] c:children)d.write(c);return bytes.toByteArray();}
    private static byte[] child(byte[] parent,String type)throws IOException{for(byte[] c:children(parent))if(type(c,0).equals(type))return c;throw new IOException("缺少 MP4 "+type);}
    private static int scale(byte[] header)throws IOException{int version=header[8]&255;if(version>1)throw new IOException("未知 MP4 时间版本");return u32(header,version==0?20:28);}
    private static void duration(byte[] header,long value,boolean track)throws IOException{int version=header[8]&255;if(version>1)throw new IOException("未知 MP4 时间版本");int offset=track?(version==0?28:36):(version==0?24:32);if(version==0){if(value<0||value>0xffffffffL)throw new IOException("MP4 时长超出范围");buf(header).putInt(offset,(int)value);}else buf(header).putLong(offset,value);}
    private static byte[] edit(long movieDuration,long audioStart)throws IOException{
        if(movieDuration<0||movieDuration>0xffffffffL||audioStart<0||audioStart>Integer.MAX_VALUE)throw new IOException("音频编辑时长超出范围");
        ByteBuffer b=ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN);b.putInt(28).put("elst".getBytes(StandardCharsets.US_ASCII)).putInt(0).putInt(1).putInt((int)movieDuration).putInt((int)audioStart).putShort((short)1).putShort((short)0);
        return box("edts",Collections.singletonList(b.array()));
    }
    static void preserve(File file,int sampleRate,int delay,int padding,long sourceDurationUs)throws IOException{
        if(delay==0&&padding==0)return;
        if(sampleRate<=0||delay<0||padding<0||sourceDurationUs<=0||sourceDurationUs>3_601_000_000L)throw new IOException("音频延迟信息无效");
        long durationUs=sourceDurationUs-Math.round((delay+(long)padding)*1_000_000.0/sampleRate);if(durationUs<=0)throw new IOException("音频裁剪时长无效");
        try(RandomAccessFile f=new RandomAccessFile(file,"rw")){
            long length=f.length(),pos=0,moovPos=-1;byte[] moov=null;
            while(pos<length){if(length-pos<8)throw new IOException("MP4 顶层块不完整");f.seek(pos);long size=f.readInt()&0xffffffffL;byte[] kind=new byte[4];f.readFully(kind);String type=new String(kind,StandardCharsets.US_ASCII);int header=8;if(size==1){size=f.readLong();header=16;}
                if(size<header||size>length-pos)throw new IOException("不支持开放或损坏 MP4 块");
                if(type.equals("moov")){if(moov!=null||header!=8||size>LIMIT)throw new IOException("不支持该 MP4 元数据布局");moovPos=pos;moov=new byte[(int)size];f.seek(pos);f.readFully(moov);}pos+=size;
            }
            if(moov==null)throw new IOException("缺少 MP4 moov");
            int movieScale=scale(child(moov,"mvhd"));if(movieScale<=0)throw new IOException("MP4 时间刻度无效");long movieDuration=Math.round(durationUs*movieScale/1_000_000.0);int changed=0;
            List<byte[]> top=children(moov);for(int i=0;i<top.size();i++)if(type(top.get(i),0).equals("trak")){
                byte[] track=top.get(i),media=child(track,"mdia"),handler=child(media,"hdlr");if(handler.length<20)throw new IOException("MP4 handler 无效");if(!new String(handler,16,4,StandardCharsets.US_ASCII).equals("soun"))continue;
                int audioScale=scale(child(media,"mdhd"));if(audioScale<=0)throw new IOException("音频时间刻度无效");long audioStart=Math.round(delay*(double)audioScale/sampleRate);
                List<byte[]> fields=children(track),replacement=new ArrayList<>();for(byte[] c:fields){String t=type(c,0);if(t.equals("edts"))throw new IOException("暂不支持带时间偏移的音频编辑列表，未生成副本");if(t.equals("tkhd"))duration(c,movieDuration,true);replacement.add(c);}replacement.add(edit(movieDuration,audioStart));top.set(i,box("trak",replacement));changed++;
            }
            if(changed!=1)throw new IOException("音频轨结构不受支持");
            long movieEnd=0;for(byte[] c:top)if(type(c,0).equals("trak")){byte[] t=child(c,"tkhd");int v=t[8]&255;if(v>1)throw new IOException("未知轨时长版本");long end=v==0?(buf(t).getInt(28)&0xffffffffL):buf(t).getLong(36);if(end<0)throw new IOException("负轨时长");movieEnd=Math.max(movieEnd,end);}
            for(byte[] c:top)if(type(c,0).equals("mvhd"))duration(c,movieEnd,false);
            byte[] replacement=box("moov",top);
            // Append first, then invalidate the old moov. Existing chunk offsets remain valid.
            f.seek(length);f.write(replacement);f.getFD().sync();f.seek(moovPos+4);f.writeBytes("free");f.getFD().sync();
        }catch(IndexOutOfBoundsException|ArithmeticException e){throw new IOException("损坏的 MP4 时间元数据",e);}
    }
}

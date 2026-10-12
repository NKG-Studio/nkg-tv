#!/usr/bin/env python3
"""Original MIT test clip: moving blocks, diagonal texture, hard cut at 2 s."""
import subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
output=root/'app/src/debug/assets/motion-cut-test.mp4'
output.parent.mkdir(parents=True,exist_ok=True)
w,h,fps,frames=64,36,4,16
p=subprocess.Popen(['ffmpeg','-y','-loglevel','error','-f','rawvideo','-pix_fmt','rgb24','-s',f'{w}x{h}','-r',str(fps),'-i','pipe:0','-f','lavfi','-i','sine=frequency=660:sample_rate=44100:duration=4','-c:v','libx264','-bf','0','-pix_fmt','yuv420p','-c:a','aac','-t','4','-movflags','+faststart',str(output)],stdin=subprocess.PIPE)
for frame in range(frames):
 data=bytearray()
 for y in range(h):
  for x in range(w):
   cut=frame>=8
   c=(20+(x*3+frame*7)%70,25+(y*4)%70,100) if not cut else (100,20+(x*2)%80,25+(y*5+frame*9)%70)
   if (x-frame*4)%64<12 and 7<y<26:c=(230,200,40) if not cut else (30,210,220)
   if (x+y+frame*2)%13<2:c=(210,220,230)
   data.extend(c)
 p.stdin.write(data)
p.stdin.close()
if p.wait():raise SystemExit('ffmpeg failed')

# Derived policy fixtures are still entirely original synthetic content.
subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(output),'-f','lavfi','-i','sine=frequency=330:sample_rate=44100:duration=4','-map','0:v','-map','0:a','-map','1:a','-c:v','copy','-c:a','aac','-t','4','-movflags','+faststart',str(output.with_name('multi-audio-test.mp4'))],check=True)
subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(output),'-c:v','libx264','-colorspace','bt2020nc','-color_primaries','bt2020','-color_trc','bt709','-c:a','copy','-movflags','+faststart',str(output.with_name('bt2020-test.mp4'))],check=True)

#!/usr/bin/env python3
"""Original-resolution reference via halo-preserving CPU tiles (no resampling)."""
import ctypes,json,sys
from pathlib import Path
lib=ctypes.CDLL(sys.argv[1]);source=Path(sys.argv[2]).read_bytes();actual=Path(sys.argv[3]).read_bytes();w,h=map(int,sys.argv[4:6]);assert len(source)==w*h*4 and len(actual)==w*h*16
lib.nkg_upscale.argtypes=[ctypes.POINTER(ctypes.c_uint32),ctypes.POINTER(ctypes.c_uint32),ctypes.c_int32,ctypes.c_int32];lib.nkg_upscale.restype=ctypes.c_int32
maximum=total=compared=0;tiles=0
# Rust's reference bound is 320x180. Preserve its radius-one receptive field.
for y in range(0,h,178):
 for x in range(0,w,318):
  endx=min(w,x+318);endy=min(h,y+178);sx=max(0,x-1);sy=max(0,y-1);ex=min(w,endx+1);ey=min(h,endy+1);tw,th=ex-sx,ey-sy
  pixels=b''.join(source[(row*w+sx)*4:(row*w+ex)*4] for row in range(sy,ey))
  a=(ctypes.c_uint32*(tw*th)).from_buffer_copy(pixels);b=(ctypes.c_uint32*(tw*th*4))();assert lib.nkg_upscale(a,b,tw,th)==0
  raw=bytes(b)
  for row in range((endy-y)*2):
   start=((y-sy)*2+row)*tw*8+(x-sx)*8;reference=raw[start:start+(endx-x)*8]
   offset=((y*2+row)*w*2+x*2)*4;gpu=actual[offset:offset+len(reference)]
   for left,right in zip(reference,gpu):d=abs(left-right);maximum=max(maximum,d);total+=d
   compared+=len(reference)
  tiles+=1
assert compared==len(actual) and maximum<=1,(compared,len(actual),maximum)
print(json.dumps(dict(passed=True,width=w,height=h,scale=2,reference_tiles=tiles,compared_bytes=compared,max_channel_error=maximum,mean_channel_error=total/compared,scope='Full original resolution; CPU reference uses halo tiles, GPU uses a full-frame dispatch; no resolution reduction'),indent=2))

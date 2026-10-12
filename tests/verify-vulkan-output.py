#!/usr/bin/env python3
"""Compare Vulkan's byte output with the actual portable Rust C ABI."""
import ctypes,json,struct,sys
from pathlib import Path
lib=ctypes.CDLL(sys.argv[1]);w,h=map(int,sys.argv[4:6]);source=Path(sys.argv[2]).read_bytes();output=Path(sys.argv[3]).read_bytes();assert len(source)==w*h*4 and len(output)==w*h*16
input_array=(ctypes.c_uint32*(w*h)).from_buffer_copy(source);reference=(ctypes.c_uint32*(w*h*4))()
lib.nkg_upscale.argtypes=[ctypes.POINTER(ctypes.c_uint32),ctypes.POINTER(ctypes.c_uint32),ctypes.c_int32,ctypes.c_int32];lib.nkg_upscale.restype=ctypes.c_int32
assert lib.nkg_upscale(input_array,reference,w,h)==0
expected=bytes(reference);differences=[abs(a-b) for a,b in zip(expected,output)];maximum=max(differences);assert maximum<=1, maximum
print(json.dumps(dict(passed=True,width=w,height=h,scale=2,compared_bytes=len(output),max_channel_error=maximum,mean_channel_error=sum(differences)/len(differences),scope='Vulkan shader versus actual Rust original TinySR2 model; no film quality or device FPS claim'),indent=2))

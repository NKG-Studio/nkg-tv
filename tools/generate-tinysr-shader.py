#!/usr/bin/env python3
"""Generate Vulkan TinySR2 correctness prototype from the exact shared Rust weights.
Not the chosen production film model and not a playback/FPS acceptance test.
"""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
source=(root/'core/src/weights.rs').read_text()
arrays={}
for name,count in [('W1',216),('B1',24),('W2',96),('B2',4)]:
 m=re.search(r'pub const '+name+r': \[f32; \d+\] = \[(.*?)\];',source,re.S)
 values=re.findall(r'([-+0-9.eE]+)f32',m.group(1));assert len(values)==count
 arrays[name]=f'const float {name}[{count}] = float[]('+','.join(values)+');'
shader='''#version 450
// Experimental original synthetic-data model; no production quality claim.
layout(local_size_x=8,local_size_y=8,local_size_z=1) in;
layout(set=0,binding=0,std430) readonly buffer Input { uint pixels[]; } inputFrame;
layout(set=0,binding=1,std430) writeonly buffer Output { uint pixels[]; } outputFrame;
layout(push_constant) uniform Shape {
 uint width; uint height; uint mode; uint bt709; uint fullRange;
 uint uOffset; uint vOffset; uint yRow; uint uRow; uint vRow;
 uint yPixel; uint uPixel; uint vPixel; uint cropX; uint cropY;
} shape;
'''+ '\n'.join(arrays.values())+'''
vec3 rgb(uint p){return vec3((p>>16)&255u,(p>>8)&255u,p&255u)/255.0;}
uint sampleByte(uint offset){return (inputFrame.pixels[offset>>2]>>((offset&3u)*8u))&255u;}
vec3 samplePixel(ivec2 p){
 if(shape.mode==0u)return rgb(inputFrame.pixels[uint(p.y)*shape.width+uint(p.x)]);
 uvec2 s=uvec2(p)+uvec2(shape.cropX,shape.cropY);
 float yy=float(sampleByte(s.y*shape.yRow+s.x*shape.yPixel));
 float u=float(sampleByte(shape.uOffset+(s.y/2u)*shape.uRow+(s.x/2u)*shape.uPixel))-128.0;
 float v=float(sampleByte(shape.vOffset+(s.y/2u)*shape.vRow+(s.x/2u)*shape.vPixel))-128.0;
 if(shape.fullRange==0u){yy=(yy-16.0)*255.0/219.0;u=u*255.0/224.0;v=v*255.0/224.0;}
 vec3 c=shape.bt709!=0u?vec3(yy+1.5748*v,yy-.187324*u-.468124*v,yy+1.8556*u):vec3(yy+1.402*v,yy-.344136*u-.714136*v,yy+1.772*u);
 return floor(clamp(c,0.0,255.0)+0.5)/255.0;
}
void main(){
 uvec2 p=gl_GlobalInvocationID.xy;if(p.x>=shape.width||p.y>=shape.height)return;
 vec3 center=samplePixel(ivec2(p));vec3 h[24];
 for(int j=0;j<24;j++)h[j]=vec3(B1[j]);
 for(int ky=0;ky<3;ky++)for(int kx=0;kx<3;kx++){
  ivec2 s=clamp(ivec2(p)+ivec2(kx-1,ky-1),ivec2(0),ivec2(shape.width-1,shape.height-1));
  vec3 v=samplePixel(s)-0.5;
  for(int j=0;j<24;j++)h[j]+=v*W1[(ky*3+kx)*24+j];
 }
 for(int sub=0;sub<4;sub++){
  vec3 v=center+B2[sub];for(int j=0;j<24;j++)v+=max(h[j],vec3(0.0))*W2[j*4+sub];
  uvec3 c=uvec3(clamp(v,0.0,1.0)*255.0+0.5);
  uint index=(p.y*2+uint(sub/2))*shape.width*2+p.x*2+uint(sub%2);
  outputFrame.pixels[index]=0xff000000u|(c.r<<16)|(c.g<<8)|c.b;
 }
}
'''
(root/'core/gpu/tinysr2.comp').write_text(shader)

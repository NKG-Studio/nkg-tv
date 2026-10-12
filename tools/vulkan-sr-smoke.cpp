#include "../core/gpu/vulkan_sr.hpp"
#include <chrono>
#include <fstream>
#include <iostream>
#include <iomanip>
static std::vector<uint32_t> read(const char* path){std::ifstream f(path,std::ios::binary|std::ios::ate);if(!f)throw std::runtime_error("open input");auto n=f.tellg();if(n<=0||n%4)throw std::runtime_error("input alignment");std::vector<uint32_t> v(size_t(n)/4);f.seekg(0);f.read(reinterpret_cast<char*>(v.data()),n);if(!f)throw std::runtime_error("read input");return v;}
int main(int argc,char** argv){try{
 if(argc!=8)throw std::runtime_error("usage: shader.spv input.argb output.argb width height repeats --allow-software|--hardware-only");uint32_t w=std::stoul(argv[4]),h=std::stoul(argv[5]);int repeats=std::stoi(argv[6]);if(repeats<1||repeats>120)throw std::runtime_error("repeat limit");auto input=read(argv[2]);if(input.size()!=uint64_t(w)*h)throw std::runtime_error("source size");auto shader=read(argv[1]);auto start=std::chrono::steady_clock::now();nkg::VulkanSr model(shader,std::string(argv[7])=="--allow-software");double setup=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-start).count();std::vector<uint32_t> output(size_t(w)*h*4);std::vector<double> samples;
 for(int i=0;i<repeats+2;i++){auto t=std::chrono::steady_clock::now();model.infer(input.data(),output.data(),w,h);double ms=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-t).count();if(i>=2)samples.push_back(ms);}
 std::ofstream f(argv[3],std::ios::binary);f.write(reinterpret_cast<const char*>(output.data()),output.size()*4);if(!f)throw std::runtime_error("write output");std::sort(samples.begin(),samples.end());
 std::cout<<"{\"passed\":true,\"device\":"<<std::quoted(model.properties.deviceName)<<",\"device_type\":"<<model.properties.deviceType<<",\"software\":"<<(model.properties.deviceType==VK_PHYSICAL_DEVICE_TYPE_CPU?"true":"false")<<",\"width\":"<<w<<",\"height\":"<<h<<",\"scale\":2,\"setup_ms\":"<<setup<<",\"warmup\":2,\"dispatches\":"<<repeats<<",\"p50_upload_compute_readback_ms\":"<<samples[samples.size()/2]<<",\"p95_upload_compute_readback_ms\":"<<samples[std::min(samples.size()-1,size_t(samples.size()*.95))]<<",\"scope\":\"Synthetic repeated-frame compute benchmark, NOT video playback FPS or production image quality\"}\n";
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<"\n";return 1;}return 0;}

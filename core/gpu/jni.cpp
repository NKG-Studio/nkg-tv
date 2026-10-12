#include "vulkan_sr.hpp"
#include <jni.h>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <sstream>
#include <iomanip>
namespace {
 std::mutex mutex;std::unordered_map<jlong,std::unique_ptr<nkg::VulkanSr>> sessions;jlong nextId=1;
 void fail(JNIEnv* e,const char* text){jclass c=e->FindClass("java/lang/IllegalStateException");if(c)e->ThrowNew(c,text);}
 nkg::VulkanSr& session(jlong id){auto i=sessions.find(id);if(i==sessions.end())throw std::runtime_error("closed GPU session");return *i->second;}
}
extern "C" JNIEXPORT jlong JNICALL Java_studio_nkg_tv_GpuCore_create(JNIEnv* e,jclass,jobject shader,jboolean allowSoftware){try{
 std::lock_guard<std::mutex> lock(mutex);auto* data=e->GetDirectBufferAddress(shader);jlong n=e->GetDirectBufferCapacity(shader);if(!data||n<4||n>256*1024||n%4)throw std::runtime_error("invalid shader bytes");std::vector<uint32_t> spirv(size_t(n)/4);std::memcpy(spirv.data(),data,size_t(n));auto model=std::make_unique<nkg::VulkanSr>(spirv,allowSoftware);jlong id=nextId++;sessions.emplace(id,std::move(model));return id;
 }catch(const std::exception& x){fail(e,x.what());return 0;}}
extern "C" JNIEXPORT jstring JNICALL Java_studio_nkg_tv_GpuCore_info(JNIEnv* e,jclass,jlong id){try{
 std::lock_guard<std::mutex> lock(mutex);auto p=session(id).properties;std::ostringstream s;s<<"{\"device\":"<<std::quoted(p.deviceName)<<",\"device_type\":"<<p.deviceType<<",\"api_version\":"<<p.apiVersion<<",\"driver_version\":"<<p.driverVersion<<",\"software\":"<<(p.deviceType==VK_PHYSICAL_DEVICE_TYPE_CPU?"true":"false")<<"}";return e->NewStringUTF(s.str().c_str());
 }catch(const std::exception& x){fail(e,x.what());return nullptr;}}
extern "C" JNIEXPORT jintArray JNICALL Java_studio_nkg_tv_GpuCore_yuv(JNIEnv* e,jclass,jlong id,jobject y,jobject u,jobject v,jintArray strides,jint w,jint h,jint cropX,jint cropY,jboolean bt709,jboolean fullRange){try{
 std::lock_guard<std::mutex> lock(mutex);if(w<1||h<1||w>4096||h>4096||int64_t(w)*h>4096ll*2160||cropX<0||cropY<0)throw std::runtime_error("invalid original dimensions");if(!strides||e->GetArrayLength(strides)!=6)throw std::runtime_error("invalid strides");jint s[6];e->GetIntArrayRegion(strides,0,6,s);if(e->ExceptionCheck())return nullptr;
 jobject buffers[3]={y,u,v};nkg::VulkanSr::Plane planes[3]{};for(int i=0;i<3;i++){void* p=e->GetDirectBufferAddress(buffers[i]);jlong bytes=e->GetDirectBufferCapacity(buffers[i]);if(!p||bytes<=0||s[i*2]<=0||s[i*2+1]<=0)throw std::runtime_error("invalid decoder plane");planes[i]={static_cast<uint8_t*>(p),size_t(bytes),uint32_t(s[i*2]),uint32_t(s[i*2+1])};}
 std::vector<uint32_t> output(size_t(w)*h*4);session(id).inferYuv(planes,output.data(),w,h,cropX,cropY,bt709,fullRange);jintArray result=e->NewIntArray(jsize(output.size()));if(!result)return nullptr;e->SetIntArrayRegion(result,0,jsize(output.size()),reinterpret_cast<const jint*>(output.data()));return result;
 }catch(const std::exception& x){fail(e,x.what());return nullptr;}}
extern "C" JNIEXPORT void JNICALL Java_studio_nkg_tv_GpuCore_destroy(JNIEnv*,jclass,jlong id){std::lock_guard<std::mutex> lock(mutex);sessions.erase(id);}

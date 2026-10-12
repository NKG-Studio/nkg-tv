#include <jni.h>
#include <net.h>
#include <mutex>
#include <vector>
#include <string>
static ncnn::Net network;
static std::mutex guard;
static bool ready = false;
extern "C" JNIEXPORT jboolean JNICALL Java_studio_nkg_tv_Core_initModel(JNIEnv* env, jclass, jstring path) {
    if (!path) return JNI_FALSE;
    const char* value=env->GetStringUTFChars(path,nullptr);if(!value)return JNI_FALSE;
    std::string base(value);env->ReleaseStringUTFChars(path,value);
    std::lock_guard<std::mutex> lock(guard);
    network.clear(); ready=false;
    network.opt.use_vulkan_compute=false; network.opt.num_threads=2;
    network.opt.use_fp16_arithmetic=false; network.opt.use_fp16_storage=false;
    if(network.load_param((base+"/realesr-animevideov3-x2.param").c_str())!=0)return JNI_FALSE;
    if(network.load_model((base+"/realesr-animevideov3-x2.bin").c_str())!=0)return JNI_FALSE;
    ready=true;return JNI_TRUE;
}
extern "C" JNIEXPORT jintArray JNICALL Java_studio_nkg_tv_Core_upscaleAnime(JNIEnv* env,jclass,jintArray array,jint w,jint h) {
    if(!array||w<1||h<1||w>320||h>180||env->GetArrayLength(array)!=w*h)return nullptr;
    std::lock_guard<std::mutex> lock(guard);if(!ready)return nullptr;
    std::vector<jint> input(w*h);env->GetIntArrayRegion(array,0,w*h,input.data());if(env->ExceptionCheck())return nullptr;
    std::vector<unsigned char> rgb(w*h*3);
    for(int i=0;i<w*h;i++){rgb[i*3]=(input[i]>>16)&255;rgb[i*3+1]=(input[i]>>8)&255;rgb[i*3+2]=input[i]&255;}
    ncnn::Mat image=ncnn::Mat::from_pixels(rgb.data(),ncnn::Mat::PIXEL_RGB,w,h);
    const float norm[3]={1.f/255,1.f/255,1.f/255};image.substract_mean_normalize(nullptr,norm);
    ncnn::Extractor extractor=network.create_extractor();ncnn::Mat out;
    if(extractor.input("data",image)!=0||extractor.extract("output",out)!=0||out.w!=w*2||out.h!=h*2||out.c!=3)return nullptr;
    const float denorm[3]={255,255,255};out.substract_mean_normalize(nullptr,denorm);
    std::vector<unsigned char> bytes(w*h*12);out.to_pixels(bytes.data(),ncnn::Mat::PIXEL_RGB);
    std::vector<jint> result(w*h*4);
    for(int i=0;i<w*h*4;i++)result[i]=(jint)(0xff000000u|(bytes[i*3]<<16)|(bytes[i*3+1]<<8)|bytes[i*3+2]);
    jintArray dest=env->NewIntArray(w*h*4);if(dest)env->SetIntArrayRegion(dest,0,w*h*4,result.data());return dest;
}

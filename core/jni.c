#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
extern int32_t nkg_upscale(const uint32_t*, uint32_t*, int32_t, int32_t);
JNIEXPORT jintArray JNICALL Java_studio_nkg_tv_Core_upscale(JNIEnv* env, jclass cls, jintArray pixels, jint width, jint height) {
    (void)cls;
    if (!pixels || width < 1 || height < 1 || width > 320 || height > 180 || (*env)->GetArrayLength(env, pixels) != width * height) return NULL;
    uint32_t* input = malloc((size_t)width * height * sizeof(uint32_t));
    uint32_t* output = malloc((size_t)width * height * 4 * sizeof(uint32_t));
    if (!input || !output) { free(input); free(output); return NULL; }
    (*env)->GetIntArrayRegion(env, pixels, 0, width * height, (jint*)input);
    if ((*env)->ExceptionCheck(env)) { free(input); free(output); return NULL; }
    int result = nkg_upscale(input, output, width, height);
    free(input);
    jintArray dest = NULL;
    if (result == 0) {
        dest = (*env)->NewIntArray(env, width * height * 4);
        if (dest) (*env)->SetIntArrayRegion(env, dest, 0, width * height * 4, (jint*)output);
    }
    free(output);
    return dest;
}

/*
 * MP10 JNI seam. Build this small adapter with the Android NDK against the
 * wellfriend-perception-ffi static/shared library. It contains no scanner
 * algorithm: all work delegates to the checked wf_* C ABI.
 */
#include <jni.h>
#include <stdint.h>
#include "wellfriend_perception.h"

JNIEXPORT jlong JNICALL Java_dev_wellfriend_scan_perception_JniNativePerceptionBridge_nativeCreate(JNIEnv *env, jclass cls, jstring config) {
    (void)cls;
    const char *text = (*env)->GetStringUTFChars(env, config, 0);
    WfEngine *engine = wf_engine_create(text);
    (*env)->ReleaseStringUTFChars(env, config, text);
    return (jlong)(intptr_t)engine;
}
JNIEXPORT void JNICALL Java_dev_wellfriend_scan_perception_JniNativePerceptionBridge_nativeDestroy(JNIEnv *env, jclass cls, jlong handle) { (void)env; (void)cls; wf_engine_destroy((WfEngine *)(intptr_t)handle); }
static jstring call(JNIEnv *env, jlong handle, jbyteArray image, jint width, jint height, jint stride, jstring format, jstring request, int operation) {
    const jsize length = (*env)->GetArrayLength(env, image);
    jbyte *bytes = (*env)->GetByteArrayElements(env, image, 0);
    const char *format_text = (*env)->GetStringUTFChars(env, format, 0);
    const char *request_text = (*env)->GetStringUTFChars(env, request, 0);
    char *response = operation == 0 ? wf_analyze_frame((WfEngine *)(intptr_t)handle, (const uint8_t *)bytes, (uint32_t)width, (uint32_t)height, (uint32_t)stride, format_text, request_text) : operation == 1 ? wf_reconstruct_page((WfEngine *)(intptr_t)handle, (const uint8_t *)bytes, (uint32_t)width, (uint32_t)height, (uint32_t)stride, format_text, request_text) : wf_apply_filter((WfEngine *)(intptr_t)handle, (const uint8_t *)bytes, (uint32_t)width, (uint32_t)height, (uint32_t)stride, format_text, request_text);
    (void)length;
    jstring output = (*env)->NewStringUTF(env, response);
    wf_string_free(response);
    (*env)->ReleaseStringUTFChars(env, request, request_text);
    (*env)->ReleaseStringUTFChars(env, format, format_text);
    (*env)->ReleaseByteArrayElements(env, image, bytes, JNI_ABORT);
    return output;
}
JNIEXPORT jstring JNICALL Java_dev_wellfriend_scan_perception_JniNativePerceptionBridge_nativeAnalyze(JNIEnv *env, jclass cls, jlong handle, jbyteArray image, jint width, jint height, jint stride, jstring format, jstring request) { (void)cls; return call(env, handle, image, width, height, stride, format, request, 0); }
JNIEXPORT jstring JNICALL Java_dev_wellfriend_scan_perception_JniNativePerceptionBridge_nativeReconstruct(JNIEnv *env, jclass cls, jlong handle, jbyteArray image, jint width, jint height, jint stride, jstring format, jstring request) { (void)cls; return call(env, handle, image, width, height, stride, format, request, 1); }
JNIEXPORT jstring JNICALL Java_dev_wellfriend_scan_perception_JniNativePerceptionBridge_nativeApplyFilter(JNIEnv *env, jclass cls, jlong handle, jbyteArray image, jint width, jint height, jint stride, jstring format, jstring request) { (void)cls; return call(env, handle, image, width, height, stride, format, request, 2); }

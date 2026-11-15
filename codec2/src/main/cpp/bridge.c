#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "codec2.h"
#define MAX_BYTES 4788
#define MAX_SAMPLES (MAX_BYTES / 6 * 320)
static void fail(JNIEnv *env) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (type) (*env)->ThrowNew(env, type, "Invalid Codec2 1200 audio");
}
JNIEXPORT jbyteArray JNICALL Java_net_meshstar_codec2_Codec2_encodeNative(JNIEnv *env, jclass cls, jshortArray input) {
    if (!input) { fail(env); return NULL; }
    jsize n = (*env)->GetArrayLength(env, input);
    if (n < 1 || n > MAX_SAMPLES) { fail(env); return NULL; }
    struct CODEC2 *codec = codec2_create(CODEC2_MODE_1200);
    if (!codec) { fail(env); return NULL; }
    if (codec2_samples_per_frame(codec) != 320 || codec2_bytes_per_frame(codec) != 6) {
        codec2_destroy(codec); fail(env); return NULL;
    }
    int size = ((n + 319) / 320) * 6;
    unsigned char bytes[MAX_BYTES];
    for (int i = 0; i < n; i += 320) {
        short samples[320] = {0};
        int count = n - i < 320 ? n - i : 320;
        (*env)->GetShortArrayRegion(env, input, i, count, samples);
        if ((*env)->ExceptionCheck(env)) { codec2_destroy(codec); return NULL; }
        codec2_encode(codec, bytes + i / 320 * 6, samples);
    }
    codec2_destroy(codec);
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, size, (jbyte *)bytes);
    return result;
}
JNIEXPORT jshortArray JNICALL Java_net_meshstar_codec2_Codec2_decodeNative(JNIEnv *env, jclass cls, jbyteArray input) {
    if (!input) { fail(env); return NULL; }
    jsize n = (*env)->GetArrayLength(env, input);
    if (n < 6 || n > MAX_BYTES || n % 6 != 0) { fail(env); return NULL; }
    unsigned char bytes[MAX_BYTES];
    (*env)->GetByteArrayRegion(env, input, 0, n, (jbyte *)bytes);
    if ((*env)->ExceptionCheck(env)) return NULL;
    struct CODEC2 *codec = codec2_create(CODEC2_MODE_1200);
    if (!codec) { fail(env); return NULL; }
    jshortArray result = (*env)->NewShortArray(env, n / 6 * 320);
    if (result) for (int i = 0; i < n; i += 6) {
        short samples[320];
        codec2_decode(codec, samples, bytes + i);
        (*env)->SetShortArrayRegion(env, result, i / 6 * 320, 320, samples);
        if ((*env)->ExceptionCheck(env)) break;
    }
    codec2_destroy(codec);
    return result;
}

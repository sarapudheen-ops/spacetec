#ifndef J2534_JNI_H
#define J2534_JNI_H

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "J2534_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// J2534 error codes
#define STATUS_NOERROR 0x00000000
#define ERR_NOT_SUPPORTED 0x00000001
#define ERR_INVALID_CHANNEL_ID 0x00000002
#define ERR_INVALID_PROTOCOL_ID 0x00000003
#define ERR_NULL_PARAMETER 0x00000004
#define ERR_INVALID_IOCTL_VALUE 0x00000005
#define ERR_INVALID_FLAGS 0x00000006
#define ERR_FAILED 0x00000007
#define ERR_DEVICE_NOT_CONNECTED 0x00000008
#define ERR_TIMEOUT 0x00000009
#define ERR_INVALID_DEVICE_ID 0x0000000A
#define ERR_INVALID_FUNCTION 0x0000000B
#define ERR_INVALID_MSG 0x0000000C
#define ERR_INVALID_TIME_INTERVAL 0x0000000D
#define ERR_INVALID_MSG_ID 0x0000000E
#define ERR_DEVICE_IN_USE 0x0000000F
#define ERR_INVALID_IOCTL_ID 0x00000010
#define ERR_BUFFER_EMPTY 0x00000011
#define ERR_BUFFER_FULL 0x00000012
#define ERR_BUFFER_OVERFLOW 0x00000013
#define ERR_PIN_INVALID 0x00000014
#define ERR_CHANNEL_IN_USE 0x00000015
#define ERR_MSG_PROTOCOL_ID 0x00000016
#define ERR_INVALID_FILTER_ID 0x00000017
#define ERR_NO_FLOW_CONTROL 0x00000018
#define ERR_NOT_UNIQUE 0x00000019
#define ERR_INVALID_BAUDRATE 0x0000001A
#define ERR_INVALID_DEVICE_STATE 0x0000001B
#define ERR_INVALID_TRANSMIT_PATTERN 0x0000001C
#define ERR_INSUFFICIENT_MEMORY 0x0000001D

// J2534 protocol IDs
#define J1850VPW 1
#define J1850PWM 2
#define ISO9141 3
#define ISO14230 4
#define CAN 5
#define ISO15765 6
#define SCI_A_ENGINE 7
#define SCI_A_TRANS 8
#define SCI_B_ENGINE 9
#define SCI_B_TRANS 10

// J2534 filter types
#define PASS_FILTER 1
#define BLOCK_FILTER 2
#define FLOW_CONTROL_FILTER 3

// J2534 connect flags
#define CAN_29BIT_ID 0x0100
#define CAN_ID_BOTH 0x0200
#define CAN_ISO_BRP 0x0400
#define CAN_HS_DATA 0x0800

#ifdef __cplusplus
extern "C" {
#endif

// JNI function declarations
JNIEXPORT jboolean JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_initialize(JNIEnv *env, jobject thiz);

JNIEXPORT jobject JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_scanForDevices(JNIEnv *env, jobject thiz);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_connect(JNIEnv *env, jobject thiz, 
                                                 jlong deviceHandle, jlong protocol, 
                                                 jlong flags, jlong baudrate);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_disconnect(JNIEnv *env, jobject thiz, jlong handle);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_readMessages(JNIEnv *env, jobject thiz, 
                                                      jlong handle, jobjectArray messages, 
                                                      jint numMessages, jlong timeout);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_writeMessages(JNIEnv *env, jobject thiz, 
                                                       jlong handle, jobjectArray messages, 
                                                       jint numMessages, jlong timeout);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_startPeriodicMessage(JNIEnv *env, jobject thiz, 
                                                              jlong handle, jobject message, 
                                                              jlong id, jlong period);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_stopPeriodicMessage(JNIEnv *env, jobject thiz, 
                                                             jlong handle, jlong id);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_startMessageFilter(JNIEnv *env, jobject thiz, 
                                                            jlong handle, jlong filterType, 
                                                            jobject mask, jobject pattern, 
                                                            jobject flowControl);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_stopMessageFilter(JNIEnv *env, jobject thiz, 
                                                           jlong handle, jlong filterId);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_setProgrammingVoltage(JNIEnv *env, jobject thiz, 
                                                               jlong handle, jlong pinNumber, 
                                                               jlong voltage);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_readVersion(JNIEnv *env, jobject thiz, 
                                                     jlong handle, jstring apiVersion, 
                                                     jstring dllVersion, jstring devVersion);

JNIEXPORT jstring JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_getLastError(JNIEnv *env, jobject thiz);

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_ioctl(JNIEnv *env, jobject thiz, 
                                               jlong handle, jlong ioControlCode, 
                                               jlong input, jlong output);

JNIEXPORT void JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_cleanup(JNIEnv *env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // J2534_JNI_H
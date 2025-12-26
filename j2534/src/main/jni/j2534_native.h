/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

#ifndef J2534_NATIVE_H
#define J2534_NATIVE_H

#include <jni.h>
#include <windows.h>

// J2534 API function pointers
typedef long (*PassThruOpen_t)(void* pName, unsigned long* pDeviceID);
typedef long (*PassThruClose_t)(unsigned long DeviceID);
typedef long (*PassThruConnect_t)(unsigned long DeviceID, unsigned long ProtocolID, 
                                  unsigned long Flags, unsigned long Baudrate, 
                                  unsigned long* pChannelID);
typedef long (*PassThruDisconnect_t)(unsigned long ChannelID);
typedef long (*PassThruReadMsgs_t)(unsigned long ChannelID, void* pMsg, 
                                   unsigned long* pNumMsgs, unsigned long Timeout);
typedef long (*PassThruWriteMsgs_t)(unsigned long ChannelID, void* pMsg, 
                                    unsigned long* pNumMsgs, unsigned long Timeout);
typedef long (*PassThruStartMsgFilter_t)(unsigned long ChannelID, unsigned long FilterType,
                                         void* pMaskMsg, void* pPatternMsg, 
                                         void* pFlowControlMsg, unsigned long* pFilterID);
typedef long (*PassThruStopMsgFilter_t)(unsigned long ChannelID, unsigned long FilterID);
typedef long (*PassThruSetProgrammingVoltage_t)(unsigned long DeviceID, unsigned long PinNumber, 
                                                unsigned long Voltage);
typedef long (*PassThruReadVersion_t)(unsigned long DeviceID, char* pFirmwareVersion, 
                                      char* pDllVersion, char* pApiVersion);
typedef long (*PassThruGetLastError_t)(char* pErrorDescription);
typedef long (*PassThruIoctl_t)(unsigned long ChannelID, unsigned long IoctlID, 
                                void* pInput, void* pOutput);

// J2534 message structure
typedef struct {
    unsigned long ProtocolID;
    unsigned long RxStatus;
    unsigned long TxFlags;
    unsigned long Timestamp;
    unsigned long DataSize;
    unsigned long ExtraDataIndex;
    unsigned char Data[4128];
} PASSTHRU_MSG;

// J2534 configuration structure
typedef struct {
    unsigned long Parameter;
    unsigned long Value;
} SCONFIG;

// J2534 configuration list
typedef struct {
    unsigned long NumOfParams;
    SCONFIG* ConfigPtr;
} SCONFIG_LIST;

// Library handle structure
typedef struct {
    HMODULE hLibrary;
    PassThruOpen_t PassThruOpen;
    PassThruClose_t PassThruClose;
    PassThruConnect_t PassThruConnect;
    PassThruDisconnect_t PassThruDisconnect;
    PassThruReadMsgs_t PassThruReadMsgs;
    PassThruWriteMsgs_t PassThruWriteMsgs;
    PassThruStartMsgFilter_t PassThruStartMsgFilter;
    PassThruStopMsgFilter_t PassThruStopMsgFilter;
    PassThruSetProgrammingVoltage_t PassThruSetProgrammingVoltage;
    PassThruReadVersion_t PassThruReadVersion;
    PassThruGetLastError_t PassThruGetLastError;
    PassThruIoctl_t PassThruIoctl;
} J2534_LIBRARY;

// Global variables
extern J2534_LIBRARY* g_j2534_lib;
extern unsigned long g_last_error;

// Function prototypes
#ifdef __cplusplus
extern "C" {
#endif

// JNI function declarations
JNIEXPORT jlong JNICALL Java_com_spacetec_j2534_J2534Interface_nativeLoadLibrary
  (JNIEnv *, jobject, jstring);

JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruOpen
  (JNIEnv *, jobject, jstring);

JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruClose
  (JNIEnv *, jobject, jint);

JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruConnect
  (JNIEnv *, jobject, jint, jint, jint, jint);

JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruDisconnect
  (JNIEnv *, jobject, jint);

JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativeGetLastError
  (JNIEnv *, jobject);

// Utility functions
J2534_LIBRARY* load_j2534_library(const char* library_path);
void unload_j2534_library(J2534_LIBRARY* lib);
void convert_java_message_to_native(JNIEnv* env, jobject java_msg, PASSTHRU_MSG* native_msg);
jobject convert_native_message_to_java(JNIEnv* env, PASSTHRU_MSG* native_msg);
void convert_java_config_to_native(JNIEnv* env, jobjectArray java_configs, SCONFIG_LIST* native_configs);

#ifdef __cplusplus
}
#endif

#endif // J2534_NATIVE_H
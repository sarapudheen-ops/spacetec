/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

#include "j2534_native.h"
#include <string.h>
#include <stdlib.h>

// Global variables
J2534_LIBRARY* g_j2534_lib = nullptr;
unsigned long g_last_error = 0;

// Device and channel tracking
static unsigned long g_next_device_id = 1;
static unsigned long g_next_channel_id = 1;

/*
 * Class:     com_spacetec_j2534_J2534Interface
 * Method:    nativeLoadLibrary
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_spacetec_j2534_J2534Interface_nativeLoadLibrary
  (JNIEnv *env, jobject obj, jstring library_path) {
    
    const char* path = env->GetStringUTFChars(library_path, nullptr);
    if (path == nullptr) {
        g_last_error = 0x00000004; // ERR_NULL_PARAMETER
        return 0;
    }
    
    J2534_LIBRARY* lib = load_j2534_library(path);
    env->ReleaseStringUTFChars(library_path, path);
    
    if (lib == nullptr) {
        g_last_error = 0x00000007; // ERR_FAILED
        return 0;
    }
    
    g_j2534_lib = lib;
    g_last_error = 0; // STATUS_NOERROR
    return reinterpret_cast<jlong>(lib);
}

/*
 * Class:     com_spacetec_j2534_J2534Interface
 * Method:    nativePassThruOpen
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruOpen
  (JNIEnv *env, jobject obj, jstring name) {
    
    if (g_j2534_lib == nullptr || g_j2534_lib->PassThruOpen == nullptr) {
        g_last_error = 0x00000008; // ERR_DEVICE_NOT_CONNECTED
        return -1;
    }
    
    const char* device_name = nullptr;
    if (name != nullptr) {
        device_name = env->GetStringUTFChars(name, nullptr);
    }
    
    unsigned long device_id;
    long result = g_j2534_lib->PassThruOpen(const_cast<char*>(device_name), &device_id);
    
    if (device_name != nullptr) {
        env->ReleaseStringUTFChars(name, device_name);
    }
    
    g_last_error = result;
    
    if (result == 0) { // STATUS_NOERROR
        return static_cast<jint>(device_id);
    } else {
        return -1;
    }
}

/*
 * Class:     com_spacetec_j2534_J2534Interface
 * Method:    nativePassThruClose
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruClose
  (JNIEnv *env, jobject obj, jint device_id) {
    
    if (g_j2534_lib == nullptr || g_j2534_lib->PassThruClose == nullptr) {
        g_last_error = 0x00000008; // ERR_DEVICE_NOT_CONNECTED
        return 0x00000008;
    }
    
    long result = g_j2534_lib->PassThruClose(static_cast<unsigned long>(device_id));
    g_last_error = result;
    
    return static_cast<jint>(result);
}

/*
 * Class:     com_spacetec_j2534_J2534Interface
 * Method:    nativePassThruConnect
 * Signature: (IIII)I
 */
JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruConnect
  (JNIEnv *env, jobject obj, jint device_id, jint protocol_id, jint flags, jint baud_rate) {
    
    if (g_j2534_lib == nullptr || g_j2534_lib->PassThruConnect == nullptr) {
        g_last_error = 0x00000008; // ERR_DEVICE_NOT_CONNECTED
        return -1;
    }
    
    unsigned long channel_id;
    long result = g_j2534_lib->PassThruConnect(
        static_cast<unsigned long>(device_id),
        static_cast<unsigned long>(protocol_id),
        static_cast<unsigned long>(flags),
        static_cast<unsigned long>(baud_rate),
        &channel_id
    );
    
    g_last_error = result;
    
    if (result == 0) { // STATUS_NOERROR
        return static_cast<jint>(channel_id);
    } else {
        return -1;
    }
}

/*
 * Class:     com_spacetec_j2534_J2534Interface
 * Method:    nativePassThruDisconnect
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativePassThruDisconnect
  (JNIEnv *env, jobject obj, jint channel_id) {
    
    if (g_j2534_lib == nullptr || g_j2534_lib->PassThruDisconnect == nullptr) {
        g_last_error = 0x00000008; // ERR_DEVICE_NOT_CONNECTED
        return 0x00000008;
    }
    
    long result = g_j2534_lib->PassThruDisconnect(static_cast<unsigned long>(channel_id));
    g_last_error = result;
    
    return static_cast<jint>(result);
}

/*
 * Class:     com_spacetec_j2534_J2534Interface
 * Method:    nativeGetLastError
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_spacetec_j2534_J2534Interface_nativeGetLastError
  (JNIEnv *env, jobject obj) {
    return static_cast<jint>(g_last_error);
}

// Utility function implementations
J2534_LIBRARY* load_j2534_library(const char* library_path) {
    HMODULE hLib = LoadLibraryA(library_path);
    if (hLib == nullptr) {
        return nullptr;
    }
    
    J2534_LIBRARY* lib = static_cast<J2534_LIBRARY*>(malloc(sizeof(J2534_LIBRARY)));
    if (lib == nullptr) {
        FreeLibrary(hLib);
        return nullptr;
    }
    
    lib->hLibrary = hLib;
    
    // Load function pointers
    lib->PassThruOpen = reinterpret_cast<PassThruOpen_t>(GetProcAddress(hLib, "PassThruOpen"));
    lib->PassThruClose = reinterpret_cast<PassThruClose_t>(GetProcAddress(hLib, "PassThruClose"));
    lib->PassThruConnect = reinterpret_cast<PassThruConnect_t>(GetProcAddress(hLib, "PassThruConnect"));
    lib->PassThruDisconnect = reinterpret_cast<PassThruDisconnect_t>(GetProcAddress(hLib, "PassThruDisconnect"));
    lib->PassThruReadMsgs = reinterpret_cast<PassThruReadMsgs_t>(GetProcAddress(hLib, "PassThruReadMsgs"));
    lib->PassThruWriteMsgs = reinterpret_cast<PassThruWriteMsgs_t>(GetProcAddress(hLib, "PassThruWriteMsgs"));
    lib->PassThruStartMsgFilter = reinterpret_cast<PassThruStartMsgFilter_t>(GetProcAddress(hLib, "PassThruStartMsgFilter"));
    lib->PassThruStopMsgFilter = reinterpret_cast<PassThruStopMsgFilter_t>(GetProcAddress(hLib, "PassThruStopMsgFilter"));
    lib->PassThruSetProgrammingVoltage = reinterpret_cast<PassThruSetProgrammingVoltage_t>(GetProcAddress(hLib, "PassThruSetProgrammingVoltage"));
    lib->PassThruReadVersion = reinterpret_cast<PassThruReadVersion_t>(GetProcAddress(hLib, "PassThruReadVersion"));
    lib->PassThruGetLastError = reinterpret_cast<PassThruGetLastError_t>(GetProcAddress(hLib, "PassThruGetLastError"));
    lib->PassThruIoctl = reinterpret_cast<PassThruIoctl_t>(GetProcAddress(hLib, "PassThruIoctl"));
    
    // Verify essential functions are loaded
    if (lib->PassThruOpen == nullptr || lib->PassThruClose == nullptr ||
        lib->PassThruConnect == nullptr || lib->PassThruDisconnect == nullptr) {
        FreeLibrary(hLib);
        free(lib);
        return nullptr;
    }
    
    return lib;
}

void unload_j2534_library(J2534_LIBRARY* lib) {
    if (lib != nullptr) {
        if (lib->hLibrary != nullptr) {
            FreeLibrary(lib->hLibrary);
        }
        free(lib);
    }
}

void convert_java_message_to_native(JNIEnv* env, jobject java_msg, PASSTHRU_MSG* native_msg) {
    if (java_msg == nullptr || native_msg == nullptr) {
        return;
    }
    
    jclass msg_class = env->GetObjectClass(java_msg);
    
    // Get field IDs
    jfieldID protocol_id_field = env->GetFieldID(msg_class, "protocolId", "I");
    jfieldID rx_status_field = env->GetFieldID(msg_class, "rxStatus", "I");
    jfieldID tx_flags_field = env->GetFieldID(msg_class, "txFlags", "I");
    jfieldID timestamp_field = env->GetFieldID(msg_class, "timestamp", "J");
    jfieldID data_size_field = env->GetFieldID(msg_class, "dataSize", "I");
    jfieldID extra_data_index_field = env->GetFieldID(msg_class, "extraDataIndex", "I");
    jfieldID data_field = env->GetFieldID(msg_class, "data", "[B");
    
    // Copy fields
    native_msg->ProtocolID = static_cast<unsigned long>(env->GetIntField(java_msg, protocol_id_field));
    native_msg->RxStatus = static_cast<unsigned long>(env->GetIntField(java_msg, rx_status_field));
    native_msg->TxFlags = static_cast<unsigned long>(env->GetIntField(java_msg, tx_flags_field));
    native_msg->Timestamp = static_cast<unsigned long>(env->GetLongField(java_msg, timestamp_field));
    native_msg->DataSize = static_cast<unsigned long>(env->GetIntField(java_msg, data_size_field));
    native_msg->ExtraDataIndex = static_cast<unsigned long>(env->GetIntField(java_msg, extra_data_index_field));
    
    // Copy data array
    jbyteArray data_array = static_cast<jbyteArray>(env->GetObjectField(java_msg, data_field));
    if (data_array != nullptr) {
        jbyte* data_bytes = env->GetByteArrayElements(data_array, nullptr);
        jsize data_length = env->GetArrayLength(data_array);
        
        size_t copy_length = static_cast<size_t>(data_length);
        if (copy_length > sizeof(native_msg->Data)) {
            copy_length = sizeof(native_msg->Data);
        }
        
        memcpy(native_msg->Data, data_bytes, copy_length);
        env->ReleaseByteArrayElements(data_array, data_bytes, JNI_ABORT);
    }
    
    env->DeleteLocalRef(msg_class);
}

jobject convert_native_message_to_java(JNIEnv* env, PASSTHRU_MSG* native_msg) {
    if (native_msg == nullptr) {
        return nullptr;
    }
    
    // Find the J2534Message class
    jclass msg_class = env->FindClass("com/spacetec/j2534/J2534Message");
    if (msg_class == nullptr) {
        return nullptr;
    }
    
    // Get constructor
    jmethodID constructor = env->GetMethodID(msg_class, "<init>", "(IIJII[B)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(msg_class);
        return nullptr;
    }
    
    // Create data array
    jbyteArray data_array = env->NewByteArray(static_cast<jsize>(native_msg->DataSize));
    if (data_array != nullptr && native_msg->DataSize > 0) {
        env->SetByteArrayRegion(data_array, 0, static_cast<jsize>(native_msg->DataSize),
                               reinterpret_cast<const jbyte*>(native_msg->Data));
    }
    
    // Create Java object
    jobject java_msg = env->NewObject(msg_class, constructor,
        static_cast<jint>(native_msg->ProtocolID),
        static_cast<jint>(native_msg->RxStatus),
        static_cast<jint>(native_msg->TxFlags),
        static_cast<jlong>(native_msg->Timestamp),
        static_cast<jint>(native_msg->DataSize),
        static_cast<jint>(native_msg->ExtraDataIndex),
        data_array
    );
    
    env->DeleteLocalRef(msg_class);
    if (data_array != nullptr) {
        env->DeleteLocalRef(data_array);
    }
    
    return java_msg;
}
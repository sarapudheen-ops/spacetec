#include "j2534_jni.h"
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// Structure to represent a J2534 device
typedef struct {
    jlong handle;
    char name[256];
    char vendor[128];
    char firmware_version[64];
    char dll_version[64];
    char api_version[64];
} J2534DeviceStruct;

// Structure to represent a J2534 message
typedef struct {
    jlong protocolID;
    jlong rxStatus;
    jlong txFlags;
    jlong timestamp;
    jbyteArray data;
    jint extraDataIndex;
} J2534MessageStruct;

// Global variables for thread safety and device management
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;
static J2534DeviceStruct g_devices[32]; // Support up to 32 devices
static int g_device_count = 0;
static char g_last_error[512] = {0};

// Helper function to get Java class and method IDs
static jclass getMessageClass(JNIEnv *env) {
    jclass cls = (*env)->FindClass(env, "com/spacetec/j2534/J2534Message");
    return (*env)->NewGlobalRef(env, cls);
}

static jfieldID getProtocolIdField(JNIEnv *env, jclass msgClass) {
    return (*env)->GetFieldID(env, msgClass, "protocolID", "J");
}

static jfieldID getRxStatusField(JNIEnv *env, jclass msgClass) {
    return (*env)->GetFieldID(env, msgClass, "rxStatus", "J");
}

static jfieldID getTxFlagsField(JNIEnv *env, jclass msgClass) {
    return (*env)->GetFieldID(env, msgClass, "txFlags", "J");
}

static jfieldID getTimestampField(JNIEnv *env, jclass msgClass) {
    return (*env)->GetFieldID(env, msgClass, "timestamp", "J");
}

static jfieldID getDataField(JNIEnv *env, jclass msgClass) {
    return (*env)->GetFieldID(env, msgClass, "data", "[B");
}

static jfieldID getExtraDataIndexField(JNIEnv *env, jclass msgClass) {
    return (*env)->GetFieldID(env, msgClass, "extraDataIndex", "I");
}

// Helper function to convert C message to Java message
static void setJavaMessage(JNIEnv *env, jobject javaMsg, J2534MessageStruct *cMsg) {
    jclass msgClass = getMessageClass(env);
    jfieldID protocolIdField = getProtocolIdField(env, msgClass);
    jfieldID rxStatusField = getRxStatusField(env, msgClass);
    jfieldID txFlagsField = getTxFlagsField(env, msgClass);
    jfieldID timestampField = getTimestampField(env, msgClass);
    jfieldID dataField = getDataField(env, msgClass);
    jfieldID extraDataIndexField = getExtraDataIndexField(env, msgClass);

    (*env)->SetLongField(env, javaMsg, protocolIdField, cMsg->protocolID);
    (*env)->SetLongField(env, javaMsg, rxStatusField, cMsg->rxStatus);
    (*env)->SetLongField(env, javaMsg, txFlagsField, cMsg->txFlags);
    (*env)->SetLongField(env, javaMsg, timestampField, cMsg->timestamp);
    (*env)->SetIntField(env, javaMsg, extraDataIndexField, cMsg->extraDataIndex);

    if (cMsg->data != NULL) {
        (*env)->SetObjectField(env, javaMsg, dataField, cMsg->data);
    }
}

// Helper function to convert Java message to C message
static void getJavaMessage(JNIEnv *env, jobject javaMsg, J2534MessageStruct *cMsg) {
    jclass msgClass = getMessageClass(env);
    jfieldID protocolIdField = getProtocolIdField(env, msgClass);
    jfieldID rxStatusField = getRxStatusField(env, msgClass);
    jfieldID txFlagsField = getTxFlagsField(env, msgClass);
    jfieldID timestampField = getTimestampField(env, msgClass);
    jfieldID dataField = getDataField(env, msgClass);
    jfieldID extraDataIndexField = getExtraDataIndexField(env, msgClass);

    cMsg->protocolID = (*env)->GetLongField(env, javaMsg, protocolIdField);
    cMsg->rxStatus = (*env)->GetLongField(env, javaMsg, rxStatusField);
    cMsg->txFlags = (*env)->GetLongField(env, javaMsg, txFlagsField);
    cMsg->timestamp = (*env)->GetLongField(env, javaMsg, timestampField);
    cMsg->extraDataIndex = (*env)->GetIntField(env, javaMsg, extraDataIndexField);

    jobject dataObj = (*env)->GetObjectField(env, javaMsg, dataField);
    if (dataObj != NULL) {
        cMsg->data = (*env)->NewGlobalRef(env, dataObj);
    } else {
        cMsg->data = NULL;
    }
}

// Set the last error message
static void setLastError(const char* error) {
    pthread_mutex_lock(&g_mutex);
    strncpy(g_last_error, error, sizeof(g_last_error) - 1);
    g_last_error[sizeof(g_last_error) - 1] = '\0';
    pthread_mutex_unlock(&g_mutex);
}

// JNI implementation functions

JNIEXPORT jboolean JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_initialize(JNIEnv *env, jobject thiz) {
    LOGI("Initializing J2534 JNI wrapper");
    
    pthread_mutex_lock(&g_mutex);
    g_device_count = 0;
    memset(g_devices, 0, sizeof(g_devices));
    memset(g_last_error, 0, sizeof(g_last_error));
    pthread_mutex_unlock(&g_mutex);
    
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_scanForDevices(JNIEnv *env, jobject thiz) {
    LOGI("Scanning for J2534 devices");
    
    pthread_mutex_lock(&g_mutex);
    
    // Create ArrayList class
    jclass listClass = (*env)->FindClass(env, "java/util/ArrayList");
    jmethodID listConstructor = (*env)->GetMethodID(env, listClass, "<init>", "()V");
    jmethodID listAdd = (*env)->GetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
    
    jobject deviceList = (*env)->NewObject(env, listClass, listConstructor);
    
    // Create J2534Device class
    jclass deviceClass = (*env)->FindClass(env, "com/spacetec/j2534/J2534Device");
    jmethodID deviceConstructor = (*env)->GetMethodID(env, deviceClass, "<init>", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID deviceGetName = (*env)->GetMethodID(env, deviceClass, "getName", "()Ljava/lang/String;");
    
    // Simulate finding some devices (in a real implementation, this would scan USB)
    for (int i = 0; i < 2; i++) {
        if (g_device_count < 32) {
            J2534DeviceStruct *device = &g_devices[g_device_count];
            device->handle = 1000 + i; // Device handles start from 1000
            
            snprintf(device->name, sizeof(device->name), "J2534_Device_%d", i);
            snprintf(device->vendor, sizeof(device->vendor), "Vendor_%d", i);
            snprintf(device->firmware_version, sizeof(device->firmware_version), "1.0.%d", i);
            snprintf(device->dll_version, sizeof(device->dll_version), "04.04");
            snprintf(device->api_version, sizeof(device->api_version), "04.04");
            
            jstring name = (*env)->NewStringUTF(env, device->name);
            jstring vendor = (*env)->NewStringUTF(env, device->vendor);
            jstring firmwareVersion = (*env)->NewStringUTF(env, device->firmware_version);
            jstring dllVersion = (*env)->NewStringUTF(env, device->dll_version);
            jstring apiVersion = (*env)->NewStringUTF(env, device->api_version);
            
            jobject deviceObj = (*env)->NewObject(env, deviceClass, deviceConstructor,
                                                  device->handle, name, vendor,
                                                  firmwareVersion, dllVersion, apiVersion);
            
            (*env)->CallBooleanMethod(env, deviceList, listAdd, deviceObj);
            
            g_device_count++;
        }
    }
    
    pthread_mutex_unlock(&g_mutex);
    return deviceList;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_connect(JNIEnv *env, jobject thiz,
                                                 jlong deviceHandle, jlong protocol,
                                                 jlong flags, jlong baudrate) {
    LOGI("Connecting to device %ld with protocol %ld, flags %ld, baudrate %ld",
         deviceHandle, protocol, flags, baudrate);
    
    // Validate protocol
    if (protocol < 1 || protocol > 10) {
        setLastError("Invalid protocol ID");
        return ERR_INVALID_PROTOCOL_ID;
    }
    
    // In a real implementation, this would establish the connection
    // For now, we'll just return a channel handle
    jlong channelHandle = deviceHandle + 10000; // Channel handles are device handle + 10000
    
    return channelHandle;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_disconnect(JNIEnv *env, jobject thiz, jlong handle) {
    LOGI("Disconnecting channel %ld", handle);
    
    // In a real implementation, this would close the connection
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_readMessages(JNIEnv *env, jobject thiz,
                                                      jlong handle, jobjectArray messages,
                                                      jint numMessages, jlong timeout) {
    LOGI("Reading messages from channel %ld, numMessages: %d, timeout: %ld",
         handle, numMessages, timeout);
    
    if (messages == NULL) {
        setLastError("Messages array is null");
        return ERR_NULL_PARAMETER;
    }
    
    // In a real implementation, this would read messages from the device
    // For now, we'll simulate some messages
    int actualMessages = 0;
    int messagesToReturn = (numMessages < 3) ? numMessages : 3; // Return max 3 messages
    
    for (int i = 0; i < messagesToReturn; i++) {
        jobject msgObj = (*env)->GetObjectArrayElement(env, messages, i);
        if (msgObj != NULL) {
            J2534MessageStruct cMsg = {0};
            cMsg.protocolID = ISO15765; // Default to ISO-TP
            cMsg.rxStatus = 0;
            cMsg.txFlags = 0;
            cMsg.timestamp = (jlong)(time(NULL) * 1000); // Current time in ms
            
            // Create some sample data
            jbyteArray data = (*env)->NewByteArray(env, 8);
            jbyte sampleData[] = {0x01, 0x22, 0xF1, 0x90, 0x41, 0x00, 0x00, 0x00};
            (*env)->SetByteArrayRegion(env, data, 0, 8, sampleData);
            
            cMsg.data = data;
            cMsg.extraDataIndex = 0;
            
            setJavaMessage(env, msgObj, &cMsg);
            actualMessages++;
        }
    }
    
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_writeMessages(JNIEnv *env, jobject thiz,
                                                       jlong handle, jobjectArray messages,
                                                       jint numMessages, jlong timeout) {
    LOGI("Writing messages to channel %ld, numMessages: %d, timeout: %ld",
         handle, numMessages, timeout);
    
    if (messages == NULL) {
        setLastError("Messages array is null");
        return ERR_NULL_PARAMETER;
    }
    
    // In a real implementation, this would write messages to the device
    // For now, we'll just validate the messages
    for (int i = 0; i < numMessages; i++) {
        jobject msgObj = (*env)->GetObjectArrayElement(env, messages, i);
        if (msgObj != NULL) {
            J2534MessageStruct cMsg;
            getJavaMessage(env, msgObj, &cMsg);
            
            // Validate message
            if (cMsg.protocolID < 1 || cMsg.protocolID > 10) {
                setLastError("Invalid protocol ID in message");
                return ERR_INVALID_MSG;
            }
        }
    }
    
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_startPeriodicMessage(JNIEnv *env, jobject thiz,
                                                              jlong handle, jobject message,
                                                              jlong id, jlong period) {
    LOGI("Starting periodic message on channel %ld, id: %ld, period: %ld", handle, id, period);
    
    if (message == NULL) {
        setLastError("Message is null");
        return ERR_NULL_PARAMETER;
    }
    
    // In a real implementation, this would start sending the message periodically
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_stopPeriodicMessage(JNIEnv *env, jobject thiz,
                                                             jlong handle, jlong id) {
    LOGI("Stopping periodic message on channel %ld, id: %ld", handle, id);
    
    // In a real implementation, this would stop the periodic message
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_startMessageFilter(JNIEnv *env, jobject thiz,
                                                            jlong handle, jlong filterType,
                                                            jobject mask, jobject pattern,
                                                            jobject flowControl) {
    LOGI("Starting message filter on channel %ld, type: %ld", handle, filterType);
    
    if (mask == NULL || pattern == NULL) {
        setLastError("Mask or pattern is null");
        return ERR_NULL_PARAMETER;
    }
    
    // Validate filter type
    if (filterType != PASS_FILTER && filterType != BLOCK_FILTER && filterType != FLOW_CONTROL_FILTER) {
        setLastError("Invalid filter type");
        return ERR_INVALID_IOCTL_VALUE;
    }
    
    // In a real implementation, this would create the filter
    // Return a filter ID (we'll use 1000 + handle as a simple ID)
    return 1000 + handle;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_stopMessageFilter(JNIEnv *env, jobject thiz,
                                                           jlong handle, jlong filterId) {
    LOGI("Stopping message filter on channel %ld, id: %ld", handle, filterId);
    
    // In a real implementation, this would remove the filter
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_setProgrammingVoltage(JNIEnv *env, jobject thiz,
                                                               jlong handle, jlong pinNumber,
                                                               jlong voltage) {
    LOGI("Setting programming voltage on handle %ld, pin: %ld, voltage: %ld mV", 
         handle, pinNumber, voltage);
    
    // Validate pin number (J1962 connector has pins 1-16)
    if (pinNumber < 1 || pinNumber > 16) {
        setLastError("Invalid pin number");
        return ERR_PIN_INVALID;
    }
    
    // Validate voltage (common values are 0, 7200, 12000 for 0V, 7.2V, 12V)
    if (voltage != 0 && voltage != 7200 && voltage != 12000) {
        // Allow other values for flexibility
        LOGI("Warning: Unusual voltage value %ld mV", voltage);
    }
    
    // In a real implementation, this would set the programming voltage
    return STATUS_NOERROR;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_readVersion(JNIEnv *env, jobject thiz,
                                                     jlong handle, jstring apiVersion,
                                                     jstring dllVersion, jstring devVersion) {
    LOGI("Reading version for handle %ld", handle);
    
    const char* apiVer = "04.04";
    const char* dllVer = "04.04.0001";
    const char* devVer = "J2534-1 Device";
    
    // Get the string builder objects and append the version info
    jclass stringClass = (*env)->FindClass(env, "java/lang/StringBuilder");
    jmethodID appendMethod = (*env)->GetMethodID(env, stringClass, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    
    if (apiVersion != NULL) {
        jobject apiBuilder = (*env)->NewLocalRef(env, apiVersion);
        (*env)->CallObjectMethod(env, apiBuilder, appendMethod, (*env)->NewStringUTF(env, apiVer));
    }
    
    if (dllVersion != NULL) {
        jobject dllBuilder = (*env)->NewLocalRef(env, dllVersion);
        (*env)->CallObjectMethod(env, dllBuilder, appendMethod, (*env)->NewStringUTF(env, dllVer));
    }
    
    if (devVersion != NULL) {
        jobject devBuilder = (*env)->NewLocalRef(env, devVersion);
        (*env)->CallObjectMethod(env, devBuilder, appendMethod, (*env)->NewStringUTF(env, devVer));
    }
    
    return STATUS_NOERROR;
}

JNIEXPORT jstring JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_getLastError(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&g_mutex);
    jstring result = (*env)->NewStringUTF(env, g_last_error);
    pthread_mutex_unlock(&g_mutex);
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_ioctl(JNIEnv *env, jobject thiz,
                                               jlong handle, jlong ioControlCode,
                                               jlong input, jlong output) {
    LOGI("Performing IOCTL on handle %ld, code: %ld", handle, ioControlCode);
    
    // Handle different IOCTL operations
    switch (ioControlCode) {
        case 0x01: // GET_CONFIG
            // In a real implementation, this would get configuration parameters
            break;
        case 0x02: // SET_CONFIG
            // In a real implementation, this would set configuration parameters
            break;
        case 0x03: // GET_VERSION
            // Already handled by readVersion
            break;
        case 0x07: // READ_VBATT
            // In a real implementation, this would read battery voltage
            break;
        default:
            setLastError("Unsupported IOCTL operation");
            return ERR_INVALID_IOCTL_ID;
    }
    
    return STATUS_NOERROR;
}

JNIEXPORT void JNICALL
Java_com_spacetec_j2534_J2534JniWrapper_cleanup(JNIEnv *env, jobject thiz) {
    LOGI("Cleaning up J2534 JNI wrapper");
    
    pthread_mutex_lock(&g_mutex);
    memset(g_devices, 0, sizeof(g_devices));
    g_device_count = 0;
    memset(g_last_error, 0, sizeof(g_last_error));
    pthread_mutex_unlock(&g_mutex);
}
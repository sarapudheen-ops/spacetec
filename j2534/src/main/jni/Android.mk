LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := j2534_jni

LOCAL_SRC_FILES := j2534_jni.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)

LOCAL_LDLIBS := -llog -landroid

LOCAL_CPPFLAGS := -std=c++11 -Wall -Wextra -O2

include $(BUILD_SHARED_LIBRARY)
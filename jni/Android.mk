# Android ndk makefile for ds4droid

LOCAL_BUILD_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_BUILD_PATH)/cpudetect/cpudetect.mk
include $(LOCAL_BUILD_PATH)/desmume_neon.mk
include $(LOCAL_BUILD_PATH)/desmume_compat.mk

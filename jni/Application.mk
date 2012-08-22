# Android ndk makefile for ds4droid

APP_STL := gnustl_static
APP_ABI := armeabi-v7a
APP_CFLAGS := -O3 -ffast-math -funroll-loops -fno-strict-aliasing -Wno-psabi
APP_PLATFORM := android-9
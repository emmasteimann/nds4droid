# Android ndk makefile for ds4droid

APP_STL := gnustl_static
APP_ABI := armeabi armeabi-v7a x86
# For releases
APP_CFLAGS := -O3 -ffast-math -funroll-loops -fomit-frame-pointer -fno-strict-aliasing -fno-math-errno -funsafe-math-optimizations -ffinite-math-only -fdata-sections -fbranch-target-load-optimize  -Wno-psabi
# For debugging
#APP_CFLAGS := -Wno-psabi
APP_PLATFORM := android-9
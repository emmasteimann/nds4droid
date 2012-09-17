# Android ndk makefile for ds4droid

LOCAL_PATH := $(call my-dir)

MY_LOCAL_PATH := $(LOCAL_PATH)

include $(CLEAR_VARS)


LOCAL_MODULE    		:= 	libdesmumev7
LOCAL_C_INCLUDES		:= 	$(LOCAL_PATH)/desmume/src \
							$(LOCAL_PATH)/desmume/src/android \
							$(LOCAL_PATH)/desmume/src/android/7z/CPP \
							$(LOCAL_PATH)/desmume/src/android/7z/CPP/include_windows \
							$(LOCAL_PATH)/desmume/src/android/agg/include
						   
LOCAL_SRC_FILES			:= 	desmume/src/aggdraw.cpp \
							desmume/src/addons.cpp \
							desmume/src/slot1.cpp \
							desmume/src/addons/slot1_none.cpp \
							desmume/src/addons/slot1_r4.cpp \
							desmume/src/addons/slot1_retail.cpp \
							desmume/src/addons/slot1_retail_nand.cpp \
							desmume/src/addons/slot2_expMemory.cpp \
							desmume/src/addons/slot2_gbagame.cpp \
							desmume/src/addons/slot2_guitarGrip.cpp \
							desmume/src/addons/slot2_mpcf.cpp \
							desmume/src/addons/slot2_none.cpp \
							desmume/src/addons/slot2_paddle.cpp \
							desmume/src/addons/slot2_piano.cpp \
							desmume/src/addons/slot2_rumblepak.cpp \
							desmume/src/MMU.cpp \
							desmume/src/NDSSystem.cpp \
							desmume/src/utils/vfat.cpp \
							desmume/src/debug.cpp \
							desmume/src/emufile.cpp \
							desmume/src/GPU.cpp \
							desmume/src/gfx3d.cpp \
							desmume/src/SPU.cpp \
							desmume/src/wifi.cpp \
							desmume/src/rtc.cpp \
							desmume/src/armcpu.cpp \
							desmume/src/mc.cpp \
							desmume/src/render3D.cpp \
							desmume/src/FIFO.cpp \
							desmume/src/readwrite.cpp \
							desmume/src/movie.cpp \
							desmume/src/common.cpp \
							desmume/src/cheatSystem.cpp \
							desmume/src/firmware.cpp \
							desmume/src/version.cpp \
							desmume/src/driver.cpp \
							desmume/src/bios.cpp \
							desmume/src/utils/decrypt/decrypt.cpp \
							desmume/src/utils/emufat.cpp \
							desmume/src/utils/libfat/libfat.cpp \
							desmume/src/utils/libfat/libfat_public_api.cpp \
							desmume/src/arm_instructions.cpp \
							desmume/src/thumb_instructions.cpp \
							desmume/src/GPU_osd.cpp \
							desmume/src/utils/dlditool.cpp \
							desmume/src/matrix.cpp \
							desmume/src/metaspu/metaspu.cpp \
							desmume/src/utils/datetime.cpp \
							desmume/src/cp15.cpp \
							desmume/src/utils/tinyxml/tinyxml.cpp \
							desmume/src/utils/tinyxml/tinyxmlparser.cpp \
							desmume/src/utils/tinyxml/tinyxmlerror.cpp \
							desmume/src/utils/tinyxml/tinystr.cpp \
							desmume/src/utils/xstring.cpp \
							desmume/src/utils/guid.cpp \
							desmume/src/utils/ConvertUTF.c \
							desmume/src/utils/decrypt/header.cpp \
							desmume/src/utils/libfat/partition.cpp \
							desmume/src/utils/libfat/fatfile.cpp \
							desmume/src/utils/libfat/cache.cpp \
							desmume/src/utils/libfat/directory.cpp \
							desmume/src/utils/libfat/fatdir.cpp \
							desmume/src/utils/libfat/lock.cpp \
							desmume/src/utils/libfat/file_allocation_table.cpp \
							desmume/src/utils/libfat/filetime.cpp \
							desmume/src/path.cpp \
							desmume/src/fs-linux.cpp \
							desmume/src/saves.cpp \
							desmume/src/ROMReader.cpp \
							desmume/src/OGLRender.cpp \
							desmume/src/texcache.cpp \
							desmume/src/rasterize.cpp \
							desmume/src/utils/task.cpp \
							desmume/src/filter/2xsai.cpp \
							desmume/src/filter/bilinear.cpp \
							desmume/src/filter/epx.cpp \
							desmume/src/filter/hq2x.cpp \
							desmume/src/filter/hq4x.cpp \
							desmume/src/filter/lq2x.cpp \
							desmume/src/filter/scanline.cpp \
							desmume/src/android/mic.cpp \
							desmume/src/android/throttle.cpp \
							desmume/src/android/main.cpp \
							desmume/src/android/OpenArchive.cpp \
							desmume/src/android/7zip.cpp \
							desmume/src/android/sndopensl.cpp
							
LOCAL_ARM_NEON 			:= true
LOCAL_ARM_MODE 			:= arm
LOCAL_CFLAGS			:= -DANDROID -DHAVE_LIBAGG -DHAVE_LIBZ -fexceptions -ftree-vectorize -fsingle-precision-constant -fprefetch-loop-arrays -fvariable-expansion-in-unroller -mfloat-abi=softfp -mfpu=vfpv3-d16 -marm -march=armv7-a
LOCAL_STATIC_LIBRARIES 	:= aggcompat sevenzip 
LOCAL_LDLIBS 			:= -llog -lz -lGLESv1_CM -lEGL -ljnigraphics -lOpenSLES -landroid 

include $(BUILD_SHARED_LIBRARY)



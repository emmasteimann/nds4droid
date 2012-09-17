/*
	Copyright (C) 2012 Jeffrey Quesnelle

	This file is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 2 of the License, or
	(at your option) any later version.

	This file is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with the this software.  If not, see <http://www.gnu.org/licenses/>.
*/

#include <jni.h>
#include <errno.h>

#include <android/native_window_jni.h>

#include <EGL/egl.h>
#include <GLES/gl.h>
#include <android/sensor.h>
#include <android/bitmap.h>


#include "main.h"
#include "../OGLRender.h"
#include "../rasterize.h"
#include "../SPU.h"
#include "../debug.h"
#include "../NDSSystem.h"
#include "../path.h"
#include "../GPU_OSD.h"
#include "../addons.h"
#include "../slot1.h"
#include "../saves.h"
#include "throttle.h"
#include "video.h"
#include "OpenArchive.h"
#include "sndopensl.h"
#include "cheatSystem.h"
#ifdef HAVE_NEON
#include "neontest.h"
#endif

#define JNI(X,...) Java_com_opendoorstudios_ds4droid_DeSmuME_##X(JNIEnv* env, jclass* clazz, __VA_ARGS__)
#define JNI_NOARGS(X) Java_com_opendoorstudios_ds4droid_DeSmuME_##X(JNIEnv* env, jclass* clazz)

unsigned int frameCount = 0;

GPU3DInterface *core3DList[] = {
	&gpu3DNull,
	&gpu3Dgl,
	&gpu3DRasterize,
	NULL
};

SoundInterface_struct *SNDCoreList[] = {
	&SNDDummy,
	&SNDOpenSL,
	NULL
};

int scanline_filter_a = 2, scanline_filter_b = 4;
volatile bool execute = false;
volatile bool paused = true;
volatile BOOL pausedByMinimize = FALSE;
bool autoframeskipenab=1;
int frameskiprate=1;
int lastskiprate=0;
int emu_paused = 0;
bool frameAdvance = false;
bool continuousframeAdvancing = false;
bool staterewindingenabled = false;
struct NDS_fw_config_data fw_config;
bool FrameLimit = true;
int sndcoretype, sndbuffersize;
AndroidBitmapInfo bitmapInfo;
EGLSurface surface;
EGLContext context;
const char* IniName = NULL;
char androidTempPath[1024];
bool useMmapForRomLoading;
extern bool enableMicrophone;

extern "C" {

void logCallback(const Logger& logger, const char* message)
{
	if(message)
		LOGI("%s", message);
}

struct MainLoopData
{
	u64 freq;
	int framestoskip;
	int framesskipped;
	int skipnextframe;
	u64 lastticks;
	u64 curticks;
	u64 diffticks;
	u64 fpsticks;
	int fps;
	int fps3d;
	int fpsframecount;
	int toolframecount;
}  mainLoopData = {0};

VideoInfo video;

/**
 * Initialize an EGL context for the current display.
 */
static bool android_opengl_init() {
	//call back into java here?
	
	const EGLint attribs[] = {
            EGL_RED_SIZE, 8,
			EGL_GREEN_SIZE, 8,
			EGL_BLUE_SIZE, 8,
			EGL_ALPHA_SIZE, 8,
			EGL_DEPTH_SIZE, 16,
			EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
			EGL_RENDERABLE_TYPE, 1, 
			EGL_NONE 
    };
    EGLint w, h, dummy, format;
    EGLint numConfigs;
    EGLConfig config;
    EGLSurface surface;
    EGLContext context;

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);

    eglInitialize(display, 0, 0);

    /* Here, the application chooses the configuration it desires. In this
     * sample, we have a very simplified selection process, where we pick
     * the first EGLConfig that matches our criteria */
    eglChooseConfig(display, attribs, &config, 1, &numConfigs);

	const EGLint surfaceAttribs[] = {
            EGL_WIDTH, 256,
			EGL_HEIGHT, 384,
			EGL_LARGEST_PBUFFER, 0,
			EGL_NONE
    };
	
    surface = eglCreatePbufferSurface(display, config, surfaceAttribs);
    context = eglCreateContext(display, config, NULL, NULL);

    if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE) {
        LOGW("Unable to eglMakeCurrent");
        return false;
    }
	
	INFO("Created OpenGL");
    return true;
}



void nds4droid_displayFrame()
{
}

static void DoDisplay_DrawHud()
{
	osd->update();
	DrawHUD();
	osd->clear();
}

void JNI_NOARGS(copyMasterBuffer)
{
	video.srcBuffer = (u8*)GPU_screen;
	
	//draw directly to the gpu screen
	aggDraw.hud->attach((u8*)video.srcBuffer, 256, 384, 512);
	DoDisplay_DrawHud();
	
	//convert pixel format to 32bpp for compositing
	//why do we do this over and over? well, we are compositing to 
	//filteredbuffer32bpp, and it needs to get refreshed each frame..
	const int size = video.size();
	u16* src = (u16*)video.srcBuffer;
	if(bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
	{
		u32* dest = video.buffer;
		for(int i=0;i<size;++i)
			*dest++ = 0xFF000000 | RGB15TO32_NOALPHA(src[i]);
	}
	else if(bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGB_565)
	{
		u16* dest = (u16*)video.buffer;
		for(int i=0;i<size;++i)
			*dest++ = RGB15TO16_REVERSE(src[i]);
	}
}

void doBitmapDraw(u8* pixels, u8* dest, int width, int height, int stride, int pixelFormat, int verticalOffset, bool rotate)
{
	if(pixelFormat == ANDROID_BITMAP_FORMAT_RGBA_8888)
	{
		u32* src = (u32*)pixels;
		src += (verticalOffset * (rotate ? height : width));
		if(video.currentfilter == VideoInfo::NONE)
		{
			if(rotate)
			{
				for(int y = 0 ; y < height ; ++y)
				{
					u32* destline = (u32*)dest;
					u32* srccol = src + (height - y - 1);
					for(int x = 0 ; x < width ; ++x) 
					{
						*destline++ = *srccol;
						srccol += height;
					}
					dest += stride;
				}
			}
			else
			{
				if(stride == width * sizeof(u32)) //bitmap is the same size, we can do one massive memcpy
					memcpy(dest, src, width * height * sizeof(u32));
				else
				{
					for(int y = 0 ; y < height ; ++y)
					{
						memcpy(dest, &src[y * width], width * sizeof(u32));
						dest += stride;
					}
				}
			}
		}
		else
		{
			//the alpha channels are screwy because of interpolation
			//we need to go pixel by pixel and clear them
			if(rotate)
			{
				for(int y = 0 ; y < height ; ++y)
				{
					u32* destline = (u32*)dest;
					u32* srccol = src + (height - y - 1);
					for(int x = 0 ; x < width ; ++x) 
					{
						*destline++ = 0xFF000000 | *srccol;
						srccol += height;
					}
					dest += stride;
				}
			}
			else
			{
				for(int y = 0 ; y < height ; ++y)
				{
					u32* destline = (u32*)dest;
					for(int x = 0 ; x < width ; ++x)
						*destline++ = 0xFF000000 | *src++;
					dest += stride;
				}
			}
		}
	}
	else
	{
		u16* src = (u16*)pixels;
		src += (verticalOffset * (rotate ? height : width));
		if(rotate)
		{
			for(int y = 0 ; y < height ; ++y)
			{
				u16* destline = (u16*)dest;
				u16* srccol = src + (height - y - 1);
				for(int x = 0 ; x < width ; ++x) 
				{
					*destline++ = *srccol;
					srccol += height;
				}
				dest += stride;
			}
		}
		else
		{
			if(stride == width * sizeof(u16)) //bitmap is the same size, we can do one massive memcpy
				memcpy(dest, src, width * height * sizeof(u16));
			else
			{
				for(int y = 0 ; y < height ; ++y)
				{
					memcpy(dest, &src[y * width], width * sizeof(u16));
					dest += stride;
				}
			}
		}
	}
}

void JNI(draw, jobject bitmapMain, jobject bitmapTouch, jboolean rotate)
{
	if(bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
		video.filter();
	//here the magic happens
	void* pixels = NULL;
	if(AndroidBitmap_lockPixels(env,bitmapMain,&pixels) >= 0)
	{
		doBitmapDraw((u8*)video.finalBuffer(), (u8*)pixels, bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride, bitmapInfo.format, 0, rotate == JNI_TRUE);
		AndroidBitmap_unlockPixels(env, bitmapMain);
	}
	if(AndroidBitmap_lockPixels(env,bitmapTouch,&pixels) >= 0)
	{
		doBitmapDraw((u8*)video.finalBuffer(), (u8*)pixels, bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride, bitmapInfo.format, video.height / 2, rotate == JNI_TRUE);
		AndroidBitmap_unlockPixels(env, bitmapTouch);
	}
}

void JNI(drawToSurface, jobject surface)
{
	ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
	if(!window)
		return;
	
	ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, NULL) >= 0) {
		
		int stride, format;
		if(buffer.format == WINDOW_FORMAT_RGB_565)
		{
			stride = buffer.stride * sizeof(u16);
			format = ANDROID_BITMAP_FORMAT_RGB_565;
		}
		else
		{
			stride = buffer.stride * sizeof(u32);
			format = ANDROID_BITMAP_FORMAT_RGBA_8888;
		}
		
		doBitmapDraw((u8*)video.finalBuffer(), (u8*)buffer.bits, video.width, video.height, stride, format, 0, false);
		
		ANativeWindow_unlockAndPost(window);
    }
	
	ANativeWindow_release(window);
}

bool NDS_Pause(bool showMsg = true)
{
	if(paused) return false;

	emu_halt();
	paused = TRUE;
	SPU_Pause(1);
	while (!paused) {}
	if (showMsg) INFO("Emulation paused\n");

	return true;
}

void NDS_UnPause(bool showMsg = true)
{
	if (/*romloaded &&*/ paused)
	{
		paused = FALSE;
		pausedByMinimize = FALSE;
		execute = TRUE;
		SPU_Pause(0);
		if (showMsg) INFO("Emulation unpaused\n");

	}
}

static void nds4droid_throttle(bool allowSleep = true, int forceFrameSkip = -1)
{
	int skipRate = (forceFrameSkip < 0) ? frameskiprate : forceFrameSkip;
	int ffSkipRate = (forceFrameSkip < 0) ? 9 : forceFrameSkip;

	if(lastskiprate != skipRate)
	{
		lastskiprate = skipRate;
		mainLoopData.framestoskip = 0; // otherwise switches to lower frameskip rates will lag behind
	}

	if(!mainLoopData.skipnextframe || forceFrameSkip == 0 || frameAdvance || (continuousframeAdvancing && !FastForward))
	{
		mainLoopData.framesskipped = 0;

		if (mainLoopData.framestoskip > 0)
			mainLoopData.skipnextframe = 1;
	}
	else
	{
		mainLoopData.framestoskip--;

		if (mainLoopData.framestoskip < 1)
			mainLoopData.skipnextframe = 0;
		else
			mainLoopData.skipnextframe = 1;

		mainLoopData.framesskipped++;

		NDS_SkipNextFrame();
	}

	if(FastForward)
	{
		if(mainLoopData.framesskipped < ffSkipRate)
		{
			mainLoopData.skipnextframe = 1;
			mainLoopData.framestoskip = 1;
		}
		if (mainLoopData.framestoskip < 1)
			mainLoopData.framestoskip += ffSkipRate;
	}
	else if((/*autoframeskipenab && frameskiprate ||*/ FrameLimit) && allowSleep)
	{
		SpeedThrottle();
	}

	if (autoframeskipenab && frameskiprate)
	{
		if(!frameAdvance && !continuousframeAdvancing)
		{
			AutoFrameSkip_NextFrame();
			if (mainLoopData.framestoskip < 1)
				mainLoopData.framestoskip += AutoFrameSkip_GetSkipAmount(0,skipRate);
		}
	}
	else
	{
		if (mainLoopData.framestoskip < 1)
			mainLoopData.framestoskip += skipRate;
	}

	if (frameAdvance && allowSleep)
	{
		frameAdvance = false;
		emu_halt();
		SPU_Pause(1);
	}
	if(execute && emu_paused && !frameAdvance)
	{
		// safety net against running out of control in case this ever happens.
		NDS_UnPause(); NDS_Pause();
	}

	//ServiceDisplayThreadInvocations();
}

void nds4droid_user()
{
	const int kFramesPerToolUpdate = 1;

	Hud.fps = mainLoopData.fps;
	Hud.fps3d = mainLoopData.fps3d;

	//nds4droid_display();

	gfx3d.frameCtrRaw++;
	if(gfx3d.frameCtrRaw == 60) {
		mainLoopData.fps3d = gfx3d.frameCtr;
		gfx3d.frameCtrRaw = 0;
		gfx3d.frameCtr = 0;
	}

	mainLoopData.toolframecount++;

	//Update_RAM_Search(); // Update_RAM_Watch() is also called.

	mainLoopData.fpsframecount++;
	mainLoopData.curticks = GetTickCount();
	bool oneSecond = mainLoopData.curticks >= mainLoopData.fpsticks + mainLoopData.freq;
	if(oneSecond) // TODO: print fps on screen in DDraw
	{
		mainLoopData.fps = mainLoopData.fpsframecount;
		mainLoopData.fpsframecount = 0;
		mainLoopData.fpsticks = GetTickCount();
	}

	if(nds.idleFrameCounter==0 || oneSecond) 
	{
		//calculate a 16 frame arm9 load average
		for(int cpu=0;cpu<2;cpu++)
		{
			int load = 0;
			//printf("%d: ",cpu);
			for(int i=0;i<16;i++)
			{
				//blend together a few frames to keep low-framerate games from having a jittering load average
				//(they will tend to work 100% for a frame and then sleep for a while)
				//4 frames should handle even the slowest of games
				s32 sample = 
					nds.runCycleCollector[cpu][(i+0+nds.idleFrameCounter)&15]
				+	nds.runCycleCollector[cpu][(i+1+nds.idleFrameCounter)&15]
				+	nds.runCycleCollector[cpu][(i+2+nds.idleFrameCounter)&15]
				+	nds.runCycleCollector[cpu][(i+3+nds.idleFrameCounter)&15];
				sample /= 4;
				load = load/8 + sample*7/8;
			}
			//printf("\n");
			load = std::min(100,std::max(0,(int)(load*100/1120380)));
			Hud.cpuload[cpu] = load;
		}
	}

	Hud.cpuloopIterationCount = nds.cpuloopIterationCount;
}

void nds4droid_core()
{
	NDS_beginProcessingInput();
	NDS_endProcessingInput();
	NDS_exec<false>();
}



void nds4droid_unpause()
{
	if(!execute) NDS_Pause(false);
	if (emu_paused && autoframeskipenab && frameskiprate) AutoFrameSkip_IgnorePreviousDelay();
	NDS_UnPause();
}

bool doRomLoad(const char* path, const char* logical)
{
	if(NDS_LoadROM(path, logical) >= 0)
	{
		INFO("Loading %s was successful\n",path);
		nds4droid_unpause();
		if (autoframeskipenab && frameskiprate) AutoFrameSkip_IgnorePreviousDelay();
		return true;
	}
	return false;
}

bool nds4droid_loadrom(const char* path)
{
	char LogicalName[1024], PhysicalName[1024];

	const char* s_nonRomExtensions [] = {"txt", "nfo", "htm", "html", "jpg", "jpeg", "png", "bmp", "gif", "mp3", "wav", "lnk", "exe", "bat", "gmv", "gm2", "lua", "luasav", "sav", "srm", "brm", "cfg", "wch", "gs*", "dst"};

	if(!ObtainFile(path, LogicalName, PhysicalName, "rom", s_nonRomExtensions, ARRAY_SIZE(s_nonRomExtensions)))
		return false;
		
	return doRomLoad(path, PhysicalName);
}

void JNI(resize, jobject bitmap)
{
	AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
}

int JNI_NOARGS(getNativeWidth)
{
	return video.width;
}

int JNI_NOARGS(getNativeHeight)
{
	return video.height;
}

void JNI(setFilter, int index)
{
	video.setfilter(index);
}


void JNI_NOARGS(runCore)
{
	int frameStart = GetTickCount();
	nds4droid_core();
	int frameEnd = GetTickCount();
	/*if(frameCount ++ % 5 == 0)
	{
		LOGI("Core frame time %d ms", frameEnd - frameStart);
	}*/
}

void JNI(setSoundPaused, int set)
{
	if(sndcoretype != 1)
		return;
	SNDOpenSLPaused(set == 0 ? false : true);
}

int JNI_NOARGS(runOther)
{
	if(execute)
	{
		if(sndcoretype != 0)
			SPU_Emulate_user();
		nds4droid_user();
		nds4droid_throttle();
		return mainLoopData.fps > 0 ? mainLoopData.fps : 1;
	}
	return 1;
}

void JNI(saveState, int slot)
{
	savestate_slot(slot);
}

void JNI(restoreState, int slot)
{
	loadstate_slot(slot);
}

void loadSettings(JNIEnv* env)
{
	CommonSettings.num_cores = sysconf( _SC_NPROCESSORS_ONLN );
	LOGI("%i cores detected", CommonSettings.num_cores); 
	CommonSettings.advanced_timing = false;
	CommonSettings.cheatsDisable = GetPrivateProfileBool(env,"General", "cheatsDisable", false, IniName);
	CommonSettings.autodetectBackupMethod = GetPrivateProfileInt(env,"General", "autoDetectMethod", 0, IniName);
	video.rotation =  GetPrivateProfileInt(env,"Video","WindowRotate", 0, IniName);
	video.rotation_userset =  GetPrivateProfileInt(env,"Video","WindowRotateSet", video.rotation, IniName);
	video.layout_old = video.layout = GetPrivateProfileInt(env,"Video", "LCDsLayout", 0, IniName);
	video.swap = GetPrivateProfileInt(env,"Video", "LCDsSwap", 0, IniName);
	CommonSettings.hud.FpsDisplay = GetPrivateProfileBool(env,"Display","DisplayFps", false, IniName);
	CommonSettings.hud.FrameCounterDisplay = GetPrivateProfileBool(env,"Display","FrameCounter", false, IniName);
	CommonSettings.hud.ShowInputDisplay = GetPrivateProfileBool(env,"Display","DisplayInput", false, IniName);
	CommonSettings.hud.ShowGraphicalInputDisplay = GetPrivateProfileBool(env,"Display","DisplayGraphicalInput", false, IniName);
	CommonSettings.hud.ShowLagFrameCounter = GetPrivateProfileBool(env,"Display","DisplayLagCounter", false, IniName);
	CommonSettings.hud.ShowMicrophone = GetPrivateProfileBool(env,"Display","DisplayMicrophone", false, IniName);
	CommonSettings.hud.ShowRTC = GetPrivateProfileBool(env,"Display","DisplayRTC", false, IniName);
	CommonSettings.micMode = (TCommonSettings::MicMode)GetPrivateProfileInt(env,"MicSettings", "MicMode", (int)TCommonSettings::InternalNoise, IniName);
	video.screengap = GetPrivateProfileInt(env,"Display", "ScreenGap", 0, IniName);
	CommonSettings.showGpu.main = GetPrivateProfileInt(env,"Display", "MainGpu", 1, IniName) != 0;
	CommonSettings.showGpu.sub = GetPrivateProfileInt(env,"Display", "SubGpu", 1, IniName) != 0;
	CommonSettings.spu_advanced = GetPrivateProfileBool(env,"Sound", "SpuAdvanced", false, IniName);
	CommonSettings.advanced_timing = GetPrivateProfileBool(env,"Emulation", "AdvancedTiming", false, IniName);
	CommonSettings.GFX3D_Zelda_Shadow_Depth_Hack = GetPrivateProfileInt(env,"3D", "ZeldaShadowDepthHack", 0, IniName);
	CommonSettings.wifi.mode = GetPrivateProfileInt(env,"Wifi", "Mode", 0, IniName);
	CommonSettings.wifi.infraBridgeAdapter = GetPrivateProfileInt(env,"Wifi", "BridgeAdapter", 0, IniName);
	frameskiprate = GetPrivateProfileInt(env,"Display", "FrameSkip", 1, IniName);
	CommonSettings.spuInterpolationMode = (SPUInterpolationMode)GetPrivateProfileInt(env, "Sound","SPUInterpolation", 1, IniName);
	CommonSettings.GFX3D_HighResolutionInterpolateColor = GetPrivateProfileBool(env, "3D", "HighResolutionInterpolateColor", 0, IniName);
	CommonSettings.GFX3D_EdgeMark = GetPrivateProfileBool(env, "3D", "EnableEdgeMark", 0, IniName);
	CommonSettings.GFX3D_Fog = GetPrivateProfileBool(env, "3D", "EnableFog", 1, IniName);
	CommonSettings.GFX3D_Texture = GetPrivateProfileBool(env, "3D", "EnableTexture", 1, IniName);
	CommonSettings.GFX3D_LineHack = GetPrivateProfileBool(env, "3D", "EnableLineHack", 0, IniName);
	useMmapForRomLoading = GetPrivateProfileBool(env, "General", "UseMmap", true, IniName);
	fw_config.language = GetPrivateProfileInt(env, "Firmware","Language", 1, IniName);
	enableMicrophone = GetPrivateProfileBool(env, "General", "EnableMicrophone", true, IniName);
}

void JNI_NOARGS(reloadFirmware)
{
	NDS_CreateDummyFirmware(&fw_config);
}

void JNI_NOARGS(loadSettings)
{
	loadSettings(env);
}


#ifdef HAVE_NEON
void enable_runfast()
{
	static const unsigned int x = 0x04086060;
	static const unsigned int y = 0x03000000;
	int r;
	asm volatile (
		"fmrx	%0, fpscr			\n\t"	//r0 = FPSCR
		"and	%0, %0, %1			\n\t"	//r0 = r0 & 0x04086060
		"orr	%0, %0, %2			\n\t"	//r0 = r0 | 0x03000000
		"fmxr	fpscr, %0			\n\t"	//FPSCR = r0
		: "=r"(r)
		: "r"(x), "r"(y)
	);
}
#endif

void JNI(init, jobject _inst)
{
#ifdef HAVE_NEON
	//neontest();
	enable_runfast();
#endif
	INFO("");
	for(std::vector<Logger*>::iterator it = Logger::channels.begin() ; it != Logger::channels.end() ; ++it)
		(*it)->setCallback(logCallback);

	extern bool windows_opengl_init();
	oglrender_init = android_opengl_init;
	InitDecoder();
	
	path.ReadPathSettings();
	if (video.layout > 2)
	{
		video.layout = video.layout_old = 0;
	}
	
	loadSettings(env);

	Desmume_InitOnce();
	//gpu_SetRotateScreen(video.rotation);
	NDS_FillDefaultFirmwareConfigData(&fw_config);
	Hud.reset();
	
	INFO("Init NDS");
	
	switch (slot1_device_type)
	{
		case NDS_SLOT1_NONE:
		case NDS_SLOT1_RETAIL:
		case NDS_SLOT1_R4:
		case NDS_SLOT1_RETAIL_NAND:
			break;
		default:
			slot1_device_type = NDS_SLOT1_RETAIL;
			break;
	}
	
	switch (addon_type)
	{
	case NDS_ADDON_NONE:
		break;
	case NDS_ADDON_CFLASH:
		break;
	case NDS_ADDON_RUMBLEPAK:
		break;
	case NDS_ADDON_GBAGAME:
		if (!strlen(GBAgameName))
		{
			addon_type = NDS_ADDON_NONE;
			break;
		}
		// TODO: check for file exist
		break;
	case NDS_ADDON_GUITARGRIP:
		break;
	case NDS_ADDON_EXPMEMORY:
		break;
	case NDS_ADDON_PIANO:
		break;
	case NDS_ADDON_PADDLE:
		break;
	default:
		addon_type = NDS_ADDON_NONE;
		break;
	}

	slot1Change((NDS_SLOT1_TYPE)slot1_device_type);
	addonsChangePak(addon_type);

	
	NDS_Init();
	
	osd->singleScreen = true;
	cur3DCore = GetPrivateProfileInt(env, "3D", "Renderer", 1, IniName);
	NDS_3D_ChangeCore(cur3DCore); //OpenGL
	
	LOG("Init sound core\n");
	sndcoretype = GetPrivateProfileInt(env, "Sound","SoundCore2", SNDCORE_OPENSL, IniName);
	sndbuffersize = GetPrivateProfileInt(env, "Sound","SoundBufferSize2", DESMUME_SAMPLE_RATE*8/60, IniName);
	SPU_ChangeSoundCore(sndcoretype, sndbuffersize);
	
	static const char* nickname = "emozilla";
	fw_config.nickname_len = strlen(nickname);
	for(int i = 0 ; i < fw_config.nickname_len ; ++i)
		fw_config.nickname[i] = nickname[i];
		
	static const char* message = "ds4droid makes you happy!";
	fw_config.message_len = strlen(message);
	for(int i = 0 ; i < fw_config.message_len ; ++i)
		fw_config.message[i] = message[i];
	
	fw_config.language = GetPrivateProfileInt(env, "Firmware","Language", 1, IniName);
		
	video.setfilter(GetPrivateProfileInt(env,"Video", "Filter", video.NONE, IniName));
	
	NDS_CreateDummyFirmware(&fw_config);
	
	InitSpeedThrottle();
	
	mainLoopData.freq = 1000;
	mainLoopData.lastticks = GetTickCount();
}

void JNI(change3D, int type)
{
	NDS_3D_ChangeCore(cur3DCore = type);
}

void JNI(changeSound, int type)
{
	SPU_ChangeSoundCore(sndcoretype = type, sndbuffersize);
}

jboolean JNI(loadRom, jstring path)
{
	jboolean isCopy; 
	const char* szPath = env->GetStringUTFChars(path, &isCopy);
	bool ret = nds4droid_loadrom(szPath);
	env->ReleaseStringUTFChars(path, szPath);
	return ret ? JNI_TRUE : JNI_FALSE;
}

void JNI(setWorkingDir, jstring path, jstring temp)
{
	jboolean isCopy; 
	const char* szPath = env->GetStringUTFChars(path, &isCopy);
	strncpy(PathInfo::pathToModule, szPath, MAX_PATH);
	env->ReleaseStringUTFChars(path, szPath);
	
	szPath = env->GetStringUTFChars(temp, &isCopy);
	strncpy(androidTempPath, szPath, 1024);
	env->ReleaseStringUTFChars(temp, szPath);
}

void JNI(touchScreenTouch, int x, int y)
{
	if(x<0) x = 0; else if(x>255) x = 255;
	if(y<0) y = 0; else if(y>192) y = 192;
	NDS_setTouchPos(x,y);
}

void JNI_NOARGS(touchScreenRelease)
{
	NDS_releaseTouch();
}

void JNI(setButtons, int l, int r, int up, int down, int left, int right, int a, int b, int x, int y, int start, int select)
{
	NDS_setPad(right, left, down, up, select, start, b, a, y, x, l, r, false, false);
}

jint JNI_NOARGS(getNumberOfCheats)
{
	return cheats == NULL ? 0 : cheats->getSize();
}

jstring JNI(getCheatName, int pos)
{
	if(cheats == NULL || pos < 0 || pos >= cheats->getSize())
		return 0;
	return env->NewStringUTF(cheats->getItemByIndex(pos)->description);
}

jboolean JNI(getCheatEnabled, int pos)
{
	if(cheats == NULL || pos < 0 || pos >= cheats->getSize())
		return 0;
	return cheats->getItemByIndex(pos)->enabled ? JNI_TRUE : JNI_FALSE;
}

jstring JNI(getCheatCode, int pos)
{
	if(cheats == NULL || pos < 0 || pos >= cheats->getSize())
		return 0;
	char buffer[1024] = {0};
	cheats->getXXcodeString(*cheats->getItemByIndex(pos), buffer);
	jstring ret = env->NewStringUTF(buffer);
	return ret;
}

jint JNI(getCheatType, int pos)
{
	if(cheats == NULL || pos < 0 || pos >= cheats->getSize())
		return 0;
	return cheats->getItemByIndex(pos)->type;
}

void JNI(addCheat, jstring description, jstring code)
{
	if(cheats == NULL)
		return;
	jboolean isCopy;
	const char* descBuff = env->GetStringUTFChars(description, &isCopy);
	const char* codeBuff = env->GetStringUTFChars(code, &isCopy);
	cheats->add_AR(codeBuff, descBuff, TRUE);
	env->ReleaseStringUTFChars(description, descBuff);
	env->ReleaseStringUTFChars(code, codeBuff);
}

void JNI(updateCheat, jstring description, jstring code, jint pos)
{
	if(cheats == NULL)
		return;
	jboolean isCopy;
	const char* descBuff = env->GetStringUTFChars(description, &isCopy);
	const char* codeBuff = env->GetStringUTFChars(code, &isCopy);
	cheats->update_AR(codeBuff, descBuff, TRUE, pos);
	env->ReleaseStringUTFChars(description, descBuff);
	env->ReleaseStringUTFChars(code, codeBuff);
}

void JNI_NOARGS(saveCheats)
{
	if(cheats)
		cheats->save();
}

void JNI(setCheatEnabled, int pos, jboolean enabled)
{
	if(cheats)
		cheats->getItemByIndex(pos)->enabled = enabled == JNI_TRUE ? true : false;
}

void JNI(deleteCheat, jint pos)
{
	if(cheats)
		cheats->remove(pos);
}

void JNI_NOARGS(closeRom)
{
	NDS_FreeROM();
	execute = false;
	Hud.resetTransient();
	NDS_Reset();
}

} //end extern "C"

unsigned int GetPrivateProfileInt(JNIEnv* env, const char* lpAppName, const char* lpKeyName, int nDefault, const char* lpFileName)
{
	jclass javaClass = env->FindClass("com/opendoorstudios/ds4droid/DeSmuME");
	if(!javaClass)
		return nDefault;
	jmethodID getSettingInt = env->GetStaticMethodID(javaClass, "getSettingInt","(Ljava/lang/String;I)I");
	jstring key = env->NewStringUTF(lpKeyName);
	int ret = env->CallStaticIntMethod(javaClass, getSettingInt, key, nDefault);
	return ret;
}

bool GetPrivateProfileBool(JNIEnv* env, const char* lpAppName, const char* lpKeyName, bool bDefault, const char* lpFileName)
{
	return GetPrivateProfileInt(env, lpAppName, lpKeyName, bDefault ? 1 : 0, lpFileName);
}
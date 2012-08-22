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

#include <cpu-features.h>
#include <jni.h>

#define JNI_NOARGS(X) Java_com_opendoorstudios_ds4droid_DeSmuME_##X(JNIEnv* env, jclass* clazz)

extern "C"
{

jboolean JNI_NOARGS(useNeon)
{
	if (android_getCpuFamily() == ANDROID_CPU_FAMILY_ARM &&
        (android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON) != 0)
    {
		return JNI_TRUE;
    }
    else
    {
        return JNI_FALSE;
    }
}

}
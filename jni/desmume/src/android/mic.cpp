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

/*
	The NDS microphone produces 8-bit sound sampled at 16khz.
	The sound data must be read sample-by-sample through the 
	ARM7 SPI device (touchscreen controller, channel 6).

	Note : I added these notes because the microphone isn't 
	documented on GBATek.
*/

#include "../mic.h"
#include "readwrite.h"

void Mic_DeInit()
{
}

BOOL Mic_Init()
{
	return FALSE;
}

void Mic_Reset()
{
}

u8 Mic_ReadSample()
{
	return 0;
}

void mic_savestate(EMUFILE* os)
{
	write32le(-1,os);
}

bool mic_loadstate(EMUFILE* is, int size)
{
	is->fseek(size, SEEK_CUR);
	return TRUE;
}
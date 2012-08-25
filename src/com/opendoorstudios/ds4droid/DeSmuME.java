package com.opendoorstudios.ds4droid;

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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.Log;

class DeSmuME {
	
	public static Context context;
	
	static boolean loaded = false;
	
	static void load()
	{
		if(loaded)
			return;
		System.loadLibrary("cpudetect");
		if(useNeon()) {
			System.loadLibrary("desmumeneon");
			Log.i(MainActivity.TAG, "Using NEON enhanced native library");
		}
		else {
			System.loadLibrary("desmumecompat");
			Log.i(MainActivity.TAG, "Using compatibility native library");
		}
	}
	
	static native boolean useNeon();
	static native void init();
	static native int runCore();
	static native void runOther();
	static native void resize(Bitmap bitmap, int width, int height);
	static native void draw(Bitmap bitmap);
	static native void touchScreenTouch(int x, int y);
	static native void touchScreenRelease();
	static native void setButtons(int l, int r, int up, int down, int left, int right, int a, int b, int x, int y, int start, int select);
	static native boolean loadRom(String path);
	static native void setWorkingDir(String path, String temp);
	static native void saveState(int slot);
	static native void restoreState(int slot);
	static native void loadSettings();
	
	public static int getSettingInt(String name, int def)
	{
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(context);
		if(!pm.contains(name))
			return def;
		try {
			return pm.getInt(name, def);
		}
		catch(ClassCastException e) {
		}
		try {
			String ret = pm.getString(name, String.valueOf(def));
			return Integer.valueOf(ret);
		}
		catch(ClassCastException e) {
		}
		try {
			Boolean ret = pm.getBoolean(name, def == 0 ? false : true);
			return ret.booleanValue() ? 1 : 0;
		}
		catch(ClassCastException e) {
		}
		return def;
	}

}

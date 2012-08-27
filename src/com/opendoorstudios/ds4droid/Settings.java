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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Settings extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
	}
	
	public static final String SHOW_TOUCH_MESSAGE = "ShowTouchMessage";
	public static final String DESMUME_PATH = "DeSmuMEPath";
	public static final String VSYNC = "VSync";
	public static final String SHOW_FPS = "DisplayFps";
	public static final String FRAME_SKIP = "FrameSkip";
	public static final String SCREEN_FILTER = "Filter";
	public static final String RENDERER = "Renderer";
	public static final String ENABLE_SOUND = "SoundCore2";
	public static final String SHOW_SOUND_MESSAGE = "ShowSoundMessage";
	public static final String INSTALLED_RELEASE = "InstalledRelease";
	
	public static void applyDefaults(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		if(!prefs.contains(SHOW_TOUCH_MESSAGE))
			editor.putBoolean(SHOW_TOUCH_MESSAGE, true);
		if(!prefs.contains(VSYNC))
			editor.putBoolean(VSYNC, true);
		if(!prefs.contains(SHOW_FPS))
			editor.putBoolean(SHOW_FPS, false);
		if(!prefs.contains(FRAME_SKIP))
			editor.putString(FRAME_SKIP, "1");
		if(!prefs.contains(SCREEN_FILTER))
			editor.putString(SCREEN_FILTER, "0");
		if(!prefs.contains(RENDERER))
			editor.putString(RENDERER, "2");
		if(!prefs.contains(ENABLE_SOUND))
			editor.putBoolean(ENABLE_SOUND, false);
		if(!prefs.contains(SHOW_SOUND_MESSAGE))
			editor.putBoolean(SHOW_SOUND_MESSAGE, true);
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			editor.putInt(INSTALLED_RELEASE, info.versionCode);
		} catch (NameNotFoundException e) {
		}
		editor.apply();
	}
	
}

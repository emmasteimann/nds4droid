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

import java.util.Locale;
import java.util.Map.Entry;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

public class Settings extends PreferenceActivity {

	
	static final int EDIT_LAYOUT_ID = 1337;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
		
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		findPreference(EDIT_LAYOUT).setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference pref) {
				startActivityForResult(new Intent(Settings.this, ButtonLayoutEditor.class), EDIT_LAYOUT_ID);
				return true;
			}
			
		});
		
		findPreference(RESET_LAYOUT).setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				applyLayoutDefaults(prefs, true);
				return true;
			}
			
		});
		
		findPreference(EDIT_MAPPINGS).setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(Settings.this, KeyMapSettings.class));
				return true;
			}
			
		});
		
	}
	
	static void applyLayoutDefaults(SharedPreferences prefs, boolean overwrite) {
		SharedPreferences.Editor edit = prefs.edit();
		for(Entry<Integer, Button> button : Button.portraitToDefault.entrySet()) {
			final String prefBase = "Controls.Portrait." + Button.getButtonName(button.getValue().id);
			if(!overwrite && prefs.contains(prefBase + ".Left"))
				continue;
			edit.remove(prefBase + ".Left");
			edit.remove(prefBase + ".Right");
			edit.remove(prefBase + ".Top");
			edit.remove(prefBase + ".Bottom");
		}
		if(overwrite || !prefs.contains("Controls.Portrait.Draw"))
				edit.putBoolean("Controls.Portrait.Draw", true);
		for(Entry<Integer, Button> button : Button.landscapeToDefault.entrySet()) {
			final String prefBase = "Controls.Landscape." + Button.getButtonName(button.getValue().id);
			if(!overwrite && prefs.contains(prefBase + ".Left"))
				continue;
			edit.remove(prefBase + ".Left");
			edit.remove(prefBase + ".Right");
			edit.remove(prefBase + ".Top");
			edit.remove(prefBase + ".Bottom");
		}
		if(overwrite || !prefs.contains("Controls.Landscape.Draw"))
			edit.putBoolean("Controls.Landscape.Draw", true);
		if(overwrite || !prefs.contains(BUTTON_TRANSPARENCY))
			edit.putInt(BUTTON_TRANSPARENCY, 78);
		if(overwrite || !prefs.contains(HAPTIC))
			edit.putBoolean(HAPTIC, false);
		if(overwrite || !prefs.contains(ALWAYS_TOUCH))
			edit.putBoolean(ALWAYS_TOUCH, false);
		edit.apply();
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
	public static final String EDIT_LAYOUT = "Controls.EditLayout";
	public static final String RESET_LAYOUT = "Controls.ResetLayout";
	public static final String LCD_SWAP = "LCDsSwap";
	public static final String BUTTON_TRANSPARENCY = "Controls.Transparency";
	public static final String HAPTIC = "Controls.Haptic";
	public static final String EDIT_MAPPINGS = "Controls.EditMappings";
	public static final String MAPPING_UP = "Controls.KeyMap.Up";
	public static final String MAPPING_DOWN = "Controls.KeyMap.Down";
	public static final String MAPPING_LEFT = "Controls.KeyMap.Left";
	public static final String MAPPING_RIGHT = "Controls.KeyMap.Right";
	public static final String MAPPING_A = "Controls.KeyMap.A";
	public static final String MAPPING_B = "Controls.KeyMap.B";
	public static final String MAPPING_X = "Controls.KeyMap.X";
	public static final String MAPPING_Y = "Controls.KeyMap.Y";
	public static final String MAPPING_START = "Controls.KeyMap.Start";
	public static final String MAPPING_SELECT = "Controls.KeyMap.Select";
	public static final String MAPPING_L = "Controls.KeyMap.L";
	public static final String MAPPING_R = "Controls.KeyMap.R";
	public static final String MAPPING_TOUCH = "Controls.KeyMap.Touch";
	public static final String DONT_ROTATE_LCDS = "WindowRotate";
	public static final String LANGUAGE = "Language";
	public static final String ENABLE_MICROPHONE = "EnableMicrophone";
	public static final String ALWAYS_TOUCH = "Controls.AlwaysTouch";
	
	static void applyMappingDefaults(SharedPreferences prefs, boolean overwrite) {
		final SharedPreferences.Editor editor = prefs.edit();
		if(overwrite || !prefs.contains(MAPPING_UP))
			editor.putInt(MAPPING_UP, KeyEvent.KEYCODE_DPAD_UP);
		if(overwrite || !prefs.contains(MAPPING_DOWN))
			editor.putInt(MAPPING_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
		if(overwrite || !prefs.contains(MAPPING_LEFT))
			editor.putInt(MAPPING_LEFT, KeyEvent.KEYCODE_DPAD_LEFT);
		if(overwrite || !prefs.contains(MAPPING_RIGHT))
			editor.putInt(MAPPING_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT);
		if(overwrite || !prefs.contains(MAPPING_A))
			editor.putInt(MAPPING_A, KeyEvent.KEYCODE_X);
		if(overwrite || !prefs.contains(MAPPING_B))
			editor.putInt(MAPPING_B, KeyEvent.KEYCODE_Z);
		if(overwrite || !prefs.contains(MAPPING_X))
			editor.putInt(MAPPING_X, KeyEvent.KEYCODE_S);
		if(overwrite || !prefs.contains(MAPPING_Y))
			editor.putInt(MAPPING_Y, KeyEvent.KEYCODE_A);
		if(overwrite || !prefs.contains(MAPPING_START))
			editor.putInt(MAPPING_START, KeyEvent.KEYCODE_V);
		if(overwrite || !prefs.contains(MAPPING_SELECT))
			editor.putInt(MAPPING_SELECT, KeyEvent.KEYCODE_B);
		if(overwrite || !prefs.contains(MAPPING_L))
			editor.putInt(MAPPING_L, KeyEvent.KEYCODE_Q);
		if(overwrite || !prefs.contains(MAPPING_R))
			editor.putInt(MAPPING_R, KeyEvent.KEYCODE_W);
		if(overwrite || !prefs.contains(MAPPING_TOUCH))
			editor.putInt(MAPPING_TOUCH, KeyEvent.KEYCODE_T);
		editor.apply();
	}
	
	public static void applyDefaults(Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
		if(!prefs.contains(LCD_SWAP))
			editor.putBoolean(LCD_SWAP, false);
		if(!prefs.contains(DONT_ROTATE_LCDS))
			editor.putBoolean(DONT_ROTATE_LCDS, false);
		if(!prefs.contains(ENABLE_MICROPHONE))
			editor.putBoolean(ENABLE_MICROPHONE, true);
		if(!prefs.contains(LANGUAGE)) {
			final String userLanguage = Locale.getDefault().getISO3Language();
			int lang = 1; //english
			if(userLanguage.equals("jpn")) 
				lang = 0;			
			else if(userLanguage.equals("fra")) 
				lang = 2;
			else if(userLanguage.equals("deu"))
				lang = 3;
			else if(userLanguage.equals("ita"))
				lang = 4;
			else if(userLanguage.equals("spa"))
				lang = 5;
			editor.putString(LANGUAGE, String.valueOf(lang));
		}
		applyLayoutDefaults(prefs, false);
		applyMappingDefaults(prefs, false);
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			editor.putInt(INSTALLED_RELEASE, info.versionCode);
		} catch (NameNotFoundException e) {
		}
		editor.apply();
	}
	
}

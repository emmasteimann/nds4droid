package com.opendoorstudios.ds4droid;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class KeyMapSettings extends PreferenceActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.keymap);
	}

}

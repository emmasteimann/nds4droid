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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

	static EmulatorThread coreThread;
	static Controls controls;
	NDSView view;
	static final String TAG = "nds4droid";
	Dialog loadingDialog = null;
	
	Handler msgHandler = new Handler() {
		
		@Override
		public
		void dispatchMessage(Message msg) {
			switch(msg.what) {
			case DRAW_SCREEN:		
				//view.invalidate();
				if(view.drawingThread != null ) {
					view.drawingThread.drawEventLock.lock();
					view.drawingThread.drawEvent.signal();
					view.drawingThread.drawEventLock.unlock();
				}
				break;
			case PICK_ROM:
				pickRom();
				break;
			case LOADING_START:
				if(loadingDialog == null) {
					final String loadingMsg = getResources().getString(R.string.loading);
					loadingDialog = ProgressDialog.show(MainActivity.this, null, loadingMsg, true);
					break;
				}
				break;
			case LOADING_END:
				if(loadingDialog != null) {
					loadingDialog.dismiss();
					loadingDialog = null;
				}
				break;
			case ROM_ERROR:
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setMessage(R.string.rom_error).setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						arg0.dismiss();
						pickRom();
					}
				}).setOnCancelListener(new OnCancelListener() {

					@Override
					public void onCancel(DialogInterface arg0) {
						arg0.dismiss();
						pickRom();
					}
					
				});
				builder.create().show();
			}
		}
		
	};
	
	public static final int DRAW_SCREEN = 1337;
	public static final int PICK_ROM = 1338;
	public static final int LOADING_START = 1339;
	public static final int LOADING_END = 1340;
	public static final int ROM_ERROR = 1341;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		view = new NDSView(this);
		setContentView(view);
		
		controls = new Controls(view);

		Settings.applyDefaults(this);
		prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		loadJavaSettings(null);
		
		if(!DeSmuME.inited) 
			pickRom();
		
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);
	    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}
	
	void runEmulation() {
		boolean created = false;
		if(coreThread == null) {
			coreThread = new EmulatorThread(this);
			created = true;
		}
		else
			coreThread.setCurrentActivity(this);
		coreThread.setPause(!DeSmuME.romLoaded);
		if(created)
			coreThread.start();
	}
	
	void pauseEmulation() {
		if(coreThread != null) {
			coreThread.setPause(true);
		}
	}
	
	void pickRom() {
		Intent i = new Intent(this, FileDialog.class);
		i.setAction(Intent.ACTION_PICK);
		i.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getAbsolutePath());
		i.putExtra(FileDialog.FORMAT_FILTER, new String[] {".nds", ".zip", ".7z", ".rar"});
		startActivityForResult(i, PICK_ROM);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode != PICK_ROM || resultCode != Activity.RESULT_OK)
			return;
		String romPath = data.getStringExtra(FileDialog.RESULT_PATH);
		if(romPath != null) {
			runEmulation();
			coreThread.loadRom(romPath);
		}
			
	}
	
	@Override
	public void onResume() {
		super.onResume();
		runEmulation();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		pauseEmulation();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.activity_main, menu);
	    return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final int CHEAT_MENU_INDEX = 6;
		pauseEmulation();
		menu.findItem(R.id.cheats).setVisible(DeSmuME.romLoaded);
		return true;
	}
	
	@Override
	public void onOptionsMenuClosed(Menu menu) {
		runEmulation();
	}
	
	@Override
	public boolean onMenuItemSelected (int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case R.id.load:
			pickRom();
			break;
		case R.id.quicksave:
			saveState(0);
			break;
		case R.id.quickrestore:
			restoreState(0);
			break;
		case R.id.restore1: case R.id.restore2: case R.id.restore3: case R.id.restore4: case R.id.restore5:
		case R.id.restore6: case R.id.restore7: case R.id.restore8: case R.id.restore9:
			restoreState(Integer.valueOf(item.getTitle().toString()));
			break;
		case R.id.save1: case R.id.save2: case R.id.save3: case R.id.save4: case R.id.save5:
		case R.id.save6: case R.id.save7: case R.id.save8: case R.id.save9:
			saveState(Integer.valueOf(item.getTitle().toString()));
			break;
		case R.id.settings:
			startActivity(new Intent(this, Settings.class));
			break;
		case R.id.cheats:
			startActivity(new Intent(this, Cheats.class));
			break;
		case R.id.exit:
			finish();
			break;
		default:
			return false;
		}
		runEmulation();
		return true;
	}
	
	void restoreState(int slot) {
		if(DeSmuME.romLoaded) {
			coreThread.inFrameLock.lock();
				DeSmuME.restoreState(slot);
			coreThread.inFrameLock.unlock();
		}
	}
	
	void saveState(int slot) {
		if(DeSmuME.romLoaded) {
			coreThread.inFrameLock.lock();
				DeSmuME.saveState(slot);
			coreThread.inFrameLock.unlock();
		}
	}

	
	SharedPreferences prefs = null;
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

		if(DeSmuME.inited)
			DeSmuME.loadSettings();
		loadJavaSettings(key);
			
	}
	
	void loadJavaSettings(String key)
	{
		if(key != null) {
			if(DeSmuME.inited && key.equals(Settings.LANGUAGE))
				DeSmuME.reloadFirmware();
		}
		
		if(view != null) {
			view.showTouchMessage = prefs.getBoolean(Settings.SHOW_TOUCH_MESSAGE, true);
			view.vsync = prefs.getBoolean(Settings.VSYNC, true);
			view.showSoundMessage = prefs.getBoolean(Settings.SHOW_SOUND_MESSAGE, true);
			view.lcdSwap = prefs.getBoolean(Settings.LCD_SWAP, false);
			view.buttonAlpha = (int)(prefs.getInt(Settings.BUTTON_TRANSPARENCY, 78) * 2.55f);
			view.haptic = prefs.getBoolean(Settings.HAPTIC, false);
			view.dontRotate = prefs.getBoolean(Settings.DONT_ROTATE_LCDS, false);
			view.alwaysTouch = prefs.getBoolean(Settings.ALWAYS_TOUCH, false);
			
			controls.loadMappings(this);
			
			if(key != null) {
				if(key.equals(Settings.SCREEN_FILTER)) {
					int newFilter = DeSmuME.getSettingInt(Settings.SCREEN_FILTER, 0);
					DeSmuME.setFilter(newFilter);
					view.forceResize();
				}
				else if(key.equals(Settings.RENDERER)) {
					int new3D = DeSmuME.getSettingInt(Settings.RENDERER, 2);
					if(coreThread != null)
						coreThread.change3D(new3D);
				}
				else if(key.equals(Settings.ENABLE_SOUND)) {
					int newSound = DeSmuME.getSettingInt(Settings.ENABLE_SOUND, 0);
					if(coreThread != null)
						coreThread.changeSound(newSound);
				}
			}
		}
	}
	
	
	class NDSView extends SurfaceView implements Callback {

		SurfaceHolder surfaceHolder;
		Bitmap emuBitmapMain, emuBitmapTouch;
		DrawingThread drawingThread;
		
		final Paint emuPaint = new Paint();
		
		public boolean showTouchMessage = false;
		public boolean showSoundMessage = false;
		public boolean lcdSwap = false;
		public boolean vsync = true;
		public boolean forceTouchScreen = false;
		public int buttonAlpha = 78;
		public boolean haptic = true;
		public boolean alwaysTouch = false;
		
		public NDSView(Context context) {
			super(context);
			surfaceHolder = getHolder();
			surfaceHolder.addCallback(this);
			setKeepScreenOn(true);
			setWillNotDraw(false);
			setFocusable(true);
			setFocusableInTouchMode(true);
			
			
		}
		
		boolean doForceResize = false;
		public void forceResize() {
			doForceResize = true;
		}
		
		
		
		@Override
		public void onDraw(Canvas canvas) {
			
			if(showTouchMessage) {
				prefs.edit().putBoolean(Settings.SHOW_TOUCH_MESSAGE, showTouchMessage = false).apply();
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setPositiveButton(R.string.OK, null).setMessage(R.string.touchnotify).create().show();
			}
			
			if(showSoundMessage) {
				prefs.edit().putBoolean(Settings.SHOW_SOUND_MESSAGE, showSoundMessage = false).apply();
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setMessage(R.string.soundmsg).setPositiveButton(R.string.yes, new Dialog.OnClickListener() {

					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						prefs.edit().putBoolean(Settings.ENABLE_SOUND, true).apply();
						arg0.dismiss();
					}
					
				}).setNegativeButton(R.string.no, null).create().show();
			}
			
		}
		
		
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			return controls.onTouchEvent(event);
		}
		
		boolean resized = false;
		boolean sized = false;
		boolean landscape = false;
		boolean dontRotate = false;
		int sourceWidth;
		int sourceHeight;
		Rect srcMain, destMain, srcTouch, destTouch;
		int width = 0, height = 0, pixelFormat;
		
		
		void resize(int newWidth, int newHeight, int newPixelFormat) {
			
			synchronized(view.surfaceHolder) {
				sourceWidth = DeSmuME.getNativeWidth();
				sourceHeight = DeSmuME.getNativeHeight();
				resized = true;
				
				final boolean hasScreenFilter = DeSmuME.getSettingInt(Settings.SCREEN_FILTER, 0) != 0;
				final boolean is565 = newPixelFormat == PixelFormat.RGB_565 && !hasScreenFilter;
				landscape = newWidth > newHeight;
				controls.setView(this);
				controls.loadControls(MainActivity.this, newWidth, newHeight, is565, landscape);
				
				forceTouchScreen = !prefs.getBoolean("Controls." + (landscape ? "Landscape." : "Portrait.") + "Draw", false);
				
				
				if(landscape) {
					destMain = new Rect(0, 0, newWidth / 2, newHeight);
					destTouch = new Rect(newWidth / 2, 0, newWidth, newHeight);
				}
				else {
					destMain = new Rect(0, 0, newWidth, newHeight / 2);
					destTouch = new Rect(0, newHeight / 2, newWidth, newHeight);
				}
				
				if(landscape && dontRotate) {
					emuBitmapMain = Bitmap.createBitmap(sourceHeight / 2, sourceWidth, is565 ? Config.RGB_565 : Config.ARGB_8888);
					emuBitmapTouch = Bitmap.createBitmap(sourceHeight / 2, sourceWidth, is565 ? Config.RGB_565 : Config.ARGB_8888);
					srcMain = new Rect(0, 0, sourceHeight / 2, sourceWidth);
					srcTouch = new Rect(0, 0, sourceHeight / 2, sourceWidth);
				}
				else {
					emuBitmapMain = Bitmap.createBitmap(sourceWidth, sourceHeight / 2, is565 ? Config.RGB_565 : Config.ARGB_8888);
					emuBitmapTouch = Bitmap.createBitmap(sourceWidth, sourceHeight / 2, is565 ? Config.RGB_565 : Config.ARGB_8888);
					srcMain = new Rect(0, 0, sourceWidth, sourceHeight / 2);
					srcTouch = new Rect(0, 0, sourceWidth, sourceHeight / 2);
				}
				DeSmuME.resize(emuBitmapMain);
				
				requestFocus();
				
				width = newWidth;
				height = newHeight;
				pixelFormat = newPixelFormat;
				sized = true;
				doForceResize = false;
			}
		}


		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			synchronized(surfaceHolder) {
				view.resize(width, height, format);
			}
		}


		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			drawingThread = new DrawingThread(coreThread, this);
			drawingThread.start();
		}


		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			if(drawingThread != null) {
				drawingThread.keepDrawing.set(false);
				drawingThread.drawEventLock.lock();
				drawingThread.drawEvent.signal();
				drawingThread.drawEventLock.unlock();
				drawingThread = null;
			}
		}
		
		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event) {
			return controls.onKeyDown(keyCode, event);
		}
		
		@Override
		public boolean onKeyUp(int keyCode, KeyEvent event) {
			return controls.onKeyUp(keyCode, event);
		}
		
	}
	
}

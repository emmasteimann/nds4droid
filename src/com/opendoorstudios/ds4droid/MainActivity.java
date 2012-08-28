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
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

	static EmulatorThread coreThread;
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
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

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
		pauseEmulation();
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

	
	static final int BUTTON_RIGHT = 0;
	static final int BUTTON_DOWN = 1;
	static final int BUTTON_UP = 2;
	static final int BUTTON_LEFT = 3;
	static final int BUTTON_A = 4;
	static final int BUTTON_B = 5;
	static final int BUTTON_X = 6;
	static final int BUTTON_Y = 7;
	static final int BUTTON_L = 8;
	static final int BUTTON_R = 9;
	static final int BUTTON_START = 10;
	static final int BUTTON_SELECT = 11;
	static final int BUTTON_TOUCH = 12;

	final int[] buttonStates = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	
	final SparseIntArray touchMap = new SparseIntArray();
	
	SharedPreferences prefs = null;
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

		if(DeSmuME.inited)
			DeSmuME.loadSettings();
		loadJavaSettings(key);
			
	}
	
	void loadJavaSettings(String key)
	{
		if(view != null) {
			view.showTouchMessage = prefs.getBoolean(Settings.SHOW_TOUCH_MESSAGE, true);
			view.vsync = prefs.getBoolean(Settings.VSYNC, true);
			view.showSoundMessage = prefs.getBoolean(Settings.SHOW_SOUND_MESSAGE, true);
			
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
		Bitmap emuBitmap;
		Bitmap controls;
		Bitmap touchControls;
		DrawingThread drawingThread;
		final Paint controlsPaint = new Paint();
		final Paint emuPaint = new Paint();
		
		public boolean showTouchMessage = false;
		public boolean showSoundMessage = false;
		public boolean vsync = true;
		
		public NDSView(Context context) {
			super(context);
			surfaceHolder = getHolder();
			surfaceHolder.addCallback(this);
			setKeepScreenOn(true);
			setWillNotDraw(false);
			controlsPaint.setAlpha(200);
			
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
			if(xscale == 0 || yscale == 0)
				return false;
			if(DeSmuME.touchScreenMode) {
				switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_MOVE:
					float x = event.getX();
					float y = event.getY();
					x /= xscale;
					y /= yscale;
					//convert to bottom touch screen coordinates
					y -= 192;
					if(y >= 0)
						DeSmuME.touchScreenTouch((int)x, (int)y);
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					DeSmuME.touchScreenRelease();
					if(touchrect.contains((int)event.getX(), (int)event.getY())) {
						DeSmuME.touchScreenMode = false;
					}
					break;
				default:
					return false;
				}
				return true;				
			}
			else
			{
				if(!sized)
					return false;
				switch(event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_POINTER_DOWN:
				case MotionEvent.ACTION_MOVE:
				{
						int i = event.getActionIndex();
						int id = event.getPointerId(i);
						
						int existingTouch = touchMap.get(id, -1);
						if(existingTouch != -1) {
							//reset touch, it may get re-set below but we need to deal with sliding off a button
							buttonStates[existingTouch] = 0;
						}
						int x = (int) event.getX(i);
						int y = (int) event.getY(i);

						if(leftrect.contains(x, y))
							touchMap.put(id, BUTTON_LEFT);
						else if(rightrect.contains(x, y))
							touchMap.put(id, BUTTON_RIGHT);
						else if(uprect.contains(x, y))
							touchMap.put(id, BUTTON_UP);
						else if(downrect.contains(x, y))
							touchMap.put(id, BUTTON_DOWN);
						else if(arect.contains(x, y))
							touchMap.put(id, BUTTON_A);
						else if(brect.contains(x, y))
							touchMap.put(id, BUTTON_B);
						else if(xrect.contains(x, y))
							touchMap.put(id, BUTTON_X);
						else if(yrect.contains(x, y))
							touchMap.put(id, BUTTON_Y);
						else if(lrect.contains(x, y))
							touchMap.put(id, BUTTON_L);
						else if(rrect.contains(x, y))
							touchMap.put(id, BUTTON_R);
						else if(startrect.contains(x, y))
							touchMap.put(id, BUTTON_START);
						else if(selectrect.contains(x, y)) 
							touchMap.put(id, BUTTON_SELECT);
						else
							break;
						
						buttonStates[touchMap.get(id)] = 1;
				}
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
					if(touchrect.contains((int)event.getX(), (int)event.getY())) {
						DeSmuME.touchScreenMode = true;
						touchMap.clear();
						break;
					}
					//FT
				case MotionEvent.ACTION_CANCEL:
				{
					int i = event.getActionIndex();
					int id = event.getPointerId(i);
					int button = touchMap.get(id, -1);
					if(button == -1)
						break;
					buttonStates[button] = 0;
					touchMap.delete(id);
				}
					break;
				default:
					return false;
				}
				DeSmuME.setButtons(buttonStates[BUTTON_L], buttonStates[BUTTON_R], buttonStates[BUTTON_UP], buttonStates[BUTTON_DOWN], buttonStates[BUTTON_LEFT], buttonStates[BUTTON_RIGHT], 
						buttonStates[BUTTON_A], buttonStates[BUTTON_B], buttonStates[BUTTON_X], buttonStates[BUTTON_Y], buttonStates[BUTTON_START], buttonStates[BUTTON_SELECT]);
				return true;
			}
		}
		
		boolean resized = false;
		boolean sized = false;
		int sourceWidth;
		int sourceHeight;
		Rect src, dest;
		int width = 0, height = 0;
		
		float xscale = 0, yscale = 0;
		
		public Rect lrect, rrect, touchrect, uprect, leftrect, downrect, rightrect, startrect, selectrect, xrect, arect, yrect, brect;

		void resize(int newWidth, int newHeight) {
			
			synchronized(view.surfaceHolder) {
				//TODO: Use desmume resizing if desired as well as landscape mode
				sourceWidth = DeSmuME.getNativeWidth();
				sourceHeight = DeSmuME.getNativeHeight();
				resized = true;
				src = new Rect(0, 0, sourceWidth, sourceHeight);
				dest = new Rect(0, 0, newWidth, newHeight);
				
				xscale = (float)dest.width() / 256.0f;
				yscale = (float)dest.height() / 384.0f;
				
				Bitmap originalControls = BitmapFactory.decodeResource(getResources(), R.drawable.dscontrols);
				controls = Bitmap.createScaledBitmap(originalControls, dest.width(), dest.height(), true);
				
				Bitmap originalTouchControls = BitmapFactory.decodeResource(getResources(), R.drawable.dscontrolstouch);
				touchControls = Bitmap.createScaledBitmap(originalTouchControls, dest.width(), dest.height(), true);
				
				float controlxscale = (float)dest.width() / (float)originalControls.getWidth();
				float controlyscale = (float)dest.height() / (float)originalControls.getHeight();
				
				lrect = new Rect((int)(0 * controlxscale), (int)(0 * controlyscale), (int)(160 * controlxscale), (int)(90 * controlyscale));
				rrect = new Rect((int)(610 * controlxscale), (int)(0 * controlyscale), (int)(768 * controlxscale), (int)(90 * controlyscale));
				touchrect = new Rect((int)(320 * controlxscale), (int)(0 * controlyscale), (int)(430 * controlxscale), (int)(60 * controlyscale));
				leftrect = new Rect((int)(0 * controlxscale), (int)(915 * controlyscale), (int)(110 * controlxscale), (int)(1015 * controlyscale));
				uprect = new Rect((int)(111 * controlxscale), (int)(810 * controlyscale), (int)(221 * controlxscale), (int)(914 * controlyscale));
				rightrect = new Rect((int)(222 * controlxscale), (int)(915 * controlyscale), (int)(333 * controlxscale), (int)(1015 * controlyscale));
				downrect = new Rect((int)(111 * controlxscale), (int)(1016 * controlyscale), (int)(221 * controlxscale), (int)(1116 * controlyscale));
				arect = new Rect((int)(639 * controlxscale), (int)(895 * controlyscale), (int)(768 * controlxscale), (int)(1026 * controlyscale));
				brect = new Rect((int)(521 * controlxscale), (int)(995 * controlyscale), (int)(639 * controlxscale), (int)(1118 * controlyscale));
				yrect = new Rect((int)(397 * controlxscale), (int)(895 * controlyscale), (int)(517 * controlxscale), (int)(1026 * controlyscale));
				xrect = new Rect((int)(521 * controlxscale), (int)(805 * controlyscale), (int)(639 * controlxscale), (int)(927 * controlyscale));
				startrect = new Rect((int)(270 * controlxscale), (int)(1082 * controlyscale), (int)(364 * controlxscale), (int)(1152 * controlyscale));
				selectrect = new Rect((int)(400 * controlxscale), (int)(1082 * controlyscale), (int)(485 * controlxscale), (int)(1152 * controlyscale));
				
				emuBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Config.ARGB_8888);
				DeSmuME.resize(emuBitmap);
				width = newWidth;
				height = newHeight;
				sized = true;
				doForceResize = false;
			}
		}


		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2,
				int arg3) {
			// TODO Auto-generated method stub
			
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
		
	}
	
}

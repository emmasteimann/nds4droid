package com.opendoorstudios.ds4droid;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Environment;
import android.preference.PreferenceManager;

class EmulatorThread extends Thread {
	
	public EmulatorThread(MainActivity activity) {
		super("EmulatorThread");
		this.activity = activity;
	}
	
	public void setCurrentActivity(MainActivity activity) {
		this.activity = activity;
	}
	
	public static boolean inited = false;
	public static boolean romLoaded = false;
	
	long lastDraw = 0;
	final AtomicBoolean finished = new AtomicBoolean(false);
	final AtomicBoolean paused = new AtomicBoolean(false);
	String pendingRomLoad = null;
	
	public void loadRom(String path) {
		pendingRomLoad = path;
		synchronized(dormant) {
			dormant.notifyAll();
		}
	}
	
	public void setCancel(boolean set) {
		finished.set(set);
		synchronized(dormant) {
			dormant.notifyAll();
		}
	}
	
	public void setPause(boolean set) {
		paused.set(set);
		synchronized(dormant) {
			dormant.notifyAll();
		}
	}
	
	Object dormant = new Object();
	
	public Lock inFrameLock = new ReentrantLock();
	int fps = 1;
	MainActivity activity = null;
	
	@Override
	public void run() {
		
		while(!finished.get()) {
			
			if(!inited) {
				DeSmuME.context = activity;
				DeSmuME.load();
				
				final String defaultWorkingDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/nds4droid";
				final String path = PreferenceManager.getDefaultSharedPreferences(activity).getString(Settings.DESMUME_PATH, defaultWorkingDir);
				final File workingDir = new File(path);
				DeSmuME.setWorkingDir(workingDir.getAbsolutePath());
				workingDir.mkdir();
				new File(path + "/States").mkdir();
				new File(path + "/Battery").mkdir();
				
				
				DeSmuME.init();
				inited = true;
			}
			if(pendingRomLoad != null) {
				if(!DeSmuME.loadRom(pendingRomLoad)) {
					romLoaded = false;
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
					builder.setMessage(R.string.rom_error).setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							arg0.dismiss();
							activity.msgHandler.sendEmptyMessage(MainActivity.PICK_ROM);
						}
					}).setOnCancelListener(new OnCancelListener() {

						@Override
						public void onCancel(DialogInterface arg0) {
							arg0.dismiss();
							activity.msgHandler.sendEmptyMessage(MainActivity.PICK_ROM);
						}
						
					});
					builder.create().show();
				}
				else {
					romLoaded = true;
					paused.set(false);
				}
				pendingRomLoad = null;
			}
			
			if(!paused.get()) {
				
				inFrameLock.lock();
				fps = DeSmuME.runCore();
				inFrameLock.unlock();
				DeSmuME.runOther();
				
				if(System.currentTimeMillis() - lastDraw > (1000/fps)) {
					activity.msgHandler.sendEmptyMessage(MainActivity.DRAW_SCREEN);
					lastDraw = System.currentTimeMillis();
				}
				
			} 
			else {
				//hacky, but keeps thread alive so we don't lose contexts
				try {
					synchronized(dormant) {
						dormant.wait();
					}
				} 
				catch (InterruptedException e) {
				} 
			}
		}
	}
}
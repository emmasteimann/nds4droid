package com.opendoorstudios.ds4droid;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.opendoorstudios.ds4droid.MainActivity.NDSView;

import android.graphics.Canvas;

class DrawingThread extends Thread{
	
	public DrawingThread(EmulatorThread coreThread, NDSView view) {
		this.view = view;
		this.coreThread = coreThread;
	}
	
	final NDSView view;
	final EmulatorThread coreThread;
	
	Lock drawEventLock = new ReentrantLock();
	Condition drawEvent = drawEventLock.newCondition();
	
	AtomicBoolean keepDrawing = new AtomicBoolean(true);
	
	@Override
	public void run() {
		
		while(keepDrawing.get()) {
		
			drawEventLock.lock();
			try {
				drawEvent.await();
			} catch (InterruptedException e) {
			}
			
			if(!keepDrawing.get())
				return;
			
			Canvas canvas = view.surfaceHolder.lockCanvas();
			try {
				synchronized(view.surfaceHolder) {
					
					if(!DeSmuME.inited)
						continue;
				
					if(view.width != canvas.getWidth() || view.height != canvas.getHeight() || view.doForceResize)
						view.resize(canvas.getWidth(), canvas.getHeight());
					
					if(view.emuBitmap == null)
						continue;

					if(view.vsync) {
						coreThread.inFrameLock.lock();
							DeSmuME.copyMasterBuffer();
						coreThread.inFrameLock.unlock();
					}
					else
						DeSmuME.copyMasterBuffer();
					DeSmuME.draw(view.emuBitmap);
					
					canvas.drawBitmap(view.emuBitmap, view.src, view.dest, null);
					if(DeSmuME.touchScreenMode)
						canvas.drawBitmap(view.touchControls, 0, 0, view.controlsPaint);
					else
						canvas.drawBitmap(view.controls, 0, 0, view.controlsPaint);

				}
			}
			finally {
				view.surfaceHolder.unlockCanvasAndPost(canvas);
				drawEventLock.unlock();
			}
		
		}
		
	}
	
}
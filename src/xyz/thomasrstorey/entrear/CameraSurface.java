package xyz.thomasrstorey.entrear;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

@SuppressWarnings("deprecation")
public class CameraSurface extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
	
	private Camera camera;
	private static final String TAG = "CameraSurface";
	public CameraSurface(Context context) {
		super(context);
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		EntreARActivity.nativeVideoFrame(data);
		camera.addCallbackBuffer(data);
	}
	@SuppressLint("NewApi")
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			int cameraIndex = 0;
			camera = Camera.open(cameraIndex);
		} catch (RuntimeException e) {
			Log.e(TAG, "cannot open camera");
		}
		if(camera != null) {
			try {
				camera.setPreviewDisplay(holder);
				camera.setPreviewCallbackWithBuffer(this);
			} catch (IOException e) {
				Log.e(TAG, "cannot set camera preview display");
				camera.release();
				camera = null;
			}
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (camera != null) {
			Camera.Parameters parameters = camera.getParameters();
			parameters.setPreviewSize(1280, 960);
			parameters.setWhiteBalance("auto");
			camera.setParameters(parameters);
			parameters = camera.getParameters();
			int capWidth = parameters.getPreviewSize().width;
			int capHeight = parameters.getPreviewSize().height;
			int pixelFormat = parameters.getPreviewFormat();
			PixelFormat pixelInfo = new PixelFormat();
			PixelFormat.getPixelFormatInfo(pixelFormat, pixelInfo);
			int cameraIndex = 0;
			boolean frontFacing = false;
			
//			dont need to get camera preferences, will only use back camera
			
			int bufSize = capWidth * capHeight * pixelInfo.bitsPerPixel / 8;
			for (int i = 0; i < 5; i++) camera.addCallbackBuffer(new byte[bufSize]);
			camera.startPreview();
			EntreARActivity.nativeVideoInit(capWidth, capHeight, cameraIndex, frontFacing);
		}
		
		
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		return super.onTouchEvent(event);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if(camera != null) {
			camera.stopPreview();
			camera.setPreviewCallback(null);
			camera.release();
			camera = null;
		}
	}

}

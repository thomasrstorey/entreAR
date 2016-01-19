package xyz.thomasrstorey.entrear;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.util.Log;

public class EntreARRenderer implements GLSurfaceView.Renderer {
	
	private static final String TAG = "entreAR";
	
	int x,y,w,h;
	EntreARActivity context;
	boolean ss;
	
	EntreARRenderer (int _x, int _y, int _w, int _h, EntreARActivity _context){
		x = _x;
		y = _y;
		w = 1440;
		h = 1080;
		context = _context;
		ss = false;
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		EntreARActivity.nativeSurfaceCreated();
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		EntreARActivity.nativeSurfaceChanged(width, height);
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		EntreARActivity.nativeDrawFrame();
		if(ss){
			context.onScreenshot(captureBitmap(gl));
			ss = !ss;
		}
	}
	
	private File captureBitmap(GL10 gl) {
		File f;
		int b[]=new int[w*(y+h)];
	    int bt[]=new int[w*h];
	    Log.v(TAG, "WIDTHxHEIGHT: " + w + "x" + h);
	    IntBuffer ib=IntBuffer.wrap(b);
	    ib.position(0);
	    gl.glReadPixels(x, 0, w, y+h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, ib);
	    for(int i=0, k=0; i<h; i++, k++) {
          for(int j=0; j<w; j++){
               int pix=b[i*w+j];
               int pb=(pix>>16)&0xff;
               int pr=(pix<<16)&0x00ff0000;
               int pix1=(pix&0xff00ff00) | pr | pb;
               bt[(h-k-1)*w+j]=pix1;
          }
	    }
	    Bitmap bmp = Bitmap.createBitmap(bt, w, h,Bitmap.Config.ARGB_8888);
	    if(isExternalStorageWritable()){
		    try {
				
					f = new File(getAlbumStorageDir().getAbsolutePath() + "/ss.png");
		            FileOutputStream fos=new FileOutputStream(f);
	
		            bmp.compress(CompressFormat.PNG, 100, fos);
	            try {
	            	fos.flush();
	            } catch (IOException e){
	            	e.printStackTrace();
	            }
	            try {
	            	fos.close();
	            } catch(IOException e) {
	            	e.printStackTrace();
	            }
		    } catch (IOException e) {
		    	f = new File("/null");
		    	e.printStackTrace();
		    }
		    return f;
	    } else {
	    	Log.e(TAG, "Storage not writable");
	    	return new File("/null");
	    }
	}
	
	private File getAlbumStorageDir() {
	    // Get the directory for the user's public pictures directory.
	    File file = new File(Environment.getExternalStoragePublicDirectory(
	            Environment.DIRECTORY_PICTURES), "EntreAR");
	    if (file.mkdirs() || file.isDirectory()) {
	        Log.e(TAG, "Directory not created");
	    }
	    return file;
	}
	
	private boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}

	
	public void scheduleScreenshot () {
		ss = true;
	}

}

/*
 * Thomas R Storey
 * 2015-2016
 * EntreAR
 */

package xyz.thomasrstorey.entrear;

import java.io.File;

import org.artoolkit.ar.base.camera.CameraPreferencesActivity;
import org.json.JSONException;
import org.json.JSONObject;


import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout.LayoutParams;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class EntreARActivity extends FragmentActivity {
	
	static {
		System.loadLibrary("c++_shared");
		System.loadLibrary("entreARNative");
	}
	
//	lifecycle functions
	public static native boolean nativeCreate(Context ctx);
	public static native boolean nativeStart();
	public static native boolean nativeStop();
	public static native boolean nativeDestroy();
//	camera functions
	public static native boolean nativeVideoInit(int w, int h, int cameraIndex, boolean cameraIsFrontFacing);
	public static native void nativeVideoFrame(byte[] image);
//	opengl functions
	public static native void nativeSurfaceCreated();
	public static native void nativeSurfaceChanged(int w, int h);
	public static native void nativeDrawFrame();
	
	public static native void nativeDisplayParametersChanged(int orientation, int w, int h, int dpi);
	public static native void nativeSetInternetState(int state);
	
	public static native void nativeLoadModel(String objpath, int pathlength);
	
	public static int DISH_NOT_LOADED = 0;
	public static int DISH_LOADING = 1;
	public static int DISH_LOADED = 2;
	
	public static String ORDER_DISH_URL = "quick-and-easy.recipes";
	public static String ORDER_DISH_PATH = "/api/order/";
	
	private static final String TAG = "entreAR";
	
	private EntreARGLSurfaceView glView;
	private CameraSurface camSurface;
	private FrameLayout mainLayout;
	private int entreARState;
	private String dishObjURL;
	private String dishMtlURL;
	private String dishTexURL;
	private String dishName;
	private File dishObj;
	private File dishMtl;
	private File dishTex;
	public String decideText;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		updateNativeDisplayParameters();
		setContentView(R.layout.activity_entrear);
		EntreARActivity.nativeCreate(this);
		File[] tmpfiles = new File[3];
		setDishFiles(tmpfiles);
		updateModel();
	}
	
	@Override
	public void onStart(){
		super.onStart();
		mainLayout = (FrameLayout)this.findViewById(R.id.entreARLayout);
		EntreARActivity.nativeStart();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
		nativeSetInternetState(isConnected ? 1 : 0);
		entreARState = DISH_NOT_LOADED;
		File[] tmpfiles = new File[3];
		setDishFiles(tmpfiles);
		updateModel();
		camSurface = new CameraSurface(this);
		glView = new EntreARGLSurfaceView(this);
		glView.setZOrderMediaOverlay(true);
		mainLayout.addView(camSurface, new LayoutParams(128, 128));
		mainLayout.addView(glView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		if(glView != null) glView.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if(glView != null) glView.onPause();
		mainLayout.removeView(glView);
		mainLayout.removeView(camSurface);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		EntreARActivity.nativeStop();
	}
	
	private void updateNativeDisplayParameters() {
		Display d = getWindowManager().getDefaultDisplay();
		int orientation = d.getRotation();
		DisplayMetrics dm = new DisplayMetrics();
		d.getMetrics(dm);
		int w = dm.widthPixels;
		int h = dm.heightPixels;
		int dpi = dm.densityDpi;
		nativeDisplayParametersChanged(orientation, w, h, dpi);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		updateNativeDisplayParameters();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options, menu);
		return true;	
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.settings) {
			startActivity(new Intent(this, CameraPreferencesActivity.class));
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}
	
	public int getEntreARState(){
		return entreARState;
	}
	
	public void setEntreARState(int next){
		entreARState = next;
	}
	
	public void setDish(JSONObject orderOutput){
		try {
			dishObjURL = orderOutput.getString("objURL");
			dishMtlURL = orderOutput.getString("mtlURL");
			dishTexURL = orderOutput.getString("texURL");
			dishName = orderOutput.getString("name");
		}catch (JSONException e){
			e.printStackTrace();
		}
	}
	
	public void setDecideText(String msg){
		decideText = msg;
	}
	
	public String[] getDishURLs(){
		String[] urls = new String[3];
		urls[0] = dishObjURL;
		urls[1] = dishMtlURL;
		urls[2] = dishTexURL;
		return urls;
	}
	
	public void setDishFiles(File[] files){
		dishObj = files[0];
		dishMtl = files[1];
		dishTex = files[2];
	}
	
	public void updateModel(){
		String objpath = "Data/models/button.obj";
		if((dishObj != null && dishObj.exists()) && (dishMtl != null && dishMtl.exists()) && (dishTex != null && dishTex.exists()) ){
			Log.v(TAG, "MTL PATH: " + dishMtl.getPath());
			Log.v(TAG, "TEX PATH: " + dishTex.getPath());
			objpath = dishObj.getPath();
		}
		Log.v(TAG, objpath);
		nativeLoadModel(objpath, objpath.length());
	}
	
	public void captureScreenshot(View view) {
	   glView.captureScreenshot();
	}
	
	public void onScreenshot(File file){
		createInstagramIntent("image/*", file);
	}
	
	private void createInstagramIntent(String type, File media){
	    Intent share = new Intent(Intent.ACTION_SEND);
	    share.setType(type);
	    Log.v(TAG, media.getAbsolutePath());
	    Uri uri = Uri.fromFile(media);
	    share.putExtra(Intent.EXTRA_STREAM, uri);
	    startActivity(Intent.createChooser(share, "Share to"));
	}
}

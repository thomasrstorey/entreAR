/*
 * Thomas R Storey
 * 2015
 * EntreAR
 */

package xyz.thomasrstorey.entrear;

import java.io.File;

import org.artoolkit.ar.base.camera.CameraPreferencesActivity;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout.LayoutParams;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class EntreARActivity extends Activity {
	
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
	
	public static native void nativeLoadModel(String objpath, String mtlpath, String texpath);
	
	public static int DISH_NOT_LOADED = 0;
	public static int DISH_LOADING = 1;
	public static int DISH_LOADED = 2;
	
	public static String ORDER_DISH_URL = "http://quickandeasyrecipes.xyz/api/order/";
	
	private GLSurfaceView glView;
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
		setDishFiles(new File[3]);
		camSurface = new CameraSurface(this);
		glView = new EntreARGLSurfaceView(this);
		glView.setRenderer(new Renderer());
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
		String objpath;
		String mtlpath;
		String texpath;
		if(dishObj.exists() && dishMtl.exists() && dishTex.exists()){
			objpath = dishObj.getPath();
			mtlpath = dishMtl.getPath();
			texpath = dishTex.getPath();
		} else {
			objpath = "Data/models/button.obj";
			mtlpath = "Data/models/button.mtl";
			texpath = "Data/models/button/button.jpg";
		}
		nativeLoadModel(objpath, mtlpath, texpath);
	}
}

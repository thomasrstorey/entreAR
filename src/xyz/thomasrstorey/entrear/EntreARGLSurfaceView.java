package xyz.thomasrstorey.entrear;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.json.JSONObject;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import xyz.thomasrstorey.entrear.OrderDishTask.OrderDishResponse;
import xyz.thomasrstorey.entrear.GetDishTask.GetDishResponse;

public class EntreARGLSurfaceView extends GLSurfaceView implements DecideDialogFragment.DecideDialogListener {

	EntreARActivity context;
	private static final String TAG = "entreAR";
	int x,y,w,h;
	EntreARRenderer renderer;
	ProgressBar progressBar;
	
	public EntreARGLSurfaceView(Context _context) {
		super(_context);
		context = (EntreARActivity)_context;
		progressBar = (ProgressBar) context.findViewById(R.id.recipe_progress);
		setEGLContextClientVersion(1);
		x = 0;
		y = 0;
		w = this.getWidth();
		h = this.getHeight();
		renderer = new EntreARRenderer(x,y,w,h, context);
		setRenderer(renderer);
	}
	
	public void showDecideDialog() {
		DecideDialogFragment dialog = new DecideDialogFragment();
		dialog.setListener(this);
		dialog.show(context.getSupportFragmentManager(), "DecideDialogFragment");
	}
	
	@Override
	public void onDialogPositiveClick(DialogFragment dialog, String msg){
		progressBar.setVisibility(View.VISIBLE);
		progressBar.setIndeterminate(true);
		context.setDecideText(msg);
		getDish();
	}
	
	public void getDish () {
		if(context.getEntreARState() == EntreARActivity.DISH_NOT_LOADED){
			context.setEntreARState(EntreARActivity.DISH_LOADING);
			OrderDishTask orderTask = new OrderDishTask(context, new OrderDishResponse(){
				@Override
				public void processFinish(JSONObject output){
					context.setDish(output);
					GetDishTask getTask = new GetDishTask(context, new GetDishResponse(){
						@Override
						public void processFinish(File[] output){
							Log.v(TAG, "FINISH GET DISH TASK" + output.length);
							progressBar.setVisibility(View.GONE);
							context.setEntreARState(EntreARActivity.DISH_LOADED);
							context.setDishFiles(output);
							context.updateModel();
						}
					});
					String[] urls = context.getDishURLs();
					getTask.execute(urls[0], urls[1], urls[2]);
				}
			});
			try {
				String encoded = URLEncoder.encode(context.decideText, "UTF-8");
				orderTask.execute("http://"+EntreARActivity.ORDER_DISH_URL
								 +EntreARActivity.ORDER_DISH_PATH+"?d="+encoded);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			
		} else if(context.getEntreARState() == EntreARActivity.DISH_LOADED){
			context.setEntreARState(EntreARActivity.DISH_NOT_LOADED);
		}
	}
	
	@Override
	public boolean onTouchEvent (MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_UP){
			Log.v(TAG, "TOUCH EVENT");
			if(context.getEntreARState() == EntreARActivity.DISH_LOADED){
				context.setEntreARState(EntreARActivity.DISH_NOT_LOADED);
			}
			showDecideDialog();
		}
		return true;
	}
	
	public void captureScreenshot (){
		renderer.scheduleScreenshot();
	}

}

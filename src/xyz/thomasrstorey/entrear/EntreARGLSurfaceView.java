package xyz.thomasrstorey.entrear;

import java.io.File;

import org.json.JSONObject;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import xyz.thomasrstorey.entrear.OrderDishTask.OrderDishResponse;
import xyz.thomasrstorey.entrear.GetDishTask.GetDishResponse;

public class EntreARGLSurfaceView extends GLSurfaceView {

	EntreARActivity context;
	
	public EntreARGLSurfaceView(Context _context) {
		super(_context);
		context = (EntreARActivity)_context;
		setEGLContextClientVersion(2);
	}
	
	@Override
	public boolean onTouchEvent (MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_UP){
			if(context.getEntreARState() == EntreARActivity.DISH_NOT_LOADED){
				context.setEntreARState(EntreARActivity.DISH_LOADING);
				OrderDishTask orderTask = new OrderDishTask(context, new OrderDishResponse(){
					@Override
					public void processFinish(JSONObject output){
						context.setDish(output);
						GetDishTask getTask = new GetDishTask(context, new GetDishResponse(){
							@Override
							public void processFinish(File[] output){
								context.setEntreARState(EntreARActivity.DISH_LOADED);
								context.setDishFiles(output);
							}
						});
						String[] urls = context.getDishURLs();
						getTask.execute(urls[0], urls[1], urls[2]);
					}
				});
				orderTask.execute(EntreARActivity.ORDER_DISH_URL);
			} else if(context.getEntreARState() == EntreARActivity.DISH_LOADED){
				context.setEntreARState(EntreARActivity.DISH_NOT_LOADED);
			}
		}
		return true;
	}

}

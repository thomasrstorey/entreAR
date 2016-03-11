package xyz.thomasrstorey.entrear;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class GetDishTask extends AsyncTask<String, Void, File[]> {
	
	private static final String TAG = "entreAR";
	
	public interface GetDishResponse {
		public void processFinish(File[] output);
	}
	
	Context context;
	String filename;
	GetDishResponse delegate;
	
	public GetDishTask (Context _context, GetDishResponse _delegate) {
		context = _context;
		delegate = _delegate;
	}
	
	@Override
	protected void onPreExecute () {
		super.onPreExecute();
	}
	
	@Override
	protected File[] doInBackground(String... urls) {
		int count = urls.length;
		File[] files = new File[3];
		if(count != 3)return files;
		for(int i = 0; i < count; i++) {
			try {
				File file;
				String[] urlParts = urls[i].split("/");
				String[] filename = urlParts[urlParts.length-1].split("\\.");
				boolean delay = false;
				int retry = 0;
				int status = 0;
				do{
					if(delay){
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				URL url = new URL(urls[i]);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				status = conn.getResponseCode();
				Log.v(TAG, "STATUS CODE: " + status);
				if (status == 200) {
					Log.v(TAG, "DO THE THING");
		            InputStream in = new BufferedInputStream(conn.getInputStream());
		            try {
		            	file = new File(context.getCacheDir(), filename[0]+"."+filename[1]);
		            	OutputStream out = null;
		            	try {
		            		Log.v(TAG, "TRY");
		            		out = new BufferedOutputStream(new FileOutputStream(file));
		            		byte[] buffer = new byte[1024];
			            	int len = in.read(buffer);
		            		while (len != -1) {
			            	    out.write(buffer, 0, len);
			            	    len = in.read(buffer);
			            	}
		            	} finally {
		            		Log.v(TAG, "FINALLY");
		            	  Log.v(TAG, "downloaded: " + filename[0] + "." + filename[1]);
		            	  in.close();
		            	  out.close();
		            	  conn.disconnect();
		            	}
		            	files[i] = file;
		            } catch (IOException e){
		            	e.printStackTrace();
		            }
		        } else {
		        	conn.disconnect();
		        	retry++;
		        	delay = true;
		        }
				}while(retry < 20 && status != 200);
			} catch (IOException e){
				e.printStackTrace();
			} 	
		}
		//Log.v(TAG, "GET DISH TASK RETURN: " + files[0].getPath());
		return files;
	}
	
	@Override
	protected void onPostExecute (File[] files) {
		delegate.processFinish(files);
	}
}

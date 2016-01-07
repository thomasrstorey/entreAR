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

import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;

public class GetDishTask extends AsyncTask<String, Void, File[]> {
	
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
				String[] filename = urlParts[urlParts.length-1].split(".");
				URL url = new URL(urls[i]);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				int status = conn.getResponseCode();
				if (status == 200) {
		            InputStream in = new BufferedInputStream(conn.getInputStream());
		            try {
		            	file = File.createTempFile(filename[0], "."+filename[1], context.getCacheDir());
		            	OutputStream out = null;
		            	try {
		            		out = new BufferedOutputStream(new FileOutputStream(file));
		            		byte[] buffer = new byte[1024];
			            	int len = in.read(buffer);
		            		while (len != -1) {
			            	    out.write(buffer, 0, len);
			            	    len = in.read(buffer);
			            	}
		            	} finally {
		            	  in.close();
		            	  out.close();
		            	  conn.disconnect();
		            	}
		            	files[i] = file;
		            } catch (IOException e){
		            	e.printStackTrace();
		            }
		        }
			} catch (IOException e){
				e.printStackTrace();
			} 	
		}
		return files;
	}
	
	@Override
	protected void onPostExecute (File[] files) {
		
	}
}

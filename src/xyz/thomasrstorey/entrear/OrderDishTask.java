package xyz.thomasrstorey.entrear;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;

public class OrderDishTask extends AsyncTask<String, Void, JSONObject> {
	
	public interface OrderDishResponse {
		public void processFinish(JSONObject output);
	}
	
	Context context;
	public OrderDishResponse delegate = null;
	
	OrderDishTask(Context _context, OrderDishResponse _delegate){
		context = _context;
		delegate = _delegate;
	}
	
	protected void onPreExecute(){
		super.onPreExecute();
	}
	
	protected void onPostExecute(JSONObject json){
		delegate.processFinish(json);
	}
	
	protected JSONObject doInBackground(String... urlstr){
		if(urlstr.length != 1){
			return new JSONObject();
		}
		try {
			URL url = new URL(urlstr[0]);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			if(conn.getResponseCode() == 200){
				InputStream in = new BufferedInputStream(conn.getInputStream());
				//read input into string
				String jsonString = new String();
				byte[] buffer = new byte[1024];
				int len = in.read(buffer);
				while(len > -1) {
					String tempString = new String(buffer, "UTF-8");
					jsonString = jsonString.concat(tempString);
					len = in.read(buffer);
				}
				//parse string into JSONObject
				JSONObject json = new JSONObject(jsonString);
				//return JSONObject
				conn.disconnect();
				return json;
			} else {
				conn.disconnect();
				return new JSONObject();
			}
		} catch (IOException e) {
			e.printStackTrace();
			return new JSONObject();
		} catch (JSONException e) {
			e.printStackTrace();
			return new JSONObject();
		}
		
	}
}

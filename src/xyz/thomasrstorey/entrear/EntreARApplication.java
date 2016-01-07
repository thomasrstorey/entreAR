package xyz.thomasrstorey.entrear;

import org.artoolkit.ar.base.assets.AssetHelper;
import android.app.Application;
import android.preference.PreferenceManager;

public class EntreARApplication extends Application {
	
	private static Application sInstance;
	
	public static Application getInstance() {
		return sInstance;
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
		sInstance = this;
		((EntreARApplication) sInstance).initializeInstance();
	}
	
	protected void initializeInstance() {
		PreferenceManager.setDefaultValues(this, org.artoolkit.ar.base.R.xml.preferences, false);
		AssetHelper assetHelper = new AssetHelper(getAssets());
		assetHelper.cacheAssetFolder(getInstance(), "Data");
		assetHelper.cacheAssetFolder(getInstance(), "DataNFT");
	}
}

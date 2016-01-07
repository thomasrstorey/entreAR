#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <unistd.h>

#include <AR/ar.h>
#include <AR/arMulti.h>
#include <AR/video.h>
#include <AR/gsub_es.h>
#include <AR/arFilterTransMat.h>

#include <AR2/tracking.h>
#include <Eden/glm.h>

#include "ARMarkerNFT.h"
#include "trackingSub.h"

// types =========================================================================

typedef enum {
	    ARViewContentModeScaleToFill,
	    ARViewContentModeScaleAspectFit,
	    ARViewContentModeScaleAspectFill,
	    ARViewContentModeCenter,
	    ARViewContentModeTop,
	    ARViewContentModeBottom,
	    ARViewContentModeLeft,
	    ARViewContentModeRight,
	    ARViewContentModeTopLeft,
	    ARViewContentModeTopRight,
	    ARViewContentModeBottomLeft,
	    ARViewContentModeBottomRight,
}ARViewContentMode;

enum viewPortIndices {
	viewPortIndexLeft = 0,
	viewPortIndexBottom,
	viewPortIndexWidth,
	viewPortIndexHeight,
};

typedef struct ARModel {
	int patternID;
	ARdouble transformationMatrix[16];
	bool visible;
	GLMmodel* obj;
} ARModel;

// constants =====================================================================

#define NUM_MODELS 4
#define PAGES_MAX 4
#define MAX(X,Y) ((X) > (Y) ? (X) : (Y))
#define MIN(X,Y) ((X) > (Y) ? (Y) : (X))

#define LOG_TAG "entreARNative"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

// function prototypes ===============================================================

#define JNIFUNCTION_NATIVE(sig) Java_xyz_thomasrstorey_entrear_EntreARActivity_##sig

extern "C" {
    JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeCreate(JNIEnv* env, jobject object, jobject instanceOfAndroidContext));
	JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeStart(JNIEnv* env, jobject object));
	JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeStop(JNIEnv* env, jobject object));
	JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeDestroy(JNIEnv* env, jobject object));
	JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeVideoInit(JNIEnv* env, jobject object, jint w, jint h, jint cameraIndex, jboolean cameraIsFrontFacing));
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeVideoFrame(JNIEnv* env, jobject obj, jbyteArray pinArray)) ;
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeSurfaceCreated(JNIEnv* env, jobject object));
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeSurfaceChanged(JNIEnv* env, jobject object, jint w, jint h));
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeDisplayParametersChanged(JNIEnv* env, jobject object, jint orientation, jint width, jint height, jint dpi));
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeDrawFrame(JNIEnv* env, jobject obj));
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeSetInternetState(JNIEnv* env, jobject obj, jint state));
	JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeLoadModel(JNIEnv* env, jobject obj, jstring objPath, jstring mtlPath, jstring texPath));
};

static void nativeVideoGetCparamCallback(const ARParam* cparam, void* userdata);
static void* loadNFTDataAsync(THREAD_HANDLE_T *threadHandle);
static int initNFT(ARParamLT* cparamLT, AR_PIXEL_FORMAT pixFormat);

// global variables =================================================================

static const char* cparaName = "Data/camera_para.dat";
static const char* markerConfigDataFilename = "Data/markers.dat";

static AR2VideoParamT* 				gVid = NULL;
static bool 						videoInited = false;
static int 							videoWidth = 0;
static int 							videoHeight = 0;
static AR_PIXEL_FORMAT 				gPixFormat;
static ARUint8* 					gVideoFrame = NULL;
static size_t 						gVideoFrameSize = 0;
static bool 						videoFrameNeedsPixelBufferDataUpload = false;
static int 							gCameraIndex = 0;
static bool 						gCameraIsFrontFacing = false;

static ARMarkerNFT* 				markersNFT = NULL;
static int 							markersNFTCount = 0;

static THREAD_HANDLE_T* 			trackingThreadHandle = NULL;
static AR2HandleT*	 				ar2Handle = NULL;
static KpmHandle* 					kpmHandle = NULL;
static int 							surfaceSetCount = 0;
static AR2SurfaceSetT* 				surfaceSet[PAGES_MAX];
static THREAD_HANDLE_T* 			nftDataLoadingThreadHandle = NULL;
static int 							nftDataLoaded = false;

static int 							detectedPage = -2;
static float 						trackingTrans[3][4];

static int 							backingWidth;
static int 							backingHeight;
static GLint 						viewPort[4];
static ARViewContentMode 			gContentMode = ARViewContentModeScaleAspectFill;
static bool 						gARViewLayoutRequired = false;
static ARParamLT* 					gCParamLT = NULL;
static ARGL_CONTEXT_SETTINGS_REF 	gArglSettings = NULL;
static const ARdouble 				NEAR_PLANE = 10.0f;
static const ARdouble 				FAR_PLANE = 5000.0f;
static ARdouble 					cameraLens[16];
static ARdouble 					cameraPose[16];
static int 							cameraPoseValid;
static bool 						gARViewInited = false;

static int 							gDisplayOrientation = 1;
static int 							gDisplayWidth = 0;
static int 							gDisplayHeight = 0;
static int 							gDisplayDPI = 160;

static bool 						gContentRotate90 = false;
static bool 						gContentFlipV = false;
static bool 						gContentFlipH = false;

static int 							gInternetState = -1;

static ARModel 						models[NUM_MODELS] = {0};

static float 						lightAmbient[4] = {0.1f, 0.1f, 0.1f, 1.0f};
static float 						lightDiffuse[4] = {1.0f, 1.0f, 1.0f, 1.0f};
static float 						lightPosition[4] = {0.0f, 0.0f, 1.0f, 0.0f};

static char* 						modelFile;

// functions ==================================================================

// lifecycle ==================================================================

JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeCreate(JNIEnv* env, jobject object, jobject instanceOfAndroidContext)){

	int err_i;
	arUtilChangeToResourcesDirectory(AR_UTIL_RESOURCES_DIRECTORY_BEHAVIOR_BEST, NULL, instanceOfAndroidContext);
	newMarkers(markerConfigDataFilename, &markersNFT, &markersNFTCount);

	if(!markersNFTCount){
		LOGE("error loading markers from config\n");
		return false;
	}
	return true;
}

JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeStart(JNIEnv* env, jobject object)){
	gVid = ar2VideoOpen("");
	if(!gVid){
		LOGE("error: ar2VideoOpen\n");
		return false;
	}
	return true;
}

JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeStop(JNIEnv* env, jobject object)){
	int i, j;
	if(trackingThreadHandle){
		trackingInitQuit(&trackingThreadHandle);
	}
	j = 0;
	for(i = 0; i < surfaceSetCount; i++) {
		if(surfaceSet[i]) {
			ar2FreeSurfaceSet(&surfaceSet[i]);
			j++;
		}
	}
	surfaceSetCount = 0;
	nftDataLoaded = false;
	ar2DeleteHandle(&ar2Handle);
	kpmDeleteHandle(&kpmHandle);
	arParamLTFree(&gCParamLT);
	if(gVideoFrame){
		free(gVideoFrame);
		gVideoFrame = NULL;
		gVideoFrameSize = 0;
	}
	ar2VideoClose(gVid);
	gVid = NULL;
	videoInited = false;
	return true;
}

JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeDestroy(JNIEnv* env, jobject object)){
	if(markersNFT) deleteMarkers(&markersNFT, &markersNFTCount);
	return true;
}

// camera ==================================================================

JNIEXPORT jboolean JNICALL JNIFUNCTION_NATIVE(nativeVideoInit(JNIEnv* env, jobject object, jint w, jint h, jint cameraIndex, jboolean cameraIsFrontFacing)){
	gPixFormat = AR_PIXEL_FORMAT_NV21;
	gVideoFrameSize = (sizeof(ARUint8)*(w*h + 2*w / 2*h / 2));
	gVideoFrame = (ARUint8*) (malloc(gVideoFrameSize));
	if(!gVideoFrame) {
		gVideoFrameSize = 0;
		LOGE("Error allocating frame buffer\n");
		return false;
	}
	videoWidth = w;
	videoHeight = h;
	gCameraIndex = cameraIndex;
	gCameraIsFrontFacing = cameraIsFrontFacing;
	ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_WIDTH, videoWidth);
	ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_HEIGHT, videoHeight);
	ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_PIXELFORMAT, (int)gPixFormat);
	ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_CAMERA_INDEX, gCameraIndex);
	ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_CAMERA_FACE, gCameraIsFrontFacing);
	ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_INTERNET_STATE, gInternetState);
	if(ar2VideoGetCParamAsync(gVid, nativeVideoGetCparamCallback, NULL) < 0) {
		LOGE("error getting cparam\n");
		nativeVideoGetCparamCallback(NULL, NULL);
	}
	return true;
}

static void nativeVideoGetCparamCallback(const ARParam* cparam_p, void* userdata){
	ARParam cparam;
	if (cparam_p) cparam = *cparam_p;
	else {
		LOGE("Unable to automatically determine camera parameters - using default\n");
		if(arParamLoad(cparaName, 1, &cparam) < 0){
			LOGE("error: unable to load parameter file for camera");
			return;
		}
	}
	if (cparam.xsize != videoWidth || cparam.ysize != videoHeight) {
		LOGW("camera parameter resized from %d, %d\n", cparam.xsize, cparam.ysize);
		arParamChangeSize(&cparam, videoWidth, videoHeight, &cparam);
	}
	if((gCParamLT = arParamLTCreate(&cparam, AR_PARAM_LT_DEFAULT_OFFSET)) == NULL) {
		LOGE("error: arParamLTCreate\n");
		return;
	}
	videoInited = true;
	arglCameraFrustumRHf(&gCParamLT->param, NEAR_PLANE,FAR_PLANE, cameraLens);
	cameraPoseValid = FALSE;

	if(!initNFT(gCParamLT, gPixFormat)){
		LOGE("Error initializing NFT\n");
		arParamLTFree(&gCParamLT);
		return;
	}
	nftDataLoadingThreadHandle = threadInit(0, NULL, loadNFTDataAsync);
	if(!nftDataLoadingThreadHandle){
		LOGE("Error starting NFT loading thread\n");
		arParamLTFree(&gCParamLT);
		return;
	}
	threadStartSignal(nftDataLoadingThreadHandle);
}

static int initNFT(ARParamLT *cparamLT, AR_PIXEL_FORMAT pixFormat){
	kpmHandle = kpmCreateHandle(cparamLT, pixFormat);
	if(!kpmHandle) {
		LOGE("Error: kpmCreateHandle\n");
		return false;
	}
	if((ar2Handle = ar2CreateHandle(cparamLT, pixFormat, AR2_TRACKING_DEFAULT_THREAD_NUM)) == NULL ) {
		LOGE("error: ar2CreateHandle\n");
		kpmDeleteHandle(&kpmHandle);
		return false;
	}
	if(threadGetCPU() <= 1) {
		ar2SetTrackingThresh( ar2Handle, 5.0 );
		ar2SetSimThresh( ar2Handle, 0.50 );
		ar2SetSearchFeatureNum(ar2Handle, 16);
		ar2SetSearchSize(ar2Handle, 6);
		ar2SetTemplateSize1(ar2Handle, 6);
		ar2SetTemplateSize2(ar2Handle, 6);
	} else {
		ar2SetTrackingThresh( ar2Handle, 5.0 );
		ar2SetSimThresh( ar2Handle, 0.50 );
		ar2SetSearchFeatureNum(ar2Handle, 16);
		ar2SetSearchSize(ar2Handle, 12);
		ar2SetTemplateSize1(ar2Handle, 6);
		ar2SetTemplateSize2(ar2Handle, 6);
	}
	return true;
}

static void* loadNFTDataAsync(THREAD_HANDLE_T* threadHandle){
	int i, j;
	KpmRefDataSet* refDataSet;
	while(threadStartWait(threadHandle) == 0){
		if(trackingThreadHandle){
			LOGE("NFT2 tracking thread is running - stopping it first\n");
			trackingInitQuit(&trackingThreadHandle);
		}
		j = 0;
		for(i = 0; i < surfaceSetCount; i++){
			if(j== 0) LOGE("Unloading NFT tracking surfaces");
			ar2FreeSurfaceSet(&surfaceSet[i]);
			j++;
		}
		if(j > 0) LOGE("Unloaded %d NFT tracking surfaces\n", j);
		surfaceSetCount = 0;
		refDataSet = NULL;
		for(i = 0; i < markersNFTCount; i++){
			KpmRefDataSet* refDataSet2;
			LOGI("Reading %s.fset3\n", markersNFT[i].datasetPathname);
			if(kpmLoadRefDataSet(markersNFT[i].datasetPathname, "fset3", &refDataSet2) < 0) {
				LOGE("error loading KPM data\n");
				markersNFT[i].pageNo = -1;
				continue;
			}
			markersNFT[i].pageNo =  surfaceSetCount;
			LOGI("  Assigned page no. %d.\n", surfaceSetCount);
			if(kpmChangePageNoOfRefDataSet(refDataSet2, KpmChangePageNoAllPages, surfaceSetCount) < 0){
				LOGE("Error: kpmChangePageNoOfRefDataSet\n");
				exit(-1);
			}
			if(kpmMergeRefDataSet(&refDataSet, &refDataSet2) < 0){
				LOGE("Error: kpmMergeRefDataSet\n");
				exit(-1);
			}
			// Load AR2 data.
			LOGI("Reading %s.fset\n", markersNFT[i].datasetPathname);

			if ((surfaceSet[surfaceSetCount] = ar2ReadSurfaceSet(markersNFT[i].datasetPathname, "fset", NULL)) == NULL ) {
				LOGE("Error reading data from %s.fset\n", markersNFT[i].datasetPathname);
			}
			LOGI("  Done.\n");
			surfaceSetCount++;
			if(surfaceSetCount == PAGES_MAX) break;
		}
		if(kpmSetRefDataSet(kpmHandle, refDataSet) < 0) {
			exit(-1);
		}
		kpmDeleteRefDataSet(&refDataSet);
		trackingThreadHandle = trackingInitInit(kpmHandle);
		if(!trackingThreadHandle) exit(-1);
		threadEndSignal(threadHandle);
	}
	return NULL;
}

JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeVideoFrame(JNIEnv* env, jobject obj, jbyteArray pinArray)) {
	int i, j, k;
	jbyte* inArray;

	if(!videoInited){
		return; //no point in tracking if video has not been inited
	}

	if(!nftDataLoaded){
		if(!nftDataLoadingThreadHandle || threadGetStatus(nftDataLoadingThreadHandle) < 1) {
			return;
		} else {
			nftDataLoaded = true;
			threadWaitQuit(nftDataLoadingThreadHandle);
			threadFree(&nftDataLoadingThreadHandle);
		}
	}
	if(!gARViewInited) {
		return; // no point in tracking if no AR view
	}

	env->GetByteArrayRegion(pinArray, 0, gVideoFrameSize, (jbyte*) gVideoFrame);
	videoFrameNeedsPixelBufferDataUpload = true;

	if(trackingThreadHandle){
		float err;
		int ret;
		int pageNo;
		if(detectedPage == -2){
			trackingInitStart(trackingThreadHandle, gVideoFrame);
			detectedPage = -1;
		}
		if(detectedPage == -1){
			ret = trackingInitGetResult(trackingThreadHandle, trackingTrans, &pageNo);
			if(ret == 1){
				if(pageNo >= 0 && pageNo < surfaceSetCount) {
					detectedPage = pageNo;
					ar2SetInitTrans(surfaceSet[detectedPage], trackingTrans);
				} else {
					LOGE("Detected bad page\n");
					detectedPage = -2;
				}
			} else if (ret < 0) {
				detectedPage = -2;
			}
		}
		if(detectedPage >= 0 && detectedPage < surfaceSetCount) {
			// try to track
			if(ar2Tracking(ar2Handle, surfaceSet[detectedPage], gVideoFrame, trackingTrans, &err) < 0){
				// tracking lost
				detectedPage = -2;
			}
		}

	} else {
		LOGE("error: trackingThreadHandle\n");
		detectedPage = -2;
	}

	// update markers

	for(i = 0; i < markersNFTCount; i++){
		markersNFT[i].validPrev = markersNFT[i].valid;
		if(markersNFT[i].pageNo >= 0 && markersNFT[i].pageNo == detectedPage) {
			markersNFT[i].valid = TRUE;
			for(j = 0; j < 3; j++){
				for(k = 0; k < 4; k++) markersNFT[i].trans[j][k] = trackingTrans[j][k];
			}
		} else markersNFT[i].valid = FALSE;
		if(markersNFT[i].valid) {
			//filter pose estimate
			if(markersNFT[i].ftmi){
				if(arFilterTransMat(markersNFT[i].ftmi, markersNFT[i].trans, !markersNFT[i].validPrev) < 0) {
					LOGE("arFilterTransMat error with marker %d\n", i);
				}
			}
			if(!markersNFT[i].validPrev) {
              // marker has become visible
			}
			// set new pose
			arglCameraViewRHf(markersNFT[i].trans, markersNFT[i].pose.T, 1.0f);
		} else {
			if(markersNFT[i].validPrev) {
				// marker is no longer visible
			}
		}
	}
}

// opengl ================================================================================

/*
 *  nativeSurfaceCreated
 */
JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeSurfaceCreated(JNIEnv* env, jobject object)) {
	glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	glStateCacheFlush();
	if(gArglSettings) {
		arglCleanup(gArglSettings);
		gArglSettings = NULL;
	}
	gARViewInited = false;
}

/*
 *  nativeSurfaceChanged
 */
JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeSurfaceChanged(JNIEnv* env, jobject object, jint w, jint h)){
	backingWidth = w;
	backingWidth = h;
	gARViewLayoutRequired = true;
}

/*
 * nativeDisplayParametersChanged
 */
JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeDisplayParametersChanged(JNIEnv* env, jobject object, jint orientation, jint width, jint height, jint dpi)) {
	gDisplayOrientation = orientation;
	gDisplayWidth = width;
	gDisplayHeight = height;
	gDisplayDPI = dpi;
	gARViewLayoutRequired = true;
}

/*
 * nativeSetInternetState
 */
JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeSetInternetState(JNIEnv* env, jobject obj, jint state)) {
	gInternetState = state;
	if(gVid) {
		ar2VideoSetParami(gVid, AR_VIDEO_PARAM_ANDROID_INTERNET_STATE, state);
	}
}

static bool layoutARView(void){
	if (gDisplayOrientation == 0) {
			gContentRotate90 = true;
			gContentFlipV = false;
			gContentFlipH = gCameraIsFrontFacing;
		} else if (gDisplayOrientation == 1) {
			gContentRotate90 = false;
			gContentFlipV = false;
			gContentFlipH = gCameraIsFrontFacing;
		} else if (gDisplayOrientation == 2) {
			gContentRotate90 = true;
			gContentFlipV = true;
			gContentFlipH = (!gCameraIsFrontFacing);
		} else if (gDisplayOrientation == 3) {
			gContentRotate90 = false;
			gContentFlipV = true;
			gContentFlipH = (!gCameraIsFrontFacing);
		}
	    arglSetRotate90(gArglSettings, gContentRotate90);
	    arglSetFlipV(gArglSettings, gContentFlipV);
	    arglSetFlipH(gArglSettings, gContentFlipH);

	    int left, bottom, w, h;
	    int contentWidth = videoWidth;
	    int contentHeight = videoHeight;
	    if(gContentMode == ARViewContentModeScaleToFill) {
	    	w = backingWidth;
	    	h = backingHeight;
	    } else {
	    	int contentWidthFinalOrientation = (gContentRotate90 ? contentHeight : contentWidth);
	    	int contentHeightFinalOrientation = (gContentRotate90 ? contentWidth : contentHeight);
			if (gContentMode == ARViewContentModeScaleAspectFit || gContentMode == ARViewContentModeScaleAspectFill) {
				float scaleRatioWidth, scaleRatioHeight, scaleRatio;
				scaleRatioWidth = (float)backingWidth / (float)contentWidthFinalOrientation;
				scaleRatioHeight = (float)backingHeight / (float)contentHeightFinalOrientation;
				if (gContentMode == ARViewContentModeScaleAspectFill) scaleRatio = MAX(scaleRatioHeight, scaleRatioWidth);
				else scaleRatio = MIN(scaleRatioHeight, scaleRatioWidth);
				w = (int)((float)contentWidthFinalOrientation * scaleRatio);
				h = (int)((float)contentHeightFinalOrientation * scaleRatio);
			} else {
				w = contentWidthFinalOrientation;
				h = contentHeightFinalOrientation;
			}
		}

		if (gContentMode == ARViewContentModeTopLeft
			|| gContentMode == ARViewContentModeLeft
			|| gContentMode == ARViewContentModeBottomLeft) left = 0;
		else if (gContentMode == ARViewContentModeTopRight
				 || gContentMode == ARViewContentModeRight
				 || gContentMode == ARViewContentModeBottomRight) left = backingWidth - w;
		else left = (backingWidth - w) / 2;

		if (gContentMode == ARViewContentModeBottomLeft
			|| gContentMode == ARViewContentModeBottom
			|| gContentMode == ARViewContentModeBottomRight) bottom = 0;
		else if (gContentMode == ARViewContentModeTopLeft
				 || gContentMode == ARViewContentModeTop
				 || gContentMode == ARViewContentModeTopRight) bottom = backingHeight - h;
		else bottom = (backingHeight - h) / 2;

		glViewport(left, bottom, w, h);

		viewPort[viewPortIndexLeft] = left;
		viewPort[viewPortIndexBottom] = bottom;
		viewPort[viewPortIndexWidth] = w;
		viewPort[viewPortIndexHeight] = h;
		// Call through to anyone else who needs to know about changes in the ARView layout here.
		// --->

		gARViewLayoutRequired = false;

		return (true);
}

static bool initARView(void){
	if(gARViewInited) return false;
	if((gArglSettings = arglSetupForCurrentContext(&gCParamLT->param, gPixFormat)) == NULL) {
		LOGE("Unable to set up argl\n");
		return false;
	}
	// load obj
	models[0].obj = glmReadOBJ2(modelFile, 0, 0); // context 0, don't read textures yet.
	if (!models[0].obj) {
		LOGE("Error loading model from file '%s'.", model0file);
		exit(-1);
	}
	glmScale(models[0].obj, 0.035f);
	glmCreateArrays(models[0].obj, GLM_SMOOTH | GLM_MATERIAL | GLM_TEXTURE);

	gARViewInited = true;
	return true;
}

void drawModel (int i, float size, float x, float y, float z) {
	glPushMatrix();
	glTranslatef(x, y, z);
	glScalef(size, size, size);

	glLightfv(GL_LIGHT0, GL_AMBIENT, lightAmbient);
	glLightfv(GL_LIGHT0, GL_DIFFUSE, lightDiffuse);
	glLightfv(GL_LIGHT0, GL_POSITION, lightPosition);

	glmDrawArrays(models[i].obj, 0);
	glPopMatrix();
}

/*
 * nativeDrawFrame
 */
JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeDrawFrame(JNIEnv* env, jobject obj)){
	int width;
	int height;
	if(!videoInited){
		return;
	}
	if(!gARViewInited) {
		if(!initARView()) return;
	}
	if(videoFrameNeedsPixelBufferDataUpload) {
		arglPixelBufferDataUploadBiPlanar(gArglSettings, gVideoFrame, gVideoFrame + videoWidth*videoHeight);
		videoFrameNeedsPixelBufferDataUpload = false;
	}
//	glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	arglDispImage(gArglSettings);

	glMatrixMode(GL_PROJECTION);
	glLoadMatrixf(cameraLens);
	glMatrixMode(GL_MODELVIEW);
	glLoadIdentity();
	glStateCacheEnableDepthTest();
	glStateCacheEnableLighting();
	glEnable(GL_LIGHT0);

	for (int i = 0; i < markersNFTCount; i++) {
		if (markersNFT[i].valid) {
			glLoadMatrixf(markersNFT[i].pose.T);
			drawModel(markersNFT[i].id, 1.0f, 0.0f, 0.0f, 20.0f);
		}
	}

	if(cameraPoseValid) {
		glMultMatrixf(cameraPose);
	}

	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();
	width = (float)viewPort[viewPortIndexWidth];
	height = (float)viewPort[viewPortIndexHeight];
	glOrthof(0.0f, width, 0.0f, height, -1.0f, 1.0f);
	glMatrixMode(GL_MODELVIEW);
	glLoadIdentity();
	glStateCacheDisableDepthTest();
}

/*
 * nativeLoadModel
 */
JNIEXPORT void JNICALL JNIFUNCTION_NATIVE(nativeLoadModel(JNIEnv* env, jobject obj, jstring objPath, jstring mtlPath, jstring texPath)){
	modelFile = env->getStringUTFChars(objPath, 0);
	gARViewInited = false;
}


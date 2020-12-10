package com.github.aleneum.WebbedView.tracking;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.github.aleneum.WebbedView.R;
import com.github.aleneum.WebbedView.VuforiaApplicationControl;
import com.github.aleneum.WebbedView.VuforiaApplicationException;
import com.github.aleneum.WebbedView.VuforiaApplicationSession;
import com.github.aleneum.WebbedView.ui.AppMenu.AppMenu;
import com.github.aleneum.WebbedView.ui.AppMenu.SampleAppMenuGroup;
import com.github.aleneum.WebbedView.ui.AppMenu.SampleAppMenuInterface;
import com.github.aleneum.WebbedView.ui.SampleAppMessage;
import com.github.aleneum.WebbedView.utils.ApplicationGLView;
import com.github.aleneum.WebbedView.utils.DataManagement;
import com.github.aleneum.WebbedView.utils.DataManagementListener;
import com.github.aleneum.WebbedView.utils.GithubDataManagement;
import com.github.aleneum.WebbedView.utils.LoadingDialogHandler;
import com.github.aleneum.WebbedView.utils.Timer;
import com.github.aleneum.WebbedView.views.CircleView;
import com.github.aleneum.WebbedView.views.CustomWebView;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.DeviceTracker;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableList;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ImageTargets extends Activity implements VuforiaApplicationControl,
    SampleAppMenuInterface, DataManagementListener
{
    private static final String LOGTAG = "ImageTargets";

    private VuforiaApplicationSession vuforiaAppSession;
    
    private DataSet mCurrentDataset;
    private int mCurrentDatasetSelectionIndex = 0;
    private int mStartDatasetsIndex = 0;
    private int mDatasetsNumber = 0;
    private final ArrayList<String> mDatasetStrings = new ArrayList<>();

    private ApplicationGLView mGlView;
    private AppRenderer mRenderer;
    private GestureDetector mGestureDetector;


    // Menu option flags
    private boolean mSwitchDatasetAsap = false;
    private boolean mFlash = false;
    private boolean mContAutofocus = true;
    private boolean mDeviceTracker = false;

    private View mFocusOptionView;
    private View mFlashOptionView;
    
    private RelativeLayout mUILayout;
    
    private AppMenu mAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();

    private SampleAppMessage mSampleAppMessage;
    private Timer mRelocalizationTimer;
    private Timer mStatusDelayTimer;

    private int mCurrentStatusInfo;

    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private boolean mIsDroidDevice = false;
    private CustomWebView mWebView;
    private String mCurrentTrackable = "";
    private JSONObject mConfig;
    private boolean mUpdateProjectionEnabled = true;
    private DataManagement dataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        dataManager = GithubDataManagement.getInstance(this);
        vuforiaAppSession = new VuforiaApplicationSession(this);

        startLoadingAnimation();

        mWebView = findViewById(R.id.webview);
        mWebView.mActivity = this;
        dataManager.initialize(this);
    }

    @Override
    public void onDataManagementInitialized(DataManagement manager) {
        try {
            mConfig = dataManager.getConfig();
            JSONArray definitions = mConfig.getJSONArray("targetDefinitions");
            for (int i = 0; i < definitions.length(); ++i) {
                String fileName = definitions.getString(i);
                mDatasetStrings.add(fileName);
                Log.d(LOGTAG, "Adding: " + fileName);
            }
        } catch (JSONException err) {
            Log.e(LOGTAG, err.getMessage());
        }
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        mGestureDetector = new GestureDetector(getApplicationContext(), new GestureListener(this));
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");

        // Relocalization timer and message
        mSampleAppMessage = new SampleAppMessage(this, mUILayout);
        mRelocalizationTimer = new Timer(10000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (vuforiaAppSession != null)
                {
                    vuforiaAppSession.resetDeviceTracker();
                }

                super.onFinish();
            }
        };

        mStatusDelayTimer = new Timer(1000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (!mRelocalizationTimer.isRunning())
                {
                    mRelocalizationTimer.startTimer();
                }

                runOnUiThread(() -> mSampleAppMessage.show(getString(R.string.instruct_relocalize)));

                super.onFinish();
            }
        };
    }


    public void updateProjection(float[] matrix, String trackableName) {
        Log.v(LOGTAG, "new tracking result");
        if (! mUpdateProjectionEnabled) {
            return;
        }
        if (! mCurrentTrackable.equalsIgnoreCase(trackableName)) {
            updateWebView(trackableName);
        }

        if (mWebView.isLoaded()) {
            Log.v(LOGTAG, "update webview");
            this.mWebView.updateWebViewTransform(matrix);
        }
    }

    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();

        private WeakReference<ImageTargets> activityRef;
        

        private GestureListener(ImageTargets activity)
        {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        // Process Single Tap event to trigger autofocus
//        @Override
//        public boolean onSingleTapUp(MotionEvent e)
//        {
//            Log.d(LOGTAG, "SingleTap!");
//
//            boolean result = CameraDevice.getInstance().setFocusMode(
//                    CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
//            if (!result)
//                Log.e("SingleTapUp", "Unable to trigger focus");
//
//            // Generates a Handler to trigger continuous auto-focus
//            // after 1 second
//            autofocusHandler.postDelayed(new Runnable() {
//                public void run()
//                {
//                    if (activityRef.get().mContAutofocus)
//                    {
//                        final boolean autofocusResult = CameraDevice.getInstance().setFocusMode(
//                                CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);
//
//                        if (!autofocusResult)
//                            Log.e("SingleTapUp", "Unable to re-enable continuous auto-focus");
//                    }
//                }
//            }, 1000L);
//
//            return true;
//        }
    }

    @Override
    protected void onResume() {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        vuforiaAppSession.onResume();
    }


    // Called whenever the device orientation or screen resolution changes
    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
    }


    @Override
    protected void onPause() {
        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        // Turn off the flash
        if (mFlashOptionView != null && mFlash)
        {
            // OnCheckedChangeListener is called upon changing the checked state
            setMenuToggle(mFlashOptionView, false);
        }
        
        vuforiaAppSession.onPause();
    }
    

    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (VuforiaApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        System.gc();
    }
    

    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new ApplicationGLView(getApplicationContext());
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new AppRenderer(this, vuforiaAppSession);
        mGlView.setRenderer(mRenderer);
    }
    

    private void startLoadingAnimation() {
        mUILayout = (RelativeLayout) View.inflate(getApplicationContext(), R.layout.camera_overlay, null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        
        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
        
    }
    

    @Override
    public boolean doLoadTrackersData() {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            return false;
        }
        
        if (mCurrentDataset == null) {
            mCurrentDataset = objectTracker.createDataSet();
        }
        
        if (mCurrentDataset == null) {
            return false;
        }
        String filePath = getFilesDir() + "/" + mDatasetStrings.get(mCurrentDatasetSelectionIndex);
        Log.d(LOGTAG,  filePath);
        // check for resources; assets will override downloaded files
        if (!mCurrentDataset.load(mDatasetStrings.get(mCurrentDatasetSelectionIndex),
                STORAGE_TYPE.STORAGE_APPRESOURCE) && !mCurrentDataset.load(filePath,
                STORAGE_TYPE.STORAGE_ABSOLUTE)) {
            return false;
        }
        
        if (!objectTracker.activateDataSet(mCurrentDataset)) {
            return false;
        }
        
        TrackableList trackableList = mCurrentDataset.getTrackables();
        for (Trackable trackable : trackableList) {
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data "
                + trackable.getUserData());
        }
        
        return true;
    }
    
    
    @Override
    public boolean doUnloadTrackersData() {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null)
        {
            return false;
        }
        
        if (mCurrentDataset != null && mCurrentDataset.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(mCurrentDataset)
                && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            }
            else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }
            
            mCurrentDataset = null;
        }
        
        return result;
    }


    @Override
    public void onVuforiaResumed() {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }


    @Override
    public void onVuforiaStarted() {
        mRenderer.updateRenderingPrimitives();
        mRenderer.updateConfiguration();

        if (mContAutofocus)
        {
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
            {
                // If continuous autofocus mode fails, attempt to set to a different mode
                if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
                {
                    CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
                }

                setMenuToggle(mFocusOptionView, false);
            }
            else
            {
                setMenuToggle(mFocusOptionView, true);
            }
        }
        else
        {
            setMenuToggle(mFocusOptionView, false);
        }

        showProgressIndicator(false);
    }


    private void showProgressIndicator(boolean show) {
        if (show)
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        }
        else
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }


    // Called once Vuforia has been initialized or
    // an error has caused Vuforia initialization to stop
    @Override
    public void onInitARDone(VuforiaApplicationException exception) {
        if (exception == null)
        {
            initApplicationAR();
            
            mRenderer.setActive(true);
            
            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            mAppMenu = new AppMenu(this, this, "Image Targets",
                    mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();

            vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_BACK);
        }
        else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }
    

    private void showInitializationErrorMessage(String message) {
        final String errorMessage = message;
        runOnUiThread(() -> {
            if (mErrorDialog != null)
            {
                mErrorDialog.dismiss();
            }

            // Generates an Alert Dialog to show the error message
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    ImageTargets.this);
            builder
                .setMessage(errorMessage)
                .setTitle(getString(R.string.INIT_ERROR))
                .setCancelable(false)
                .setIcon(0)
                .setPositiveButton(getString(R.string.button_OK), (dialog, id) -> finish());

            mErrorDialog = builder.create();
            mErrorDialog.show();
        });
    }


    // Called every frame
    @Override
    public void onVuforiaUpdate(State state) {
        if (mSwitchDatasetAsap)
        {
            mSwitchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker
                .getClassType());
            if (ot == null || mCurrentDataset == null
                || ot.getActiveDataSets().at(0) == null)
            {
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
            }
            
            doUnloadTrackersData();
            doLoadTrackersData();
        }
    }
    
    
    @Override
    public boolean doInitTrackers() {
        // Indicate if the trackers were initialized correctly
        boolean result = true;
         
        TrackerManager tManager = TrackerManager.getInstance();

        Tracker tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        // Initialize the Positional Device Tracker
        DeviceTracker deviceTracker = (PositionalDeviceTracker)
                tManager.initTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null)
        {
            Log.i(LOGTAG, "Successfully initialized Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to initialize Device Tracker");
        }

        return result;
    }
    
    
    @Override
    public boolean doStartTrackers() {
        // Indicate if the trackers were started correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null && objectTracker.start())
        {
            Log.i(LOGTAG, "Successfully started Object Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to start Object Tracker");
            result = false;
        }

        if (isDeviceTrackingActive()) {
            PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager
                    .getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null && deviceTracker.start())
            {
                Log.i(LOGTAG, "Successfully started Device Tracker");
            }
            else
            {
                Log.e(LOGTAG, "Failed to start Device Tracker");
            }
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
        {
            objectTracker.stop();
            Log.i(LOGTAG, "Successfully stopped object tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to stop object tracker");
            result = false;
        }

        // Stop the device tracker
        if(isDeviceTrackingActive())
        {

            Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null)
            {
                deviceTracker.stop();
                Log.i(LOGTAG, "Successfully stopped device tracker");
            }
            else
            {
                Log.e(LOGTAG, "Could not stop device tracker");
            }
        }

        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();

        // Indicate if the trackers were deinitialized correctly
        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());
        
        return result;
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        Log.d(LOGTAG, "TOUCHED!");
        // Process the Gestures
        return ((mAppMenu != null && mAppMenu.processEvent(event))
                || mGestureDetector.onTouchEvent(event));
    }
    
    
    boolean isDeviceTrackingActive()
    {
        return mDeviceTracker;
    }

    // Menu options
    private final static int CMD_BACK = -1;
    private final static int CMD_DEVICE_TRACKING = 1;
    private final static int CMD_UPDATE_TRACKING = 2;
    private final static int CMD_RELOAD_WEBVIEW = 3;

    private final static int CMD_AUTOFOCUS = 4;
    private final static int CMD_FLASH = 5;
    private final static int CMD_DATASET_START_INDEX = 6;

    private void setSampleAppMenuSettings() {
        SampleAppMenuGroup group;
        
        group = mAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);
        group.addTextItem(getString(R.string.menu_reload_webview), CMD_RELOAD_WEBVIEW);

        group = mAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_device_tracker), CMD_DEVICE_TRACKING, false);
        group.addSelectionItem(getString(R.string.menu_update_view), CMD_UPDATE_TRACKING, true);

        group = mAppMenu.addGroup(getString(R.string.menu_camera), true);
        mFocusOptionView = group.addSelectionItem(getString(R.string.menu_contAutofocus),
            CMD_AUTOFOCUS, mContAutofocus);
        mFlashOptionView = group.addSelectionItem(
            getString(R.string.menu_flash), CMD_FLASH, false);

        mStartDatasetsIndex = CMD_DATASET_START_INDEX;
        mDatasetsNumber = mDatasetStrings.size();
        mAppMenu.attachMenu();
    }


    private void setMenuToggle(View view, boolean value) {
        ((Switch) view).setChecked(value);
    }


    // In this function you can define the desired behavior for each menu option
    // Each case corresponds to a menu option
    @Override
    public boolean menuProcess(int command) {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;
            
            case CMD_FLASH:
                result = CameraDevice.getInstance().setFlashTorchMode(!mFlash);
                
                if (result)
                {
                    mFlash = !mFlash;
                } else
                {
                    showToast(getString(mFlash ? R.string.menu_flash_error_off
                        : R.string.menu_flash_error_on));
                    Log.e(LOGTAG,
                        getString(mFlash ? R.string.menu_flash_error_off
                            : R.string.menu_flash_error_on));
                }
                break;
            
            case CMD_AUTOFOCUS:
                
                if (mContAutofocus)
                {
                    result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
                    
                    if (result)
                    {
                        mContAutofocus = false;
                    } else
                    {
                        showToast(getString(R.string.menu_contAutofocus_error_off));
                        Log.e(LOGTAG,
                            getString(R.string.menu_contAutofocus_error_off));
                    }
                } else
                {
                    result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);
                    
                    if (result)
                    {
                        mContAutofocus = true;
                    } else
                    {
                        showToast(getString(R.string.menu_contAutofocus_error_on));
                        Log.e(LOGTAG,
                            getString(R.string.menu_contAutofocus_error_on));
                    }
                }
                
                break;
            
            case CMD_DEVICE_TRACKING:

                result = toggleDeviceTracker();
                break;

            case CMD_UPDATE_TRACKING:

                mUpdateProjectionEnabled = !mUpdateProjectionEnabled;
                break;

            case CMD_RELOAD_WEBVIEW:
                updateWebView(mCurrentTrackable);
                break;

            default:
                if (command >= mStartDatasetsIndex
                    && command < mStartDatasetsIndex + mDatasetsNumber) {
                    mSwitchDatasetAsap = true;
                    mCurrentDatasetSelectionIndex = command
                        - mStartDatasetsIndex;
                }
                break;
        }
        
        return result;
    }


    private boolean toggleDeviceTracker() {
        boolean result = true;
        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                trackerManager.getTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null) {
            if (!mDeviceTracker) {
                if (!deviceTracker.start()) {
                    Log.e(LOGTAG,"Failed to start device tracker");
                    result = false;
                } else {
                    Log.d(LOGTAG,"Successfully started device tracker");
                }
            } else {
                deviceTracker.stop();
                Log.d(LOGTAG, "Successfully stopped device tracker");
                clearSampleAppMessage();
            }
        } else {
            Log.e(LOGTAG, "Device tracker is null!");
            result = false;
        }

        if (result) {
            mDeviceTracker = !mDeviceTracker;
        } else {
            clearSampleAppMessage();
        }

        return result;
    }


    public void checkForRelocalization(final int statusInfo) {
        if (mCurrentStatusInfo == statusInfo) {
            return;
        }

        mCurrentStatusInfo = statusInfo;

        if (mCurrentStatusInfo == TrackableResult.STATUS_INFO.RELOCALIZING) {
            // If the status is RELOCALIZING, start the timer
            if (!mStatusDelayTimer.isRunning()) {
                mStatusDelayTimer.startTimer();
            }
        } else  {
            // If the status is not RELOCALIZING, stop the timers and hide the message
            if (mStatusDelayTimer.isRunning()) {
                mStatusDelayTimer.stopTimer();
            }

            if (mRelocalizationTimer.isRunning()) {
                mRelocalizationTimer.stopTimer();
            }
            clearSampleAppMessage();
        }
    }


    private void clearSampleAppMessage() {
        runOnUiThread(() -> { if (mSampleAppMessage != null) { mSampleAppMessage.hide(); }});
    }
    
    
    private void showToast(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }


    public void debugWebViewMatrix(List<float[]> points) {

        CircleView view = findViewById(R.id.circles);
        view.mPoints = points;

        String pointString = points.stream().map(Arrays::toString)
                                   .collect(Collectors.joining(","));

        //            mWebView.evaluateJavascript("showPoints([" + pointString + "])", null);
        runOnUiThread(view::invalidate);

//        runOnUiThread(() -> {
//            view.invalidate();
//            mWebView.evaluateJavascript("showPoints([" + pointString + "])", null);
//        });
    }

    private void updateWebView(String trackableName) {
        try {
            JSONObject trackable = mConfig.getJSONObject("targets").getJSONObject(trackableName);
            String url =  trackable.getString("url");
            JSONArray scalingParams = mConfig.getJSONArray("defaultScaling");
            if (trackable.has("scaling")) {
                scalingParams = trackable.getJSONArray("scaling");
            }
            float [] params = {(float) scalingParams.getDouble(0), (float) scalingParams.getDouble(1),
                               (float) scalingParams.getDouble(2), (float) scalingParams.getDouble(3)};
            mWebView.setProjectionScaling(params);
            mCurrentTrackable = trackableName;
            Log.d(LOGTAG, "Changed current target to " + mCurrentTrackable);
            mWebView.getRemoteContent(url, trackable.optString("elementId", null));
        } catch (JSONException err) {
            Log.e(LOGTAG, err.getMessage());
        }
    }
}
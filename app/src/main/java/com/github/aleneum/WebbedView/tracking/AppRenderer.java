package com.github.aleneum.WebbedView.tracking;

import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.github.aleneum.WebbedView.VuforiaApplicationSession;
import com.github.aleneum.WebbedView.gl.Utils;
import com.github.aleneum.WebbedView.gl.VideoBackgroundShader;
import com.github.aleneum.WebbedView.utils.LoadingDialogHandler;
import com.github.aleneum.WebbedView.utils.MathUtils;
import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.GLTextureUnit;
import com.vuforia.ImageTargetResult;
import com.vuforia.InstanceId;
import com.vuforia.Matrix34F;
import com.vuforia.Matrix44F;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.TrackerManager;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.VIEW;
import com.vuforia.Vec2F;
import com.vuforia.Vec2I;
import com.vuforia.Vec4I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.ViewList;
import com.vuforia.VuMarkTarget;
import com.vuforia.VuMarkTargetResult;
import com.vuforia.VuMarkTemplate;
import com.vuforia.Vuforia;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class AppRenderer implements GLSurfaceView.Renderer {
    private static final String LOGTAG = "AppRenderer";
    private static final float VIRTUAL_FOV_Y_DEGS = 85.0f;
    private static final float M_PI = 3.14159f;

    private static final boolean isStereo = false;
    private static final float mNearPlane = 0.01f;
    private static final float mFarPlane = 5f;
    private static final int mDeviceMode = Device.MODE.MODE_AR;
    private static final float VUMARK_SCALE = 1.02f;
    private final VuforiaApplicationSession vuforiaAppSession;

    private RenderingPrimitives mRenderingPrimitives = null;
    private WeakReference<ImageTargets> mActivityRef;

    private Renderer mRenderer;
    private int currentView = VIEW.VIEW_SINGULAR;


    private GLTextureUnit videoBackgroundTex = null;

    // Shader user to render the video background on AR mode
    private int vbShaderProgramID = 0;
    private int vbTexSampler2DHandle = 0;
    private int vbVertexHandle = 0;
    private int vbTexCoordHandle = 0;
    private int vbProjectionMatrixHandle = 0;

    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // Stores orientation
    private boolean mIsPortrait = false;
    private boolean mInitialized = false;
    private boolean mIsActive = false;

    AppRenderer(ImageTargets activity, VuforiaApplicationSession session) {
        mActivityRef = new WeakReference<>(activity);
        mRenderer = Renderer.getInstance();
        vuforiaAppSession = session;

        Device device = Device.getInstance();
        device.setViewerActive(isStereo);  // Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.setMode(mDeviceMode);  // Select if we will be in AR or VR mode
    }

    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");
        vuforiaAppSession.onSurfaceCreated();
        initRendering();
    }

    // Called when the surface changes size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");
        vuforiaAppSession.onSurfaceChanged(width, height);
        onConfigurationChanged(mIsActive);
        initRendering();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!mIsActive) { return; }
        this.render();
    }

    void setActive(boolean active) {
        mIsActive = active;

        if(mIsActive)
            this.configureVideoBackground();
    }


    void updateConfiguration() {
        this.onConfigurationChanged(mIsActive);
    }

    private void onConfigurationChanged(boolean isARActive) {
        if(mInitialized) { return; }

        updateActivityOrientation();
        storeScreenDimensions();

        if(isARActive) {           configureVideoBackground();
        }

        updateRenderingPrimitives();
        mInitialized = true;
    }


    synchronized void updateRenderingPrimitives() {
        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }

    // Initializes shader
    private void initRendering() {
        vbShaderProgramID = Utils.createProgramFromShaderSrc(VideoBackgroundShader.VB_VERTEX_SHADER,
                VideoBackgroundShader.VB_FRAGMENT_SHADER);

        // Rendering configuration for video background
        if (vbShaderProgramID > 0) {
            // Activate shader:
            GLES20.glUseProgram(vbShaderProgramID);

            // Retrieve handler for texture sampler shader uniform variable:
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Retrieve handler for projection matrix shader uniform variable:
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");

            vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexPosition");
            vbTexCoordHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexTexCoord");
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Stop using the program
            GLES20.glUseProgram(0);
        }

        videoBackgroundTex = new GLTextureUnit();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

        mActivityRef.get().loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }

    public void render() {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        State state;
        state = TrackerManager.getInstance().getStateUpdater().updateState();
        mRenderer.begin(state);

        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW);  // Front camera. NOTE: Front camera Vuforia Engine support has been deprecated
        else
            GLES20.glFrontFace(GLES20.GL_CCW);  // Back camera

        ViewList viewList = mRenderingPrimitives.getRenderingViews();

        // Cycle through the view list
        for (int v = 0; v < viewList.getNumViews(); v++) {
            // Get the view id
            int viewID = viewList.getView(v);

            // Get the viewport for that specific view
            Vec4I viewport;
            viewport = mRenderingPrimitives.getViewport(viewID);

            GLES20.glViewport(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);
            GLES20.glScissor(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Get projection matrix for the current view.
            Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(viewID, state.getCameraCalibration());

            // Create GL matrix setting up the near and far planes
            float rawProjectionMatrixGL[] = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix, mNearPlane, mFarPlane).getData();

            // Apply the appropriate eye adjustment to the raw projection matrix, and assign to the global variable
            float eyeAdjustmentGL[] = Tool.convert2GLMatrix(
                    mRenderingPrimitives.getEyeDisplayAdjustmentMatrix(viewID)).getData();

            // Apply the adjustment to the projection matrix
            float projectionMatrix[] = new float[16];
            Matrix.multiplyMM(projectionMatrix, 0, rawProjectionMatrixGL, 0, eyeAdjustmentGL, 0);

            currentView = viewID;

            if(currentView != VIEW.VIEW_POSTPROCESS) { renderFrame(state, projectionMatrix); }
        }
        mRenderer.end();
    }

    private void renderVideoBackground(State state) {
        if(currentView == VIEW.VIEW_POSTPROCESS)
            return;
        int vbVideoTextureUnit = 0;
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);
        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex)) {
            Log.e(LOGTAG, "Unable to update video background texture");
            return;
        }
//        Log.e(LOGTAG, "Background updated");

        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(currentView)).getData();

        if (Device.getInstance().isViewerActive()) {
            float sceneScaleFactor = (float) getSceneScaleFactor(state.getCameraCalibration());
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(currentView);

        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(vbShaderProgramID);
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.getPositions().asFloatBuffer());
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.getUVs().asFloatBuffer());

        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(vbVertexHandle);
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle);

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0);

        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vbMesh.getNumTriangles() * 3, GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // Finally, we disable the vertex arrays
        GLES20.glDisableVertexAttribArray(vbVertexHandle);
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle);

        Utils.checkGLError("Rendering of the video background failed");
    }

    private void renderFrame(State state, float[] projectionMatrix) {
        // Renders video background replacing Renderer.DrawVideoBackground()
        renderVideoBackground(state);

        // Set the device pose matrix as identity
        Matrix44F devicePoseMatrix = MathUtils.Matrix44FIdentity();
        Matrix44F modelMatrix;

        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
        if (state.getDeviceTrackableResult() != null) {
            int statusInfo = state.getDeviceTrackableResult().getStatusInfo();
            int trackerStatus = state.getDeviceTrackableResult().getStatus();

            mActivityRef.get().checkForRelocalization(statusInfo);

            if (trackerStatus != TrackableResult.STATUS.NO_POSE) {
                modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());

                // We transpose here because Matrix44FInverse returns a transposed matrix
                devicePoseMatrix = MathUtils.Matrix44FTranspose(MathUtils.Matrix44FInverse(modelMatrix));
            }
        }

        TrackableResultList trackableResultList = state.getTrackableResults();
        for (TrackableResult result : trackableResultList) {
            float[] modelProjection = new float[16];
            float[] modelMatrixArray = Tool.convertPose2GLMatrix(result.getPose()).getData();
            String trackableName = "";
            if (result.isOfType(ImageTargetResult.getClassType())) {
                trackableName = result.getTrackable().getName();
            } else if (result.isOfType(VuMarkTargetResult.getClassType())) {
                VuMarkTarget vmTgt = (VuMarkTarget) result.getTrackable();
                VuMarkTemplate vmTmp = vmTgt.getTemplate();
                trackableName = instanceIdToValue(vmTgt.getInstanceId());
                Vec2F origin = vmTmp.getOrigin();
                float translX = -origin.getData()[0];
                float translY = -origin.getData()[1];
                Matrix.translateM(modelMatrixArray, 0, translX, translY, 0);

                // Scales the plane relative to the target
                float vumarkWidth = vmTgt.getSize().getData()[0];
                float vumarkHeight = vmTgt.getSize().getData()[1];
                Matrix.scaleM(modelMatrixArray, 0, vumarkWidth * VUMARK_SCALE,
                        vumarkHeight * VUMARK_SCALE, 1.0f);
            }
            Matrix.multiplyMM(modelProjection, 0, devicePoseMatrix.getData(), 0, modelMatrixArray,0);
            Matrix.multiplyMM(modelProjection, 0, projectionMatrix, 0, modelProjection, 0);
            mActivityRef.get().updateProjection(modelProjection, trackableName);
        }

    }


    // Returns scene scale factor primarily used for eye-wear devices
    private double getSceneScaleFactor(CameraCalibration cameraCalib) {
        if (cameraCalib == null) {
            Log.e(LOGTAG, "Cannot compute scene scale factor, camera calibration is invalid");
            return 0.0;
        }

        // Get the y-dimension of the physical camera field of view
        Vec2F fovVector = cameraCalib.getFieldOfViewRads();
        float cameraFovYRads = fovVector.getData()[1];

        // Get the y-dimension of the virtual camera field of view
        float virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180;
        return Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2);
    }

    // Configures the video mode and sets offsets for the camera's image
    private void configureVideoBackground()
    {
        int xSize, ySize;

        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(vuforiaAppSession.getVideoMode());

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setPosition(new Vec2I(0, 0));

        if (mIsPortrait) {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth) {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        } else {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight) {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);
    }


    private void storeScreenDimensions() {
        // Query display dimensions:
        Point size = new Point();

        mActivityRef.get().getWindowManager().getDefaultDisplay().getRealSize(size);

        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }


    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation() {
        Configuration config = mActivityRef.get().getResources().getConfiguration();

        switch (config.orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }

    private static final String hexTable = "0123456789abcdef";

    private String instanceIdToValue(InstanceId instanceId) {
        ByteBuffer instanceIdBuffer = instanceId.getBuffer();
        byte[] instanceIdBytes = new byte[instanceIdBuffer.remaining()];
        instanceIdBuffer.get(instanceIdBytes);

        String instanceIdStr;
        switch(instanceId.getDataType())
        {
            case InstanceId.ID_DATA_TYPE.STRING:
                instanceIdStr = new String(instanceIdBytes, Charset.forName("US-ASCII"));
                break;

            case InstanceId.ID_DATA_TYPE.BYTES:
                StringBuilder instanceIdStrBuilder = new StringBuilder();

                for (int i = instanceIdBytes.length - 1; i >= 0; i--)
                {
                    byte byteValue = instanceIdBytes[i];

                    instanceIdStrBuilder.append(hexTable.charAt((byteValue & 0xf0) >> 4));
                    instanceIdStrBuilder.append(hexTable.charAt(byteValue & 0x0f));
                }

                instanceIdStr = instanceIdStrBuilder.toString();
                break;

            case InstanceId.ID_DATA_TYPE.NUMERIC:
                BigInteger instanceIdNumeric = instanceId.getNumericValue();
                long instanceIdLong = instanceIdNumeric.longValue();
                instanceIdStr = Long.toString(instanceIdLong);
                break;

            default:
                return "Unknown";
        }

        return instanceIdStr;
    }

}

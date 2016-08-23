package com.desmond.squarecamera;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.io.IOException;
import java.util.List;

public abstract class CameraFragment extends Fragment implements SurfaceHolder.Callback, Camera.PictureCallback {

    private final String permissionCamera = Manifest.permission.CAMERA;

    public static final String TAG = CameraFragment.class.getSimpleName();

    public static final String CAMERA_ID_KEY = "camera_id";

    public static final String CAMERA_FLASH_KEY = "flash_mode";

    public static final String IMAGE_INFO = "image_info";

    private static final int PICTURE_SIZE_MAX_WIDTH = 1280;

    private static final int PREVIEW_SIZE_MAX_WIDTH = 640;

    private static final int REQUEST_CAMERA_PERMISSION = 133;

    private int mCameraID;

    private String mFlashMode;

    private Camera mCamera;

    private SquareCameraPreview mPreviewView;

    private SurfaceHolder mSurfaceHolder;

    private boolean mIsSafeToTakePhoto = false;

    private ImageParameters mImageParameters;

    private CameraOrientationListener mOrientationListener;

    private View topCoverView;
    private View bottomCoverView;

    private AsyncTask previewTask;

    public CameraFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mOrientationListener = new CameraOrientationListener(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore your state here because a double rotation with this fragment
        // in the backstack will cause improper state restoration
        // onCreate() -> onSavedInstanceState() instead of going through onCreateView()
        if (savedInstanceState == null) {
            mCameraID = getBackCameraID();
            mFlashMode = CameraSettingPreferences.getCameraFlashMode(getActivity());
            mImageParameters = new ImageParameters();
        } else {
            mCameraID = savedInstanceState.getInt(CAMERA_ID_KEY);
            mFlashMode = savedInstanceState.getString(CAMERA_FLASH_KEY);
            mImageParameters = savedInstanceState.getParcelable(IMAGE_INFO);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(getLayoutId(), container, false);
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOrientationListener.enable();

        requestCameraPermission();

        mPreviewView = (SquareCameraPreview) view.findViewById(R.id.camera_preview_view);
        mPreviewView.getHolder().addCallback(CameraFragment.this);

         topCoverView = view.findViewById(R.id.cover_top_view);
         bottomCoverView = view.findViewById(R.id.cover_bottom_view);

        mImageParameters.mIsPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        if (savedInstanceState == null) {
            ViewTreeObserver observer = mPreviewView.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mImageParameters.mPreviewWidth = mPreviewView.getWidth();
                    mImageParameters.mPreviewHeight = mPreviewView.getHeight();

                    mImageParameters.mCoverWidth = mImageParameters.mCoverHeight = mImageParameters.calculateCoverWidthHeight();
//                    resizeTopAndBtmCover(topCoverView, btnCoverView);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        mPreviewView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        mPreviewView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        } else {
            if (mImageParameters.isPortrait()) {
                topCoverView.getLayoutParams().height = mImageParameters.mCoverHeight;
                bottomCoverView.getLayoutParams().height = mImageParameters.mCoverHeight;
            } else {
                topCoverView.getLayoutParams().width = mImageParameters.mCoverWidth;
                bottomCoverView.getLayoutParams().width = mImageParameters.mCoverWidth;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
//        Log.d(TAG, "onSaveInstanceState");
        outState.putInt(CAMERA_ID_KEY, mCameraID);
        outState.putString(CAMERA_FLASH_KEY, mFlashMode);
        outState.putParcelable(IMAGE_INFO, mImageParameters);
        super.onSaveInstanceState(outState);
    }

    private void resizeTopAndBtmCover(final View topCover, final View bottomCover) {
        if (topCover == null || bottomCover == null) return;

        ResizeAnimation resizeTopAnimation
                = new ResizeAnimation(topCover, mImageParameters);
        resizeTopAnimation.setDuration(800);
        resizeTopAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        topCover.startAnimation(resizeTopAnimation);

        ResizeAnimation resizeBtmAnimation
                = new ResizeAnimation(bottomCover, mImageParameters);
        resizeBtmAnimation.setDuration(800);
        resizeBtmAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        bottomCover.startAnimation(resizeBtmAnimation);
    }

    private void getCamera(int cameraID) {
        try {
            mCamera = Camera.open(cameraID);
            mPreviewView.setCamera(mCamera);
        } catch (Exception e) {
            Log.d(TAG, "Can't open camera with id " + cameraID);
            e.printStackTrace();
        }
    }

    /**
     * Restart the camera preview
     */
    private void restartPreview() {
        if (mCamera != null) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
        }

        getCamera(mCameraID);
        startCameraPreviewWithDelay();
    }

    /**
     * Start the camera preview
     */
    private void startCameraPreview() {
        if (mCamera == null) return;

        determineDisplayOrientation();
        setupCamera();

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();

            setSafeToTakePhoto(true);
            setCameraFocusReady(true);
        } catch (IOException e) {
            Log.d(TAG, "Can't start camera preview due to IOException " + e);
            e.printStackTrace();
        }
    }

    private void startCameraPreviewWithDelay(){
        cancelPreviewTaskIfRunning();
        previewTask = new  PreviewTask().execute(1);
    }

    private void cancelPreviewTaskIfRunning(){
        if (previewTask != null) previewTask.cancel(true);
    }

    /**
     * Stop the camera preview
     */
    private void stopCameraPreview() {
        setSafeToTakePhoto(false);
        setCameraFocusReady(false);

        // Nulls out callbacks, stops face detection
        mCamera.stopPreview();
        mPreviewView.setCamera(null);
    }

    protected void setSafeToTakePhoto(final boolean isSafeToTakePhoto) {
        mIsSafeToTakePhoto = isSafeToTakePhoto;
    }

    private void setCameraFocusReady(final boolean isFocusReady) {
        if (this.mPreviewView != null) {
            mPreviewView.setIsFocusReady(isFocusReady);
        }
    }

    /**
     * Determine the current display orientation and rotate the camera preview accordingly
     */
    private void determineDisplayOrientation() {
        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(mCameraID, cameraInfo);

        // Clockwise rotation needed to align the window display to the natural position
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0: {
                degrees = 0;
                break;
            }
            case Surface.ROTATION_90: {
                degrees = 90;
                break;
            }
            case Surface.ROTATION_180: {
                degrees = 180;
                break;
            }
            case Surface.ROTATION_270: {
                degrees = 270;
                break;
            }
        }

        int displayOrientation;

        // CameraInfo.Orientation is the angle relative to the natural position of the device
        // in clockwise rotation (angle that is rotated clockwise from the natural position)
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            // Orientation is angle of rotation when facing the camera for
            // the camera image to match the natural orientation of the device
            displayOrientation = (cameraInfo.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
        }

        mImageParameters.mDisplayOrientation = displayOrientation;
        mImageParameters.mLayoutOrientation = degrees;

        mCamera.setDisplayOrientation(mImageParameters.mDisplayOrientation);
    }

    /**
     * Setup the camera parameters
     */
    private void setupCamera() {
        // Never keep a global parameters
        Camera.Parameters parameters = mCamera.getParameters();

        Size bestPreviewSize = determineBestPreviewSize(parameters);
        Size bestPictureSize = determineBestPictureSize(parameters);

        parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
        parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);


        // Set continuous picture focus, if it's supported
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }


        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes != null && flashModes.contains(mFlashMode)) {
            parameters.setFlashMode(mFlashMode);
            onFlashAvailableCheck(true);
        } else {
            onFlashAvailableCheck(false);
        }

        // Lock in the changes
        mCamera.setParameters(parameters);
    }

    private Size determineBestPreviewSize(Camera.Parameters parameters) {
        return determineBestSize(parameters.getSupportedPreviewSizes(), getPreviewSizeMaxWidth());
    }

    private Size determineBestPictureSize(Camera.Parameters parameters) {
        return determineBestSize(parameters.getSupportedPictureSizes(), getPictureSizeMaxWidth());
    }

    private Size determineBestSize(List<Size> sizes, int widthThreshold) {
        Size bestSize = null;
        Size size;
        int numOfSizes = sizes.size();
        for (int i = 0; i < numOfSizes; i++) {
            size = sizes.get(i);
            boolean isDesireRatio = (size.width / 4) == (size.height / 3);
            boolean isBetterSize = (bestSize == null) || size.width > bestSize.width;

            if (isDesireRatio && isBetterSize) {
                bestSize = size;
            }
        }

        if (bestSize == null) {
            Log.d(TAG, "cannot find the best camera size");
            return sizes.get(sizes.size() - 1);
        }

        return bestSize;
    }

    private int getFrontCameraID() {
        PackageManager pm = getActivity().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return CameraInfo.CAMERA_FACING_FRONT;
        }

        return getBackCameraID();
    }

    private int getBackCameraID() {
        return CameraInfo.CAMERA_FACING_BACK;
    }

    /**
     * Take a picture
     */
    public void takePicture() {

        if (mIsSafeToTakePhoto) {
            setSafeToTakePhoto(false);

            mOrientationListener.rememberOrientation();

            // Shutter callback occurs after the image is captured. This can
            // be used to trigger a sound to let the user know that image is taken
            Camera.ShutterCallback shutterCallback = null;

            // Raw callback occurs when the raw image data is available
            Camera.PictureCallback raw = null;

            // postView callback occurs when a scaled, fully processed
            // postView image is available.
            Camera.PictureCallback postView = null;

            // jpeg callback occurs when the compressed image is available
            mCamera.takePicture(shutterCallback, raw, postView, this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mCamera == null) {
            if (getView()!=null){
                getView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        restartPreview();
                    }
                }, 1);
            }
        }
    }

    @Override
    public void onStop() {
        mOrientationListener.disable();
        cancelPreviewTaskIfRunning();

        // stop the preview
        if (mCamera != null) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
        }

        CameraSettingPreferences.saveCameraFlashMode(getActivity(), mFlashMode);

        super.onStop();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;

        getCamera(mCameraID);
        startCameraPreviewWithDelay();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // The surface is destroyed with the visibility of the SurfaceView is set to View.Invisible
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) return;

        switch (requestCode) {
            case 1:
                Uri imageUri = data.getData();
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * A picture has been taken
     *
     * @param data
     * @param camera
     */
    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        int rotation = getPhotoRotation();
        onPictureTaken(data, getImageParameters().createCopy(), rotation);
        setSafeToTakePhoto(true);
    }

    private ImageParameters getImageParameters(){
        return mImageParameters;
    }

    protected int getPhotoRotation() {
        int rotation;
        int orientation = mOrientationListener.getRememberedNormalOrientation();
        CameraInfo info = new CameraInfo();
        Camera.getCameraInfo(mCameraID, info);

        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {
            rotation = (info.orientation + orientation) % 360;
        }

        return rotation;
    }

    /**
     * When orientation changes, onOrientationChanged(int) of the listener will be called
     */
    private static class CameraOrientationListener extends OrientationEventListener {


        private int mCurrentNormalizedOrientation;

        private int mRememberedNormalOrientation;

        public CameraOrientationListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation != ORIENTATION_UNKNOWN) {
                mCurrentNormalizedOrientation = normalize(orientation);
            }
        }

        /**
         * @param degrees Amount of clockwise rotation from the device's natural position
         *
         * @return Normalized degrees to just 0, 90, 180, 270
         */
        private int normalize(int degrees) {
            if (degrees > 315 || degrees <= 45) {
                return 0;
            }

            if (degrees > 45 && degrees <= 135) {
                return 90;
            }

            if (degrees > 135 && degrees <= 225) {
                return 180;
            }

            if (degrees > 225 && degrees <= 315) {
                return 270;
            }

            throw new RuntimeException("The physics as we know them are no more. Watch out for anomalies.");
        }

        public void rememberOrientation() {
            mRememberedNormalOrientation = mCurrentNormalizedOrientation;
        }

        public int getRememberedNormalOrientation() {
            rememberOrientation();
            return mRememberedNormalOrientation;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Permissions
    ///////////////////////////////////////////////////////////////////////////

    public boolean hasCameraPermissions(){
        return ContextCompat.checkSelfPermission(getContext(), permissionCamera) == PackageManager.PERMISSION_GRANTED;
    }

    protected void requestCameraPermission() {

        if (!hasCameraPermissions()) {

            if (shouldShowRequestPermissionRationale(permissionCamera)) {
                showPermissionRationaleDialog(getCameraPermissionRationaleMessage(), permissionCamera);
            } else {
                requestForPermission(permissionCamera);
            }
        }
    }

    @StringRes
    protected int getCameraPermissionRationaleMessage(){
        return R.string.squarecamera__message_permission_camera_rationale;
    }

    @StringRes
    protected int getCameraRationalePositiveButtonMessage(){
        return R.string.squarecamera__ok;
    }

    @StringRes
    protected int getCameraRationaleNegativeButtonMessage(){
        return R.string.squarecamera__cancel;
    }

    /**
     * Rationale dialog
     * @param message display message
     * @param permission permissions to request if the user presses ok
     */
    protected void showPermissionRationaleDialog(@StringRes int message, final String permission) {
        new AlertDialog.Builder(getContext())
                .setMessage(message)
                .setPositiveButton(getCameraRationalePositiveButtonMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestForPermission(permission);
                    }
                })
                .setNegativeButton(getCameraRationaleNegativeButtonMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onCameraPermissionDenied();
                    }
                })
                .create()
                .show();
    }

    private void requestForPermission(final String permission) {
        requestPermissions(new String[]{permission}, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //checks camera permission state
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onCameraPermissionGranted();
            }
            else onCameraPermissionDenied();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // camera functionality
    ///////////////////////////////////////////////////////////////////////////

    public boolean isCameraFront() {
        return mCameraID == CameraInfo.CAMERA_FACING_FRONT;
    }

    public void swapCamera() {
        if (mCameraID == CameraInfo.CAMERA_FACING_FRONT) {
            mCameraID = getBackCameraID();
        } else {
            mCameraID = getFrontCameraID();
        }
        restartPreview();
    }

    public void enableCameraFlash(boolean enable) {

        if (enable && !isFlashOn()) {
            mFlashMode = Camera.Parameters.FLASH_MODE_ON;
        } else mFlashMode = Camera.Parameters.FLASH_MODE_OFF;

        setupCamera();
    }


    public boolean isFlashOn() {
        return Camera.Parameters.FLASH_MODE_ON.equalsIgnoreCase(mFlashMode);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Picture size
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Override this if you want tot change the max picture width
     * @return max picture size width
     */
    public int getPictureSizeMaxWidth(){
        return PICTURE_SIZE_MAX_WIDTH;
    }

    /**
     * Ovveride this if you want tot change the preview picture max width
     * @return
     */
    public int getPreviewSizeMaxWidth(){
        return PREVIEW_SIZE_MAX_WIDTH;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Main Fragment handlers
    ///////////////////////////////////////////////////////////////////////////

    public abstract void onFlashAvailableCheck(boolean isAvailable);

    public abstract void onCameraPermissionGranted();
    public abstract void onCameraPermissionDenied();

    /**
     * Called when a new picture is taken
     * @param image
     * @param imageParameters
     * @param rotation
     */
    public abstract void onPictureTaken(byte[] image, ImageParameters imageParameters, int rotation);

    @LayoutRes
    public abstract int getLayoutId();

    ///////////////////////////////////////////////////////////////////////////
    // Async
    ///////////////////////////////////////////////////////////////////////////

    private class PreviewTask extends AsyncTask<Integer, Void, Void> {
        protected Void doInBackground(Integer... urls) {
            startCameraPreview();
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            resizeTopAndBtmCover(topCoverView, bottomCoverView);
        }
    }

}

package com.desmond.squarecamera;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * Camera picture preview
 */
public abstract class EditSavePhotoFragment extends Fragment {

    public static final String TAG = EditSavePhotoFragment.class.getSimpleName();
    public static final String BITMAP_KEY = "bitmap_byte_array";
    public static final String ROTATION_KEY = "rotation";
    public static final String IMAGE_INFO = "image_info";
    public static final String IMAGE_URI = "image_uri";

    private static final int REQUEST_STORAGE = 1992;

    public EditSavePhotoFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(getLayoutId(), container, false);
    }

    /**
     * Bytes -> Bitmap
     * @param rotation image rotation if available
     * @param data image in bytes
     * @return image as bitmap
     */
    protected Bitmap convertImage(int rotation, byte[] data) {
        Bitmap bitmap = ImageUtility.decodeSampledBitmapFromByte(getActivity(), data);
//        Log.d(TAG, "original bitmap width " + bitmap.getWidth() + " height " + bitmap.getHeight());
        if (rotation != 0) {
            Bitmap oldBitmap = bitmap;

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);

            bitmap = Bitmap.createBitmap(
                    oldBitmap, 0, 0, oldBitmap.getWidth(), oldBitmap.getHeight(), matrix, false
            );

            oldBitmap.recycle();
        }

        return bitmap;
    }

    public void savePicture() {
        requestForPermission();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Permission
    ///////////////////////////////////////////////////////////////////////////

    private void requestForPermission() {
        RuntimePermissionActivity.startActivity(EditSavePhotoFragment.this,
                REQUEST_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Activity.RESULT_OK != resultCode) return;

        if (REQUEST_STORAGE == requestCode && data != null) {

            final boolean isGranted = data.getBooleanExtra(RuntimePermissionActivity.REQUESTED_PERMISSION, false);

            if (isGranted && getImageViewPreview() != null) {

                Bitmap bitmap = ((BitmapDrawable) getImageViewPreview().getDrawable()).getBitmap();
                Uri photoUri = ImageUtility.savePicture(getActivity(), bitmap);

                onImageSaved(photoUri);
            }
        } else {
            onImageSaveFailure(data);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Main view handlers
    ///////////////////////////////////////////////////////////////////////////

    public abstract void onCameraExtras(ImageParameters imageParameters, byte[] data, int rotation);
    public abstract void onUriExtra(Uri photoUri);

    /**
     * @return imageview used to display the picture preview
     */
    public abstract SquareImageView getImageViewPreview();

    /**
     * Called when our preview image is saved to storage
     * @param photoUri
     */
    public abstract void onImageSaved(Uri photoUri);

    public abstract void onImageSaveFailure(Intent data);

    @LayoutRes
    public abstract int getLayoutId();
}

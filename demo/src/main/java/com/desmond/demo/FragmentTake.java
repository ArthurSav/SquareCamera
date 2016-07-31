package com.desmond.demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.desmond.squarecamera.CameraFragment;
import com.desmond.squarecamera.EditSavePhotoFragment;
import com.desmond.squarecamera.ImageParameters;

public class FragmentTake extends CameraFragment {


    public static FragmentTake newInstance() {

        Bundle args = new Bundle();

        FragmentTake fragment = new FragmentTake();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View btnGallery = view.findViewById(R.id.change_camera);
        btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasCameraPermissions()) {
                    requestCameraPermission();
                }
                //else  ((TestActivity) getActivity()).openGallery();
            }
        });
    }

    @Override
    public void onPictureTaken(byte[] image, ImageParameters imageParameters, int rotation) {
        getFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.container,
                        CameraPreview.newInstance(image, rotation, imageParameters), EditSavePhotoFragment.TAG)
                .addToBackStack(null)
                .commit();
    }


    @Override
    public int getLayoutId() {
        return com.desmond.squarecamera.R.layout.squarecamera__fragment_camera;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Permission
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCameraPermissionGranted() {
        String x = "";
    }

    @Override
    public void onCameraPermissionDenied() {
        String x = "";
    }
}

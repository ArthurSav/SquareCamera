package com.desmond.demo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Toast;

import com.desmond.squarecamera.EditSavePhotoFragment;
import com.desmond.squarecamera.ImageParameters;
import com.desmond.squarecamera.SquareImageView;

import java.net.URI;

public class CameraPreview extends EditSavePhotoFragment {

    SquareImageView imageView;


    public static Fragment newInstance(byte[] bitmapByteArray, int rotation, @NonNull ImageParameters parameters) {
        Fragment fragment = new CameraPreview();

        Bundle args = new Bundle();
        args.putByteArray(BITMAP_KEY, bitmapByteArray);
        args.putInt(ROTATION_KEY, rotation);
        args.putParcelable(IMAGE_INFO, parameters);

        fragment.setArguments(args);
        return fragment;
    }

    public static Fragment newInstance(URI imageUri) {
        Fragment fragment = new CameraPreview();

        Bundle args = new Bundle();
        args.putString(IMAGE_URI, imageUri != null? imageUri.toString(): "");

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imageView = (SquareImageView) view.findViewById(R.id.photo);
        View btn = view.findViewById(R.id.save_photo);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePicture();
            }
        });

        setupExtras();
    }

    @Override
    public void onCameraExtras(ImageParameters imageParameters, byte[] data, int rotation) {
        imageView.setImageBitmap(convertImage(rotation, data));
    }

    @Override
    public void onUriExtra(Uri photoUri) {
        imageView.setImageURI(photoUri);
    }


    private void setupExtras(){

        if (getArguments().containsKey(BITMAP_KEY)) {
            int rotation = getArguments().getInt(ROTATION_KEY);
            byte[] data = getArguments().getByteArray(BITMAP_KEY);
            ImageParameters imageParameters = getArguments().getParcelable(IMAGE_INFO);

            if (data != null && imageParameters != null) {
                onCameraExtras(imageParameters, data, rotation);
            }
        }

        else if (getArguments().containsKey(IMAGE_URI)) {
            Uri photoUri = Uri.parse(getArguments().getString(IMAGE_URI));
            onUriExtra(photoUri);
        }

    }

    @Override
    public SquareImageView getImageViewPreview() {
        return imageView;
    }

    @Override
    public void onImageSaved(Uri photoUri) {
        Toast.makeText(getContext(), "Image saved: " + photoUri.getPath(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onImageSaveFailure(Intent data) {
         Toast.makeText(getContext(), "Failed to save image", Toast.LENGTH_LONG).show();
    }

    @Override
    public int getLayoutId() {
        return com.desmond.squarecamera.R.layout.squarecamera__fragment_edit_save_photo;
    }
}

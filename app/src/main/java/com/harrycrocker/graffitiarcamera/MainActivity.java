package com.harrycrocker.graffitiarcamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;

import com.harrycrocker.graffitiarcamera.EasyAR.GLView;

import cn.easyar.Engine;

public class MainActivity extends AppCompatActivity {
    // Activity name for log string
    private static String LOGTAG =  MainActivity.class.getSimpleName();
    // Camera request code
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    // Create GLView class reference
    private GLView glView;
    // Code run on activity create
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Auto Created functions to restore saved state and set content view from activity xml
        super.onCreate(savedInstanceState);
        // get api key from resources
        String key = getResources().getString(R.string.easyar_key);
        // set content view
        setContentView(R.layout.activity_main);

        // Initialize ARActivity engine, if init fails log init fail
        if (!Engine.initialize(this, key))
            Log.e(LOGTAG, "AR Library Init Failed");

        // Create new GL View instance
        glView = new GLView(this);

        // if app has camera permission add openGL view to this
        // if permission not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // permission not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Show permission not granted explanation
            } else {
                // Show user permission request
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        }
        // if permission granted after check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Set order for media overlays
            glView.setZOrderMediaOverlay(true);
            // Add GLView to view group preview
            ((ViewGroup) findViewById(R.id.preview)).addView(glView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Add Camera button
        }

    }

    // If activity paused
    @Override
    protected void onResume()
    {
        super.onResume();
        if (glView != null) { glView.onResume(); }

    }

    // If activity resumed
    @Override
    protected void onPause()
    {
        if (glView != null) { glView.onPause(); }
        super.onPause();
    }
}

package com.harrycrocker.graffitiarcamera.EasyAR;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.util.Log;

import com.harrycrocker.graffitiarcamera.GlobalApplication;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import cn.easyar.CameraCalibration;
import cn.easyar.CameraDevice;
import cn.easyar.CameraDeviceFocusMode;
import cn.easyar.CameraDeviceType;
import cn.easyar.CameraFrameStreamer;
import cn.easyar.Frame;
import cn.easyar.FunctorOfVoidFromPointerOfTargetAndBool;
import cn.easyar.ImageTarget;
import cn.easyar.ImageTracker;
import cn.easyar.Renderer;
import cn.easyar.StorageType;
import cn.easyar.Target;
import cn.easyar.TargetInstance;
import cn.easyar.TargetStatus;
import cn.easyar.Vec2I;
import cn.easyar.Vec4I;

public class arScene {
    private CameraDevice mCamera;
    private CameraFrameStreamer mCameraStreamer;
    private ArrayList<ImageTracker> mTrackers;
    private Renderer mBgRenderer;
    private ArtRenderer mArtRenderer;
    private boolean viewportChanged = false;
    private Vec2I viewSize = new Vec2I(0, 0);
    private int rotation = 0;
    private Vec4I mViewport = new Vec4I(0, 0, 1280, 720);

    private Context mContext = GlobalApplication.getAppContext();

    private HashMap<String, Integer> mTextureMap;

    public arScene() {mTrackers = new ArrayList<ImageTracker>();}

    private void loadTrackerFromImage(ImageTracker tracker, String path) {
        ImageTarget target = new ImageTarget();
        String jstr = "{\n"
                + "  \"images\" :\n"
                + "  [\n"
                + "    {\n"
                + "      \"image\" : \"" + path + "\",\n"
                + "      \"name\" : \"" + path.substring(0, path.indexOf(".")) + "\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        target.setup(jstr, StorageType.Assets | StorageType.Json, "");
        tracker.loadTarget(target, new FunctorOfVoidFromPointerOfTargetAndBool() {
            @Override
            public void invoke(Target target, boolean status) {
                Log.i("HelloAR", String.format("load target (%b): %s (%d)", status, target.name(), target.runtimeID()));
            }
        });
    }

    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
        }

        return bitmap;
    }

    // on init start stop and destroy

    public boolean initialize() {
        mCamera = new cn.easyar.CameraDevice();
        mCameraStreamer = new CameraFrameStreamer();
        mCameraStreamer.attachCamera(mCamera);

        boolean status = true;
        status &= mCamera.open(CameraDeviceType.Default);
        mCamera.setSize(new Vec2I(1280, 720));

        if (!status)
            return status;

        ImageTracker tracker = new ImageTracker();
        tracker.attachStreamer(mCameraStreamer);
        loadTrackerFromImage(tracker,"landmark_target_1.png");
        loadTrackerFromImage(tracker,"landmark_target_2.png");
        loadTrackerFromImage(tracker,"landmark_target_3.png");
        mTrackers.add(tracker);

        return status;
    }

    public void dispose() {
        for (ImageTracker tracker : mTrackers) {
            tracker.dispose();
        }
        mTrackers.clear();
        mArtRenderer = null;
        if (mBgRenderer != null) {
            mBgRenderer.dispose();
            mBgRenderer = null;
        }
        if (mCameraStreamer != null) {
            mCameraStreamer.dispose();
            mCameraStreamer = null;
        }
        if (mCamera != null) {
            mCamera.dispose();
            mCamera = null;
        }
    }

    public boolean start() {
        boolean status = true;
        status &= (mCamera != null) && mCamera.start();
        status &= (mCameraStreamer != null) && mCameraStreamer.start();
        mCamera.setFocusMode(CameraDeviceFocusMode.Continousauto);
        for (ImageTracker tracker : mTrackers) {
            status &= tracker.start();
        }
        return status;
    }

    public boolean stop() {
        boolean status = true;
        for (ImageTracker tracker : mTrackers) {
            status &= tracker.stop();
        }
        status &= (mCameraStreamer != null) && mCameraStreamer.stop();
        status &= (mCamera != null) && mCamera.stop();
        return status;
    }

    // GL functions
    public void initGL() {
        if (mBgRenderer != null)
            mBgRenderer.dispose();

        mBgRenderer = new Renderer();
        mArtRenderer = new ArtRenderer();
        mArtRenderer.init();

        mTextureMap = new HashMap<String, Integer>();
        mTextureMap.put("landmark_target_1", mArtRenderer.generateTexture(getBitmapFromAsset(mContext,"landmark_spray_1.png")));
        mTextureMap.put("landmark_target_2", mArtRenderer.generateTexture(getBitmapFromAsset(mContext,"landmark_spray_2.png")));
        mTextureMap.put("landmark_target_3", mArtRenderer.generateTexture(getBitmapFromAsset(mContext,"landmark_spray_3.png")));
    }

    public void resizeGL(int width, int height) {
        viewSize = new Vec2I(width, height);
        viewportChanged = true;
    }

    private void updateViewport() {
        CameraCalibration mCalibration = mCamera != null ? mCamera.cameraCalibration() : null;
        int rotation = mCalibration != null ? mCalibration.rotation() : 0;
        if (rotation != this.rotation) {
            this.rotation = rotation;
            viewportChanged = true;
        }
        if (viewportChanged) {
            Vec2I size = new Vec2I(1, 1);

            if ((mCamera != null) && mCamera.isOpened())
                size = mCamera.size();

            if (rotation == 90 || rotation == 270)
                size = new Vec2I(size.data[1], size.data[0]);

            float scaleRatio = Math.max((float) viewSize.data[0] / (float) size.data[0], (float) viewSize.data[1] / (float) size.data[1]);
            Vec2I viewportSize = new Vec2I(Math.round(size.data[0] * scaleRatio), Math.round(size.data[1] * scaleRatio));

            mViewport = new Vec4I((viewSize.data[0] - viewSize.data[0]) / 2, (viewSize.data[1] - viewportSize.data[1]) / 2, viewportSize.data[0], viewportSize.data[1]);

            if ((mCamera != null) && mCamera.isOpened())
                viewportChanged = false;
        }
    }

    public void render() {
        GLES20.glClearColor(1.f, 1.f, 1.f, 1.f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mBgRenderer != null) {
            Vec4I defaultViewport = new Vec4I(0, 0, viewSize.data[0], viewSize.data[1]);
            GLES20.glViewport(defaultViewport.data[0], defaultViewport.data[1], defaultViewport.data[2], defaultViewport.data[3]);
            if (mBgRenderer.renderErrorMessage(defaultViewport))
                return;
        }

        if (mCameraStreamer == null)
            return;

        Frame frame = mCameraStreamer.peek();
        try {
            updateViewport();
            GLES20.glViewport(mViewport.data[0], mViewport.data[1], mViewport.data[2], mViewport.data[3]);

            if (mBgRenderer != null)
                mBgRenderer.render(frame, mViewport);

            for (TargetInstance targetInstance : frame.targetInstances()) {
                int status = targetInstance.status();
                if (status == TargetStatus.Tracked) {
                    Target target = targetInstance.target();
                    ImageTarget imageTarget = target instanceof ImageTarget ? (ImageTarget) (target) : null;

                    if (imageTarget == null)
                        continue;
                    //else
                    //    Log.v("arScene", "target detected " + target.name() + " ");

                    if (mArtRenderer != null)
                        mArtRenderer.render(mCamera.projectionGL(0.2f, 500.f), targetInstance.poseGL(), imageTarget.size(), mTextureMap.get(target.name()));
                }
            }
        }
        finally {
            frame.dispose();
        }
    }
}

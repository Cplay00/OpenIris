package com.yolo.openiris;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.view.Surface;

public class Yolov11Ncnn
{
    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
    public native boolean openCamera(int facing);
    public native boolean closeCamera();
    public native boolean setOutputWindow(Surface surface);

    public native int[] detectBitmap(Bitmap bitmap, int modelid, int cpugpu);

    static {
        System.loadLibrary("yolov11ncnn");
    }
}

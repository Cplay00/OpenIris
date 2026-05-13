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
    
    // 设置摄像头分辨率（需要重新打开摄像头生效）
    public native boolean setCameraResolution(int width, int height);
    
    // 获取当前帧到 Bitmap（用于截图）
    public native boolean captureFrame(Bitmap bitmap);

    static {
        System.loadLibrary("yolov11ncnn");
    }
}

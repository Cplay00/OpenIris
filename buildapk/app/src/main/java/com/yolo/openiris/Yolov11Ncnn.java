package com.yolo.openiris;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.view.Surface;

public class Yolov11Ncnn
{
    // 检测结果回调接口
    public interface DetectionCallback {
        void onDetectionResult(int[] results);
    }

    private static DetectionCallback detectionCallback;

    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
    public native boolean openCamera(int facing);
    public native boolean closeCamera();
    public native boolean setOutputWindow(Surface surface);

    public native int[] detectBitmap(Bitmap bitmap, int modelid, int cpugpu);

    public void setDetectionCallback(DetectionCallback callback) {
        detectionCallback = callback;
    }

    // 由 JNI 调用的方法
    private static void onDetectionResultFromJNI(int[] results) {
        if (detectionCallback != null) {
            detectionCallback.onDetectionResult(results);
        }
    }

    static {
        System.loadLibrary("yolov11ncnn");
    }
}

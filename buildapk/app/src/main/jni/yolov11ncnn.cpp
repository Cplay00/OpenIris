// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "yolov11.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    snprintf(text, sizeof(text), "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

//static Inference_det* g_yolo = 0;
static Inference* g_yolo = 0;
static ncnn::Mutex lock;

// 用于保存最后一帧的静态变量
static cv::Mat g_last_frame;
static ncnn::Mutex frame_lock;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // 保存当前帧（用于截图）
    {
        ncnn::MutexLockGuard g(frame_lock);
        g_last_frame = rgb.clone();
    }

    // nanodet
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolo)
        {
            std::vector<Object> objects;
            objects = g_yolo->runInference(rgb);

            g_yolo->draw(rgb, objects);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolo;
        g_yolo = 0;
    }

    delete g_camera;
    g_camera = 0;
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_yolo_openiris_Yolov11Ncnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint modelid, jint cpugpu)
{
    if (modelid < 0 || modelid > 1 || cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
    {
        "n",
        "s",
    };

    const int target_sizes[] =
    {
        640,
        640,
    };

    const float mean_vals[][3] =
    {
        {0.0f, 0.0f, 0.0f},
        {0.0f, 0.0f, 0.0f},
    };

    const float norm_vals[][3] =
    {
        { 1 / 255.f, 1 / 255.f, 1 / 255.f },
        { 1 / 255.f, 1 / 255.f, 1 / 255.f },
    };

    const char* modeltype = modeltypes[(int)modelid];
    int target_size = target_sizes[(int)modelid];
    bool use_gpu = (int)cpugpu == 1;

    // reload - 先删除旧模型，再创建新模型
    {
        ncnn::MutexLockGuard g(lock);

        // 检查 GPU 是否可用
        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            __android_log_print(ANDROID_LOG_WARN, "ncnn", "GPU not available, cannot load with GPU");
            delete g_yolo;
            g_yolo = 0;
            return JNI_FALSE;
        }

        // 删除旧模型（如果存在）
        if (g_yolo)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "Deleting old model before reload");
            delete g_yolo;
            g_yolo = 0;
        }

        // 创建新模型并加载
        g_yolo = new Inference;
        g_yolo->loadNcnnNetwork(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "Model loaded with GPU=%d", use_gpu ? 1 : 0);
    }

    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_yolo_openiris_Yolov11Ncnn_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    g_camera->open((int)facing);

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_yolo_openiris_Yolov11Ncnn_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_yolo_openiris_Yolov11Ncnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}

// public native int[] detectBitmap(Bitmap bitmap, int modelid, int cpugpu);
JNIEXPORT jintArray JNICALL Java_com_yolo_openiris_Yolov11Ncnn_detectBitmap(JNIEnv* env, jobject thiz, jobject bitmap, jint modelid, jint cpugpu)
{
    if (!g_yolo)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Model not loaded");
        return env->NewIntArray(0);
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to get bitmap info");
        return env->NewIntArray(0);
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Unsupported bitmap format");
        return env->NewIntArray(0);
    }

    void* pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to lock bitmap pixels");
        return env->NewIntArray(0);
    }

    int width = info.width;
    int height = info.height;
    cv::Mat rgba(height, width, CV_8UC4, pixels);
    cv::Mat bgr;
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);

    AndroidBitmap_unlockPixels(env, bitmap);

    std::vector<Object> objects;
    {
        ncnn::MutexLockGuard g(lock);
        objects = g_yolo->runInference(bgr);
    }

    int num_objects = objects.size();
    int result_size = num_objects * 6;
    jintArray result = env->NewIntArray(result_size);
    if (result == nullptr)
    {
        return env->NewIntArray(0);
    }

    jint* result_data = env->GetIntArrayElements(result, nullptr);
    for (int i = 0; i < num_objects; i++)
    {
        const Object& obj = objects[i];
        result_data[i * 6 + 0] = (int)obj.rect.x;
        result_data[i * 6 + 1] = (int)obj.rect.y;
        result_data[i * 6 + 2] = (int)obj.rect.width;
        result_data[i * 6 + 3] = (int)obj.rect.height;
        result_data[i * 6 + 4] = obj.label;
        result_data[i * 6 + 5] = (int)(obj.prob * 1000);
    }
    env->ReleaseIntArrayElements(result, result_data, 0);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "Detected %d objects", num_objects);
    return result;
}

// public native boolean setCameraResolution(int width, int height);
JNIEXPORT jboolean JNICALL Java_com_yolo_openiris_Yolov11Ncnn_setCameraResolution(JNIEnv* env, jobject thiz, jint width, jint height)
{
    if (width <= 0 || height <= 0)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Invalid resolution: %dx%d", width, height);
        return JNI_FALSE;
    }

    // 验证分辨率范围
    if (width < 160 || height < 120 || width > 4096 || height > 4096)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Resolution out of range: %dx%d", width, height);
        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setCameraResolution %dx%d", width, height);

    // 使用锁保护分辨率切换，避免与captureFrame并发问题
    ncnn::MutexLockGuard g(frame_lock);

    // 保存当前 facing
    int facing = g_camera->camera_facing;

    // 清除旧帧
    g_last_frame.release();

    // 设置新分辨率（会自动关闭摄像头并重新创建 ImageReader）
    g_camera->setResolution(width, height);

    // 重新打开摄像头
    g_camera->open(facing);

    return JNI_TRUE;
}

// public native boolean captureFrame(Bitmap bitmap);
JNIEXPORT jboolean JNICALL Java_com_yolo_openiris_Yolov11Ncnn_captureFrame(JNIEnv* env, jobject thiz, jobject bitmap)
{
    ncnn::MutexLockGuard g(frame_lock);

    if (g_last_frame.empty())
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "No frame available for capture");
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to get bitmap info");
        return JNI_FALSE;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Unsupported bitmap format");
        return JNI_FALSE;
    }

    // 确保帧尺寸与 Bitmap 匹配
    cv::Mat frame;
    if (g_last_frame.cols != (int)info.width || g_last_frame.rows != (int)info.height)
    {
        cv::resize(g_last_frame, frame, cv::Size(info.width, info.height));
    }
    else
    {
        frame = g_last_frame;
    }

    void* pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to lock bitmap pixels");
        return JNI_FALSE;
    }

    // BGR -> RGBA
    cv::Mat rgba;
    cv::cvtColor(frame, rgba, cv::COLOR_BGR2RGBA);

    // 检查转换结果
    if (rgba.empty() || rgba.data == nullptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to convert frame to RGBA");
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    // 复制像素
    size_t copySize = rgba.total() * rgba.elemSize();
    size_t bitmapSize = info.stride * info.height;
    if (copySize > bitmapSize)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Frame size exceeds bitmap size");
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }
    memcpy(pixels, rgba.data, copySize);

    AndroidBitmap_unlockPixels(env, bitmap);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "Frame captured: %dx%d", frame.cols, frame.rows);
    return JNI_TRUE;
}

}

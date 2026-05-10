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

// JNI 回调相关全局变量
static JavaVM* g_jvm = nullptr;
static jmethodID g_callback_method = nullptr;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // nanodet
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolo)
        {
            std::vector<Object> objects;
            objects = g_yolo->runInference(rgb);

            // 调用 Java 回调
            if (g_jvm && g_callback_method)
            {
                JNIEnv* env = nullptr;
                bool attached = false;
                int status = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_4);
                if (status == JNI_EDETACHED) {
                    g_jvm->AttachCurrentThread(&env, nullptr);
                    attached = true;
                }

                if (env) {
                    int num_objects = objects.size();
                    int result_size = num_objects * 6;
                    jintArray result = env->NewIntArray(result_size);
                    if (result) {
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
                        env->CallStaticVoidMethod(
                            env->FindClass("com/yolo/openiris/Yolov11Ncnn"),
                            g_callback_method,
                            result
                        );
                        env->DeleteLocalRef(result);
                    }
                }

                if (attached) {
                    g_jvm->DetachCurrentThread();
                }
            }

            g_yolo->draw(rgb, objects);
        }
        /*if (g_yolo)
        {
            std::vector<Detection> objects;
            objects = g_yolo->runInference(rgb);

            g_yolo->draw(rgb, objects);
        }*/
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

    g_jvm = vm;
    g_camera = new MyNdkCamera;

    // 预初始化回调方法 ID
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_4) == JNI_OK) {
        jclass clazz = env->FindClass("com/yolo/openiris/Yolov11Ncnn");
        if (clazz) {
            g_callback_method = env->GetStaticMethodID(clazz, "onDetectionResultFromJNI", "([I)V");
            env->DeleteLocalRef(clazz);
        }
    }

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

    g_jvm = nullptr;
    g_callback_method = nullptr;
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

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            __android_log_print(ANDROID_LOG_WARN, "ncnn", "GPU requested but no Vulkan GPU available, falling back to CPU");
            use_gpu = false;
        }

        if (!g_yolo) {
            g_yolo = new Inference;
        }
        int ret = g_yolo->loadNcnnNetwork(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
        if (ret != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to load model: %s (ret=%d)", modeltype, ret);
            delete g_yolo;
            g_yolo = 0;
            return JNI_FALSE;
        }
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

}

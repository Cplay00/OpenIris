# Native 依赖清单

本项目在 `app/src/main/jni/` 下包含以下预编译 native 库，用于离线构建。

## ncnn

| 属性 | 值 |
|------|-----|
| **库名** | ncnn |
| **版本** | 20240410 |
| **变体** | android-vulkan |
| **来源** | https://github.com/Tencent/ncnn/releases/tag/20240410 |
| **下载地址** | https://github.com/Tencent/ncnn/releases/download/20240410/ncnn-20240410-android-vulkan.zip |
| **许可证** | BSD 3-Clause (https://github.com/Tencent/ncnn/blob/master/LICENSE.txt) |
| **支持架构** | arm64-v8a, armeabi-v7a, x86_64, x86 |
| **用途** | YOLOv11 推理引擎，提供 Android 上的神经网络前向推理 |

## opencv-mobile

| 属性 | 值 |
|------|-----|
| **库名** | opencv-mobile |
| **版本** | 3.4.20 |
| **变体** | android |
| **来源** | https://github.com/nihui/opencv-mobile/releases/tag/v3.4.20 |
| **下载地址** | https://github.com/nihui/opencv-mobile/releases/download/v3.4.20/opencv-mobile-3.4.20-android.zip |
| **许可证** | Apache-2.0 (https://github.com/nihui/opencv-mobile/blob/master/LICENSE) |
| **支持架构** | arm64-v8a, armeabi-v7a, x86_64, x86 |
| **用途** | 轻量级 OpenCV 替代，提供图像预处理（resize、颜色转换等） |

## 更新步骤

1. 从上述 GitHub Releases 下载对应版本的 zip 文件
2. 解压到 `app/src/main/jni/` 目录
3. 更新本文件中的版本信息
4. 更新 `CMakeLists.txt` 中的路径（如有变更）
5. 验证构建通过

## 注意事项

- 这些库是预编译的静态库，不需要设备上安装额外的 so 文件
- ncnn 包含 Vulkan 支持，需要设备支持 Vulkan 1.0+
- opencv-mobile 是 OpenCV 的精简版本，仅包含核心模块
- 如需升级版本，请先在目标设备上验证兼容性

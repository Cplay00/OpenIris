# OpenIris Android 构建说明

## 目录结构

```
.buildapk/
├── app/                          # Android 应用模块
│   ├── build.gradle              # 应用构建配置
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/           # 模型文件
│       │   │   └── models/yolov11n/
│       │   │       ├── model.param
│       │   │       ├── model.bin
│       │   │       └── labels.txt
│       │   ├── java/com/yolo/openiris/  # Kotlin 源码
│       │   │   ├── MainActivity.kt
│       │   │   ├── ImageDetectActivity.kt
│       │   │   ├── RealtimeDetectActivity.kt
│       │   │   ├── VideoDetectActivity.kt
│       │   │   ├── SettingsActivity.kt
│       │   │   ├── config/
│       │   │   ├── detection/
│       │   │   ├── vlm/
│       │   │   ├── llm/
│       │   │   ├── fusion/
│       │   │   ├── export/
│       │   │   ├── ui/
│       │   │   └── utils/
│       │   ├── jni/              # C++ JNI 源码
│       │   │   ├── CMakeLists.txt
│       │   │   ├── yolov11ncnn.cpp
│       │   │   ├── yolov11.cpp
│       │   │   └── ndkcamera.cpp
│       │   └── res/              # Android 资源
│       │       ├── layout/
│       │       ├── values/
│       │       └── mipmap-hdpi/
│       └── test/                 # 单元测试
│           └── java/com/yolo/openiris/
├── build.gradle                  # 根项目构建配置
├── settings.gradle               # 项目设置
├── gradle.properties             # Gradle 属性
├── gradlew                       # Gradle Wrapper (Unix)
├── gradlew.bat                   # Gradle Wrapper (Windows)
├── gradle/                       # Gradle Wrapper 配置
├── docs/                         # 项目文档
├── tools/                        # 工具脚本
└── README.md                     # 项目说明
```

## 构建步骤

### 1. 环境准备

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17 或更高版本
- Android SDK 34
- NDK 25.x 或更高版本
- CMake 3.10.2 或更高版本

### 2. 打开项目

1. 启动 Android Studio
2. 选择 "Open an existing Android Studio project"
3. 选择 `.buildapk` 目录
4. 等待 Gradle 同步完成

### 3. 配置 NDK 和 CMake

1. 打开 File > Project Structure
2. 在 SDK Location 中确认 NDK 路径
3. 如果 CMake 未安装，通过 SDK Manager 安装

### 4. 构建 APK

#### 调试版本
```bash
./gradlew assembleDebug
```

#### 发布版本
```bash
./gradlew assembleRelease
```

### 5. 运行测试

#### 单元测试
```bash
./gradlew testDebugUnitTest
```

#### 仪器化测试
```bash
./gradlew connectedAndroidTest
```

## 注意事项

### 1. 模型文件

模型文件位于 `app/src/main/assets/models/yolov11n/`：
- `model.param`: NCNN 网络结构文件
- `model.bin`: NCNN 模型权重文件
- `labels.txt`: 类别标签文件（COCO 80 类）

如需使用其他模型，替换这三个文件即可。

### 2. NCNN 和 OpenCV

项目依赖 NCNN 和 OpenCV-Mobile 库。需要：

1. 下载 NCNN Android SDK：
   - https://github.com/Tencent/ncnn/releases
   - 解压到 `app/src/main/jni/` 目录

2. 下载 OpenCV-Mobile：
   - https://github.com/nihui/opencv-mobile/releases
   - 解压到 `app/src/main/jni/` 目录

3. 更新 `app/src/main/jni/CMakeLists.txt` 中的路径：
   ```cmake
   set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.5.1-android/sdk/native/jni)
   set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
   ```

### 3. API 配置

首次运行需要在设置页面配置：
- API Base URL: OpenAI-compatible API 地址
- API Key: API 密钥
- VLM Model: 视觉语言模型名称（默认: qwen3.5-35b-a3b）
- LLM Model: 大语言模型名称（默认: deepseek-v4-flash）

### 4. 权限

应用需要以下权限：
- CAMERA: 摄像头访问
- INTERNET: 网络访问
- READ_MEDIA_IMAGES: 图片读取（Android 13+）
- READ_MEDIA_VIDEO: 视频读取（Android 13+）

### 5. 最低系统要求

- Android 12 (API 31) 或更高版本
- ARM64 或 ARMv7 架构
- 建议骁龙 865 或更高版本芯片

## 功能说明

### 图片检测
- 选择本地图片
- YOLO 目标检测
- VLM 视觉识别（需要 API）
- LLM 结果融合（需要 API）
- 导出 JSON 和标注图片

### 实时检测
- 摄像头实时预览
- YOLO 实时检测（≥15 FPS）
- 检测框实时绘制
- VLM 间隔触发（默认 5 秒）
- GPU/CPU 切换

### 视频检测
- 选择本地视频
- 视频抽帧分析
- 时间轴结果展示
- 导出分析结果

### 设置
- API 配置管理
- 模型选择
- GPU 加速开关
- VLM 触发间隔设置

## 故障排除

### 1. Gradle 同步失败
- 检查网络连接
- 确认 Android Studio 版本
- 清理缓存：File > Invalidate Caches

### 2. NDK 编译错误
- 确认 NDK 版本
- 检查 CMakeLists.txt 路径
- 确认 NCNN 和 OpenCV 库已正确放置

### 3. 运行时崩溃
- 检查设备 Android 版本
- 确认权限已授予
- 查看 Logcat 错误日志

### 4. 模型加载失败
- 确认模型文件完整
- 检查文件路径是否正确
- 验证模型格式是否兼容

## 技术支持

如有问题，请查看：
- README.md: 项目概述
- docs/: 详细文档
- GitHub Issues: 问题反馈

---

**文档版本**: 1.0
**更新日期**: 2025-04-27

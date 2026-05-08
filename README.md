# OpenIris - 智能目标检测与 AI 识别 Android 应用

<div align="center">

![OpenIris Logo](https://img.shields.io/badge/OpenIris-v0.2--Alpha-blue?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-12%2B-green?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-orange?style=for-the-badge)

**基于 YOLOv11 + VLM/LLM 的实时目标检测与智能识别系统**

[English](#english) | [中文](#中文)

</div>

---

## 中文

### 项目简介

OpenIris 是一款基于 **YOLOv11** 目标检测模型和 **VLM/LLM** 视觉语言大模型的 Android 智能识别应用。支持实时摄像头检测、图片分析、视频处理，并提供 AI 辅助识别与结果融合功能。

### 主要功能

| 功能 | 说明 |
|------|------|
| **实时检测** | 摄像头实时预览，YOLO 实时检测，15 秒滑动窗口统计 |
| **图片检测** | 本地图片分析，支持 HEIF/HEIC 格式，高分辨率拍照 |
| **视频检测** | 视频抽帧分析，时间轴结果展示，AI 辅助识别 |
| **AI 模型管理** | 多提供商支持，模型列表获取，视觉能力配置 |
| **检测结果** | 三栏卡片式展示 (YOLO/AI/综合)，胶囊式结果 UI |
| **导出功能** | JSON 数据导出，标注图片导出，可配置导出路径 |

### 技术栈

- **检测模型**: YOLOv11 (NCNN 推理引擎)
- **AI 模型**: 支持 OpenAI 兼容 API 的 VLM/LLM
- **UI 框架**: Material Design 3
- **最低版本**: Android 12 (API 31)
- **目标版本**: Android 15 (API 35)

### 快速开始

#### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 34
- NDK 23.1.7779620
- CMake 3.10.2+

#### 构建步骤

```bash
# 克隆仓库
git clone -b alpha https://github.com/Cplay00/OpenIris.git
cd OpenIris/buildapk

# 下载依赖库（首次构建需要）
# 1. NCNN Android SDK
# 2. OpenCV-Mobile
# 详见 BUILD_INSTRUCTIONS.md

# 构建调试版
./gradlew assembleDebug
```

#### 依赖库安装

1. **下载 NCNN Android SDK**
   - https://github.com/Tencent/ncnn/releases
   - 解压到 `app/src/main/jni/ncnn-20240410-android-vulkan/`

2. **下载 OpenCV-Mobile**
   - https://github.com/nihui/opencv-mobile/releases
   - 解压到 `app/src/main/jni/opencv-mobile-3.4.20-android/`

### 项目结构

```
buildapk/
├── app/
│   ├── src/main/
│   │   ├── java/com/yolo/openiris/
│   │   │   ├── ai/                    # AI 模型管理
│   │   │   ├── config/                # 配置管理
│   │   │   ├── detection/             # 检测结果模型
│   │   │   ├── vlm/                   # 视觉语言模型
│   │   │   ├── llm/                   # 大语言模型
│   │   │   ├── fusion/                # 结果融合
│   │   │   ├── export/                # 导出功能
│   │   │   ├── ui/                    # UI 组件
│   │   │   └── utils/                 # 工具类
│   │   ├── jni/                       # C++ 原生代码
│   │   └── res/                       # 资源文件
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

### 功能截图

| 实时检测 | 图片检测 | AI 模型设置 |
|----------|----------|-------------|
| ![实时检测](https://via.placeholder.com/300x600?text=Realtime+Detection) | ![图片检测](https://via.placeholder.com/300x600?text=Image+Detection) | ![AI设置](https://via.placeholder.com/300x600?text=AI+Settings) |

### 配置说明

#### AI 模型配置

1. 打开应用 → 设置 → AI 模型设置
2. 添加 AI 提供商（支持 OpenAI 兼容 API）
3. 获取模型列表并选择要使用的模型
4. 配置模型视觉能力开关

#### 导出路径配置

- 图片导出：`内部存储/Pictures`
- JSON 导出：`内部存储/Documents/YOLO_Export/JSON`
- 可在设置中自定义导出路径

### 已知问题

- 实时检测中的 AI 识别需要手动开启
- 部分 AI 模型的视觉能力需要在提供商平台开启

### 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 许可证

本项目采用 Apache License 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

## English

### Introduction

OpenIris is an Android intelligent detection application based on **YOLOv11** object detection model and **VLM/LLM** vision language models. It supports real-time camera detection, image analysis, and video processing with AI-assisted recognition.

### Features

| Feature | Description |
|---------|-------------|
| **Real-time Detection** | Camera preview, YOLO detection, 15-second sliding window statistics |
| **Image Detection** | Local image analysis, HEIF/HEIC support, high-resolution capture |
| **Video Detection** | Video frame analysis, timeline results, AI-assisted recognition |
| **AI Model Management** | Multi-provider support, model list fetching, vision capability configuration |
| **Detection Results** | Three-column card display (YOLO/AI/Combined), capsule-style result UI |
| **Export Function** | JSON data export, annotated image export, configurable export paths |

### Tech Stack

- **Detection Model**: YOLOv11 (NCNN inference engine)
- **AI Models**: VLM/LLM with OpenAI-compatible API support
- **UI Framework**: Material Design 3
- **Minimum Version**: Android 12 (API 31)
- **Target Version**: Android 15 (API 35)

### Quick Start

#### Requirements

- Android Studio Hedgehog (2023.1.1) or higher
- JDK 17+
- Android SDK 34
- NDK 23.1.7779620
- CMake 3.10.2+

#### Build

```bash
# Clone the repository
git clone -b alpha https://github.com/Cplay00/OpenIris.git
cd OpenIris/buildapk

# Build debug version
./gradlew assembleDebug
```

### License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with ❤️ for Android developers**

[⬆ Back to Top](#openiris---智能目标检测与-ai-识别-android-应用)

</div>

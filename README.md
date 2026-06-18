# OpenIris - 智能目标检测与 AI 识别 Android 应用

<div align="center">

![OpenIris](https://img.shields.io/badge/OpenIris-v0.5--Alpha-blue?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-12%2B-green?style=for-the-badge)
![YOLO](https://img.shields.io/badge/YOLO-v11-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-yellowgreen?style=for-the-badge)

**基于 YOLOv11 + VLM/LLM 的实时目标检测与智能识别系统**

[English](#english) | [中文](#中文)

</div>

---

## 中文

### 项目简介

OpenIris 是一款基于 **YOLOv11** 目标检测模型和 **VLM/LLM** 视觉语言大模型的 Android 智能识别应用。支持实时摄像头检测、图片分析、视频处理,并提供 AI 辅助识别与结果融合功能。

### 主要功能

| 功能 | 说明 |
------|------|
| **实时检测** | 摄像头实时预览,YOLO 实时检测,15 秒滑动窗口统计 |
| **图片检测** | 本地图片分析,支持 HEIF/HEIC 格式,高分辨率拍照 |
| **视频检测** | 视频抽帧分析,时间轴结果展示,AI 辅助识别 |
| **AI 模型管理** | 多供应商支持,模型列表获取,视觉能力配置 |
| **检测结果** | 三栏卡片式展示 (YOLO/AI/综合),胶囊式结果 UI |
| **导出功能** | JSON 数据导出,标注图片导出,可配置导出路径 |
| **训练平台** | 图形化训练界面,自建数据集,模型改进,一键部署 |

### 技术栈

| 组件 | 技术 |
------|------|
| 检测模型 | YOLOv11 (NCNN 推理引擎) |
| AI 模型 | VLM/LLM (OpenAI 兼容 API) |
| UI 框架 | Material Design 3 |
| 训练框架 | Ultralytics + tkinter GUI |
| 最低版本 | Android 12 (API 31) |
| 目标版本 | Android 16 (API 36) |

---

## 项目结构

```
Yolo11forAndroid/
├── buildapk/                    # Android 主项目 (可直接构建 APK)
│   └── app/src/main/
│       ├── assets/models/       # NCNN 模型文件
│       ├── java/com/yolo/openiris/  # Kotlin/Java 源码
│       └── jni/                 # C++ NCNN 推理代码
├── ncnn-android-yolov11/        # NCNN Android 原始项目
├── training/                    # 训练框架 (详见下方)
│   ├── run_gui.py               # GUI 入口
│   ├── build_gui.bat            # 打包 exe 脚本
│   ├── configs/                 # 训练配置
│   ├── scripts/                 # 训练脚本
│   ├── tools/                   # 工具脚本
│   └── gui/                     # 图形界面
├── tools/                       # 模型转换工具
├── docs/                        # 项目文档
└── Package/                     # 构建输出
```

---

## 快速开始

### 1. Android 应用构建

**环境要求:**
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 34
- NDK 23.1.7779620
- CMake 3.10.2+

**构建步骤:**
```bash
# 克隆仓库
git clone -b alpha https://github.com/your-org/OpenIris.git
cd OpenIris/buildapk

# 构建 debug 版本
./gradlew assembleDebug

# APK 输出: buildapk/app/build/outputs/apk/debug/
```

### 2. 模型训练 (图形界面)

**方式一: 直接运行 (需要 Python 环境)**
```bash
# 安装依赖
pip install -r training/requirements.txt

# 启动 GUI
python training/run_gui.py
```

**方式二: 打包为 exe (无需 Python 环境)**
```bash
# 安装 PyInstaller
pip install pyinstaller

# 运行打包脚本
training\build_gui.bat

# 输出: training\dist\OpenIrisTraining\OpenIrisTraining.exe
```

### 3. 命令行训练

```bash
# 标准训练
python training/scripts/train.py --data configs/dataset_custom.yaml --epochs 200

# 高精度训练
python training/scripts/train_highprec.py --data configs/dataset_custom.yaml --epochs 200

# 使用改进模型 (CBAM 注意力)
python training/scripts/train_with_improvements.py --model cbam --data configs/dataset_custom.yaml
```

---

## 训练框架

### 功能特性

#### 🎯 高精度训练
- 多阶段训练策略 (预热 → 精细 → 最终)
- 知识蒸馏 (从大模型学习)
- EMA 模型平滑
- 混合精度训练 (AMP)
- 矩形训练 (自动适配不同长宽比图片)

#### 📊 自建数据集
- 多格式标注转换 (COCO/VOC/LabelMe)
- 智能数据集划分 (分层划分)
- 离线数据增强
- 数据集质量校验

#### 🖥️ 图形化界面
- 中英双语界面 (默认中文)
- 每个参数都有详细工具提示
- 全宽滑块 + 可编辑数值
- 本地模型路径自由选择
- 现代扁平化 UI 设计
- 深色主题日志控制台
- 自适应系统 DPI 缩放

#### 🧠 算法改进
- SE (Squeeze-and-Excitation) 注意力
- CBAM (Convolutional Block Attention Module)
- GhostConv 轻量级卷积
- C2fCIB 增强结构

### 训练目录结构

```
training/
├── run_gui.py                      # GUI 入口 (推荐)
├── build_gui.bat                   # 打包 exe 脚本
├── build_gui.spec                  # PyInstaller 配置
├── requirements.txt                # Python 依赖
├── README.md                       # 训练框架文档
├── configs/
│   ├── dataset_coco.yaml           # COCO 数据集配置
│   ├── dataset_custom.yaml         # 自定义数据集模板
│   ├── hyp_train.yaml              # 训练超参数
│   ├── hyp_augment.yaml            # 数据增强配置
│   └── models/                     # 改进模型配置
│       ├── yolo11-se.yaml          # +SE 注意力
│       ├── yolo11-cbam.yaml        # +CBAM 注意力
│       ├── yolo11-ghost.yaml       # +GhostConv
│       └── yolo11-enhanced.yaml    # 综合改进
├── scripts/
│   ├── train.py                    # 标准训练
│   ├── train_highprec.py           # 高精度训练
│   ├── train_adapted.py            # 适配参考环境
│   ├── train_with_improvements.py  # 改进模型训练
│   ├── evaluate.py                 # 模型评估
│   ├── dataset_validator.py        # 数据集校验
│   ├── setup_environment.bat       # 环境设置 (CMD)
│   ├── setup_environment.ps1       # 环境设置 (PowerShell)
│   └── quick_start.bat             # 快速启动
├── tools/
│   ├── export_pipeline.py          # 模型导出 (.pt → ONNX → NCNN)
│   ├── visualize_results.py        # 训练结果可视化
│   ├── convert_icon.py             # PNG → ICO 转换
│   └── dataset_builder/            # 数据集构建工具
│       ├── converter.py            # 格式转换器
│       ├── splitter.py             # 数据集划分器
│       └── augmentor.py            # 数据增强器
├── gui/
│   ├── __init__.py
│   ├── i18n.py                     # 中英双语模块
│   ├── main_window.py              # 主窗口
│   ├── launcher.py                 # 启动器 (legacy)
│   └── openiris.ico                # 应用图标
└── docs/
    ├── ADAPTATION_GUIDE.md         # 环境适配指南
    ├── ALGORITHM_IMPROVEMENTS.md   # 算法改进文档
    └── DEPENDENCY_COMPARISON.md    # 依赖对比分析
```

### 训练配置示例

**数据集配置 (dataset_custom.yaml):**
```yaml
path: ./datasets/your_dataset
train: images/train
val: images/val

nc: 5  # 类别数
names:
  0: class_0
  1: class_1
  2: class_2
  3: class_3
  4: class_4
```

**训练超参数 (hyp_train.yaml):**
```yaml
model: yolov11n.pt
imgsz: 640
epochs: 200
batch: 32
optimizer: AdamW
lr0: 0.001
amp: true
rect: true  # 自动适配不同长宽比
patience: 50
label_smoothing: 0.02
```

### 关键约束

| 约束项 | 值 | 说明 |
--------|-----|------|
| 模型变体 | YOLOv11n | 移动端必须用 nano |
| 输入尺寸 | 640 | 与 NCNN 推理端一致 |
| 归一化 | /255, mean=0 | ultralytics 默认 |
| ONNX opset | ≥12 | NCNN 兼容性 |
| 激活函数 | SiLU | 不建议改 RELU |
| 精度 | FP16 | NCNN 推理使用 |
| 矩形训练 | rect=True | 自动适配非标准图片 |

---

## 模型导出

### 自动导出流水线

```bash
# 训练完成后导出到 Android
python training/tools/export_pipeline.py \
    --weights runs/train/openiris_v1/weights/best.pt \
    --assets ncnn-android-yolov11/app/src/main/assets/models/my_model
```

### 导出流程

```
PyTorch (.pt) → ONNX → NCNN (.param + .bin) → Android assets
```

### 手动导出

```bash
# 使用原有导出脚本
python tools/export_yolov11_ncnn.py --weights path/to/best.pt --imgsz 640
```

---

## 依赖清单

### Android 构建

- Android Studio Hedgehog+
- JDK 17+
- Android SDK 34
- NDK 23.1.7779620
- CMake 3.10.2+

### Python 训练环境

**核心依赖 (必须):**
```bash
pip install ultralytics>=8.3.0
pip install torch torchvision  # 根据 CUDA 版本选择
pip install opencv-python>=4.8.0
pip install pyyaml>=6.0
pip install pandas>=2.0.0
```

**PyTorch 安装 (根据 CUDA 版本):**
```bash
# CUDA 11.8
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118

# CUDA 12.1
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

# CPU only
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
```

**可选依赖:**
```bash
pip install matplotlib seaborn scikit-learn tensorboard
pip install onnx onnxsim  # ONNX 导出
pip install albumentations  # 高级数据增强
```

**一键安装:**
```bash
pip install -r training/requirements.txt
```

---

## 常见问题

### 训练相关

**Q: 训练时 GPU 没有使用怎么办?**
A: 确认已安装 CUDA 版本的 PyTorch,检查 `device` 参数是否为 `0`。

**Q: 显存不足怎么办?**
A: 减小 `batch` 大小 (如 16),或启用 `gradient_checkpointing`。

**Q: 如何处理非标准尺寸图片?**
A: 训练脚本默认启用 `rect=True`,会自动适配不同长宽比的图片。

**Q: 如何提高精度?**
A: 增加训练轮次 (200+),使用 CBAM 注意力,增加数据量或增强。

### 部署相关

**Q: 如何更新 Android 应用中的模型?**
A: 使用 `export_pipeline.py` 导出后,替换 `assets/models/` 下的文件。

**Q: exe 打包后训练不执行?**
A: 已修复。新版本会自动检测打包模式,直接调用 ultralytics API。

**Q: GUI 界面缩放不正常?**
A: 已支持系统 DPI 自适应。如仍有问题,尝试调整系统显示缩放设置。

---

## 许可证

本项目基于 Apache License 2.0 开源 - 详见 [LICENSE](LICENSE) 文件。

---

## English

### Overview

OpenIris is an Android intelligent recognition application based on **YOLOv11** object detection model and **VLM/LLM** vision-language models. It supports real-time camera detection, image analysis, video processing, and provides AI-assisted recognition with result fusion.

### Features

| Feature | Description |
---------|-------------|
| **Real-time Detection** | Camera preview with YOLO real-time detection, 15-second sliding window statistics |
| **Image Detection** | Local image analysis, HEIF/HEIC support, high-resolution capture |
| **Video Detection** | Video frame extraction analysis, timeline results, AI-assisted recognition |
| **AI Model Management** | Multi-provider support, model list fetching, vision capability configuration |
| **Detection Results** | Three-column card display (YOLO/AI/Combined), capsule-style result UI |
| **Export Function** | JSON data export, annotated image export, configurable export paths |
| **Training Platform** | GUI training interface, custom dataset builder, model improvements |

### Tech Stack

| Component | Technology |
-----------|------------|
| Detection Model | YOLOv11 (NCNN inference engine) |
| AI Models | VLM/LLM (OpenAI-compatible API) |
| UI Framework | Material Design 3 |
| Training Framework | Ultralytics + tkinter GUI |
| Minimum Version | Android 12 (API 31) |
| Target Version | Android 15 (API 35) |

### Quick Start

#### Android App
```bash
git clone -b alpha https://github.com/your-org/OpenIris.git
cd OpenIris/buildapk
./gradlew assembleDebug
```

#### Training GUI
```bash
pip install -r training/requirements.txt
python training/run_gui.py
```

#### Build EXE
```bash
pip install pyinstaller
training\build_gui.bat
# Output: training\dist\OpenIrisTraining\OpenIrisTraining.exe
```

### Training Constraints

| Constraint | Value | Note |
------------|-------|------|
| Model | YOLOv11n | Must use nano for mobile |
| Input Size | 640 | Match NCNN inference |
| Normalization | /255, mean=0 | Ultralytics default |
| ONNX opset | ≥12 | NCNN compatibility |
| Activation | SiLU | Don't use RELU |
| Precision | FP16 | NCNN inference uses FP16 |
| Rect Training | rect=True | Auto-adapt image aspect ratios |

### License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

<div align="center">

**Made with ❤️ for Android developers**

[⬆ Back to Top](#openiris---智能目标检测与-ai-识别-android-应用)

</div>

---

## v0.5-Alpha 更新日志 (2026-06-19)

> 详细变更内容请查看 [Release v0.5-Alpha](https://github.com/Cplay00/OpenIris/releases/tag/v0.5-Alpha)

### APP 更新

- 修复暗色模式下检测结果卡片文字不可读问题
- 统一图片检测和实时检测的三个子卡片（YOLO/AI/综合分析）UI 样式
- 新增 DraggableLayout 自定义视图，修复实时检测页右侧按钮拖动问题
- 优化胶囊视图：标签名和计数加粗，置信度不加粗
- 修复综合分析平均置信度被未检测帧稀释的问题
- 新增 fromAvgConfidence() 工厂方法，提升代码清晰度
- 暗色模式子卡片使用半透明背景 + 高对比度文字
- 新增 CombinedAnalysisManager 管理综合分析逻辑
- 新增标签预设文件（COCO-80、COCO-128、VOC-20、ImageNet-1000）

### 训练工具更新

- 优化 GUI 国际化支持（i18n）
- 改进数据集验证器
- 优化训练脚本和评估脚本
- 改进导出管线

### 文档

- 新增 20+ 份审查报告和修复记录
- 更新架构流程图
- 新增待修复问题清单

---

## v0.5-Alpha 更新日志 (2026-06-19)

> 详细变更内容请查看 [Release v0.5-Alpha](https://github.com/Cplay00/OpenIris/releases/tag/v0.5-Alpha)

### 🎯 自训练模型推理支持（核心更新）

- **NCNN 推理引擎重构**：自动检测输出格式（ultralytics decoded / raw DFL），兼容内置模型和自训练模型
- **从路径加载模型**：支持从任意路径加载自训练 NCNN 模型（param + bin + labels.txt）
- **自定义模型管理**：设置页支持导入/重命名/删除自训练模型
- **标签预设文件**：新增 COCO-80、COCO-128、VOC-20、ImageNet-1000 标签文件

### APP 更新

- 修复暗色模式 UI、统一子卡片样式、修复按钮拖动、优化置信度计算
- 新增 DraggableLayout、CombinedAnalysisManager 等组件

### 训练工具更新

- 优化 GUI 国际化、数据集验证器、训练脚本、导出管线
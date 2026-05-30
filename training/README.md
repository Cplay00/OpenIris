# OpenIris YOLOv11 Training Framework

## 概述

本框架为 OpenIris 项目的 YOLOv11 模型提供完整的训练、评估和部署流水线。
支持高精度训练、自建数据集、图形化界面等功能。

## 功能特性

### 🎯 高精度训练
- 多阶段训练策略（预热 → 精细 → 最终）
- 知识蒸馏（从大模型学习）
- EMA 模型平滑
- 混合精度训练
- 测试时增强 (TTA)

### 📊 自建数据集
- 多格式标注转换（COCO/VOC/LabelMe）
- 智能数据集划分（分层划分）
- 离线数据增强
- 数据集质量校验

### 🖥️ 图形化界面
- 直观的训练配置界面
- 实时训练监控
- 一键模型导出
- 环境检测

## 目录结构

```
training/
├── configs/                        # 配置文件
│   ├── dataset_coco.yaml          # COCO 数据集配置
│   ├── dataset_custom.yaml        # 自定义数据集模板
│   ├── hyp_train.yaml             # 训练超参数（高精度）
│   └── hyp_augment.yaml           # 数据增强配置
├── scripts/                        # 核心脚本
│   ├── train.py                   # 标准训练脚本
│   ├── train_highprec.py          # 高精度训练脚本
│   ├── evaluate.py                # 模型评估
│   └── dataset_validator.py       # 数据集校验
├── tools/                          # 工具脚本
│   ├── export_pipeline.py         # 模型导出流水线
│   ├── visualize_results.py       # 训练结果可视化
│   └── dataset_builder/           # 数据集构建工具
│       ├── __init__.py
│       ├── converter.py           # 格式转换器
│       ├── splitter.py            # 数据集划分器
│       └── augmentor.py           # 数据增强器
├── gui/                            # 图形界面
│   ├── __init__.py
│   ├── main_window.py             # 主窗口
│   └── launcher.py                # 启动器 (legacy)
├── run_gui.py                      # GUI 入口 (推荐)
├── build_gui.bat                   # 打包 exe 脚本
├── build_gui.spec                  # PyInstaller 配置
├── requirements.txt                # 依赖清单
└── README.md                       # 本文件
```

## 快速开始

### 1. 环境准备

```bash
# 创建虚拟环境（推荐）
python -m venv venv
source venv/bin/activate  # Linux/Mac
# 或
venv\Scripts\activate     # Windows

# 安装 PyTorch（根据 CUDA 版本）
# CUDA 11.8
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118

# CUDA 12.1
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

# CPU only
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu

# 安装其他依赖
pip install -r training/requirements.txt
```

### 2. 启动图形界面

```bash
# 推荐入口
python training/run_gui.py

# 或使用旧入口
python training/gui/launcher.py
```

### 3. 打包为 exe (可选)

```bash
# 需要先安装 PyInstaller
pip install pyinstaller

# 运行打包脚本
training\build_gui.bat

# 输出: dist\OpenIrisTraining\OpenIrisTraining.exe
# 分发整个 dist\OpenIrisTraining 文件夹即可
```

### 3. 命令行训练

```bash
# 标准训练
python training/scripts/train.py --data configs/dataset_custom.yaml

# 高精度训练
python training/scripts/train_highprec.py \
    --data configs/dataset_custom.yaml \
    --epochs 200 --batch 32

# 知识蒸馏训练
python training/scripts/train_highprec.py \
    --data configs/dataset_custom.yaml \
    --teacher yolov11m.pt \
    --epochs 200
```

### 4. 自建数据集

```bash
# 从 COCO 格式转换
python -c "
from training.tools.dataset_builder import DatasetConverter
converter = DatasetConverter()
converter.coco_to_yolo('path/to/coco/annotations.json', 'datasets/my_dataset')
"

# 划分数据集
python -c "
from training.tools.dataset_builder import DatasetSplitter
splitter = DatasetSplitter()
splitter.split_dataset('datasets/my_dataset/images', 'datasets/my_dataset/labels', 'datasets/my_dataset_split')
"

# 数据增强
python -c "
from training.tools.dataset_builder import DatasetAugmentor
augmentor = DatasetAugmentor()
augmentor.augment_dataset(
    'datasets/my_dataset/images/train',
    'datasets/my_dataset/labels/train',
    'datasets/my_dataset_augmented',
    augmentations=['hflip', 'brightness', 'contrast'],
    augment_factor=3
)
"
```

### 5. 评估与导出

```bash
# 评估模型
python training/scripts/evaluate.py \
    --weights runs/train/openiris_highprec/weights/best.pt \
    --data configs/dataset_custom.yaml

# 导出到 Android
python training/tools/export_pipeline.py \
    --weights runs/train/openiris_highprec/weights/best.pt \
    --assets ../ncnn-android-yolov11/app/src/main/assets/models/my_model
```

## 高精度训练指南

### 训练模式

| 模式 | 轮次 | 适用场景 |
------|------|----------|
| fast | 30 | 快速验证 |
| balanced | 100 | 一般训练 |
| precision | 200+ | 高精度训练 |

### 推荐配置

#### 方案A: 单卡高精度
```yaml
mode: precision
epochs: 200
batch: 32
accumulate: 2
optimizer: AdamW
scheduler: cosine
label_smoothing: 0.02
patience: 80
```

#### 方案B: 显存受限
```yaml
mode: precision
epochs: 200
batch: 16
accumulate: 4
gradient_checkpointing: true
optimizer: AdamW
```

#### 方案C: 知识蒸馏
```yaml
mode: precision
epochs: 200
teacher: yolov11m.pt
distill_loss_weight: 0.5
```

## 数据集要求

### 目录结构
```
dataset/
├── images/
│   ├── train/
│   └── val/
├── labels/
│   ├── train/
│   └── val/
└── data.yaml
```

### 标注格式 (YOLO)
```
<class_id> <x_center> <y_center> <width> <height>
```
- class_id: 类别索引（从 0 开始）
- x_center, y_center: 中心点坐标（归一化到 0~1）
- width, height: 宽高（归一化到 0~1）

### 数据量建议

| 类别数 | 每类最少样本 | 推荐样本数 |
--------|--------------|------------|
| 1-5 | 100 | 500+ |
| 5-10 | 50 | 300+ |
| 10-20 | 30 | 200+ |
| 20+ | 20 | 100+ |

## 关键约束

| 约束项 | 值 | 说明 |
--------|-----|------|
| 模型变体 | YOLOv11n | 移动端必须用 nano |
| 输入尺寸 | 640 | 与 NCNN 推理端一致 |
| 归一化 | /255, mean=0 | ultralytics 默认 |
| ONNX opset | ≥12 | NCNN 兼容性 |
| 激活函数 | SiLU | 不建议改 RELU |
| 精度 | FP16 | NCNN 推理使用 |

## 与现有工具的关系

```
训练流程:
  train.py / train_highprec.py
         ↓
    evaluate.py (评估)
         ↓
    export_pipeline.py (导出)
         ↓
    Android assets
```

## 常见问题

### Q: 如何选择训练模式？
- **数据量 < 500**: 使用 `fast` 模式快速验证
- **数据量 500-2000**: 使用 `balanced` 模式
- **数据量 > 2000**: 使用 `precision` 模式

### Q: 显存不足怎么办？
1. 减小 `batch` 大小
2. 增加 `accumulate`（模拟大 batch）
3. 启用 `gradient_checkpointing`
4. 使用更小的输入尺寸

### Q: 如何提高精度？
1. 增加训练轮次
2. 使用知识蒸馏
3. 增加数据量或增强
4. 调整学习率和优化器
5. 使用标签平滑

### Q: 训练很慢怎么办？
1. 确保使用 GPU
2. 启用混合精度 (AMP)
3. 增加 `workers` 数量
4. 使用 SSD 存储数据集

## 依赖清单

详见 `requirements.txt`

核心依赖:
- Python >= 3.9
- PyTorch >= 2.0.0
- Ultralytics >= 8.3.0
- OpenCV >= 4.8.0

## 许可证

Apache License 2.0

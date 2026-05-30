# OpenIris 训练适配指南

## 基于 D:\YOLO11Preinit 参考环境的分析与适配

---

## 1. 参考环境分析

### 1.1 环境配置

| 项目 | 参考环境 (YOLO11Preinit) | OpenIris 项目要求 |
------|--------------------------|-------------------|
| Python | 3.13.2 (Conda) | >= 3.9 |
| PyTorch | 2.6.0+cu126 | >= 2.0.0 |
| Ultralytics | 8.3.105 | >= 8.3.0 |
| CUDA | 12.4/12.6 | 12.x |
| GPU | 未指定 | RTX 4060 Laptop (8GB) |
| 模型 | yolo11x.pt (大模型) | **yolo11n.pt** (移动端) |
| 输入尺寸 | 416-640 (不一致) | **640** (固定) |
| 优化器 | SGD | AdamW (推荐) |
| AMP | 部分禁用 | **启用** |

### 1.2 数据集

| 数据集 | 类别数 | 训练图片 | 格式 |
--------|--------|----------|------|
| Mahjong | 42 | 5,376 | Roboflow YOLO11 |
| Pig Behavior | 1 | 2,352 | Roboflow YOLO11 |
| Pig Object Detection | 3 | 844 | Roboflow YOLO11 |
| COCO8 | 2 | 4 | 测试用 |

### 1.3 训练脚本模式

参考环境使用**绝对路径**和**分离加载**模式：
```python
# 参考写法
model = YOLO('D:/YOLO11Preinit/Yolo11Pre/Lib/site-packages/ultralytics/cfg/models/11/yolo11.yaml')
model.load('yolo11x.pt')
model.train(data='D:/YOLO11Preinit/Mahjong.v57i.yolov11/data.yaml', ...)
```

---

## 2. 必须更改的内容

### 2.1 模型变体：必须使用 nano

**原因**: OpenIris 部署到 Android NCNN，必须使用轻量级模型。

| 参考 | OpenIris |
------|----------|
| `yolo11x.pt` (56.9M 参数, 196 GFLOPs) | `yolo11n.pt` (2.6M 参数, 6.6 GFLOPs) |

```python
# 参考写法（不适合移动端）
model = YOLO('yolo11x.pt')

# OpenIris 正确写法
model = YOLO('yolo11n.pt')
```

### 2.2 输入尺寸：必须固定 640

**原因**: NCNN 推理端固定使用 640，训练时必须一致。

| 参考 | OpenIris |
------|----------|
| `imgsz=416` 或 `imgsz=640` (不一致) | `imgsz=640` (固定) |

```python
# 参考写法（尺寸不一致会导致精度问题）
model.train(imgsz=416)  # Pig Behavior
model.train(imgsz=640)  # Mahjong

# OpenIris 正确写法（必须固定）
model.train(imgsz=640)
```

### 2.3 路径处理：相对路径优先

**原因**: 便于项目迁移和团队协作。

```python
# 参考写法（绝对路径，不便迁移）
data='D:/YOLO11Preinit/Mahjong.v57i.yolov11/data.yaml'

# OpenIris 正确写法（相对路径）
data='configs/dataset_custom.yaml'
# 或使用 Path 对象
from pathlib import Path
data = Path(__file__).parent.parent / 'configs' / 'dataset_custom.yaml'
```

### 2.4 优化器：推荐 AdamW

**原因**: AdamW 对移动端小模型更稳定。

| 参考 | OpenIris |
------|----------|
| `optimizer='SGD'` | `optimizer='AdamW'` |

### 2.5 AMP 必须启用

**原因**: 加速训练，节省显存，RTX 4060 完全支持。

| 参考 | OpenIris |
------|----------|
| `amp=False` (部分禁用) | `amp=True` (必须启用) |

```python
# 参考写法（不推荐）
model.train(amp=False)

# OpenIris 正确写法
model.train(amp=True)
```

---

## 3. 数据集适配

### 3.1 从 Roboflow 导出的数据集

参考环境的数据集来自 Roboflow，格式为 YOLO11，但需要调整路径。

#### Mahjong 数据集适配

```yaml
# 原始 data.yaml（相对路径）
train: ../train/images
val: ../valid/images
test: ../test/images

# OpenIris 适配后（使用绝对路径或正确的相对路径）
train: D:/YOLO11Preinit/Mahjong.v57i.yolov11/train/images
val: D:/YOLO11Preinit/Mahjong.v57i.yolov11/valid/images
test: D:/YOLO11Preinit/Mahjong.v57i.yolov11/test/images
```

### 3.2 创建 OpenIris 自定义数据集

```yaml
# training/configs/dataset_custom.yaml
path: ./datasets/your_dataset
train: images/train
val: images/val

nc: 你的类别数
names:
  0: class_0
  1: class_1
  # ...
```

### 3.3 使用参考数据集训练

如果要使用参考环境的数据集进行训练：

```bash
# 复制数据集到 OpenIris 项目
xcopy "D:\YOLO11Preinit\Mahjong.v57i.yolov11\train" "training\datasets\mahjong\images\train" /E /I
xcopy "D:\YOLO11Preinit\Mahjong.v57i.yolov11\valid" "training\datasets\mahjong\images\val" /E /I
xcopy "D:\YOLO11Preinit\Mahjong.v57i.yolov11\train\labels" "training\datasets\mahjong\labels\train" /E /I
xcopy "D:\YOLO11Preinit\Mahjong.v57i.yolov11\valid\labels" "training\datasets\mahjong\labels\val" /E /I
```

---

## 4. 硬件适配 (RTX 4060 Laptop)

### 4.1 显存限制

RTX 4060 Laptop 只有 **8GB 显存**，需要调整批次大小。

| 模型 | 参考批次 | OpenIris 推荐批次 | 说明 |
------|----------|-------------------|------|
| yolo11x | 16 | 不适用 | 大模型不适合移动端 |
| yolo11n | - | **32-48** | nano 模型显存占用小 |

### 4.2 最佳配置

```python
# RTX 4060 Laptop (8GB) 最佳配置
model.train(
    model='yolo11n.pt',
    imgsz=640,
    batch=32,        # 8GB 显存可支持
    amp=True,        # 启用混合精度，节省显存
    workers=4,       # Windows 下建议 4
    device=0,
)
```

### 4.3 显存不足时的解决方案

```python
# 方案1: 减小批次
batch=16

# 方案2: 梯度累积（模拟大批次）
batch=16
accumulate=2  # 有效 batch = 16 * 2 = 32

# 方案3: 启用梯度检查点
gradient_checkpointing=True
```

---

## 5. 训练脚本适配

### 5.1 参考脚本 vs OpenIris 脚本

| 特性 | 参考脚本 | OpenIris 脚本 |
------|----------|---------------|
| 路径 | 绝对路径 | 相对路径 |
| 模型 | yolo11x | yolo11n |
| 尺寸 | 416/640 混用 | 固定 640 |
| 优化器 | SGD | AdamW |
| AMP | 可选 | 必须启用 |
| 早停 | 无 | patience=50 |
| EMA | 无 | 启用 |
| 标签平滑 | 无 | 0.02 |

### 5.2 适配后的训练脚本

```python
# training/scripts/train_adapted.py
import warnings
warnings.filterwarnings('ignore')

from ultralytics import YOLO
from pathlib import Path

# 使用相对路径
PROJECT_ROOT = Path(__file__).parent.parent.parent

if __name__ == '__main__':
    # 使用 nano 模型（移动端必须）
    model = YOLO('yolo11n.pt')

    results = model.train(
        # 数据集配置（相对路径）
        data=str(PROJECT_ROOT / 'training' / 'configs' / 'dataset_custom.yaml'),

        # 模型配置
        imgsz=640,           # 必须固定 640
        epochs=200,
        batch=32,

        # 优化器（推荐 AdamW）
        optimizer='AdamW',
        lr0=0.001,

        # 硬件
        device=0,
        amp=True,            # 必须启用
        workers=4,           # Windows 推荐 4

        # 正则化
        label_smoothing=0.02,
        patience=50,

        # 输出
        project=str(PROJECT_ROOT / 'runs' / 'train'),
        name='openiris_v1',
    )
```

---

## 6. 环境搭建

### 6.1 方案A: 使用现有 Conda 环境

如果已安装 Anaconda/Miniconda：

```bash
# 创建新环境
conda create -n openiris python=3.11
conda activate openiris

# 安装 PyTorch (CUDA 12.x)
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

# 安装其他依赖
pip install -r training/requirements.txt
```

### 6.2 方案B: 使用 venv (无需 Anaconda)

```bash
# 创建虚拟环境
python -m venv training/venv
training\venv\Scripts\activate

# 安装 PyTorch
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

# 安装其他依赖
pip install -r training/requirements.txt
```

### 6.3 方案C: 直接使用参考环境

如果不想新建环境，可以直接使用 D:\YOLO11Preinit\Yolo11Pre：

```bash
# 激活参考环境
D:\YOLO11Preinit\Yolo11Pre\Scripts\activate

# 安装额外依赖（如果有）
pip install pyyaml matplotlib seaborn
```

---

## 7. 关键差异总结

### 必须更改

| 项目 | 参考值 | OpenIris 要求 | 原因 |
------|--------|---------------|------|
| 模型 | yolo11x | **yolo11n** | 移动端部署 |
| 输入尺寸 | 416/640 | **640** | NCNN 推理固定 |
| AMP | 可选 | **必须启用** | 性能优化 |
| 路径 | 绝对路径 | **相对路径** | 可移植性 |

### 推荐更改

| 项目 | 参考值 | OpenIris 推荐 | 原因 |
------|--------|---------------|------|
| 优化器 | SGD | **AdamW** | 小模型更稳定 |
| 学习率 | 0.01 | **0.001** | AdamW 推荐 |
| 标签平滑 | 0 | **0.02** | 防止过拟合 |
| 早停 | 无 | **patience=50** | 避免过训练 |
| EMA | 无 | **启用** | 模型平滑 |

---

## 8. 快速开始命令

```bash
# 1. 激活环境
D:\YOLO11Preinit\Yolo11Pre\Scripts\activate

# 2. 进入项目目录
cd D:\Opencode,OCCM\Items\Yolo11forAndroid

# 3. 运行适配后的训练
python training/scripts/train.py --data training/configs/dataset_custom.yaml --epochs 200

# 4. 或使用 GUI
python training/gui/launcher.py
```

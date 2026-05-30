# YOLO11 算法改进指南

## 概述

本文档介绍如何通过添加注意力机制、改进卷积模块等方式提升 YOLO11 的检测精度。
所有改进方案都针对 OpenIris 的 Android NCNN 部署进行了优化。

---

## 1. 可用的改进模块

### 1.1 注意力机制

| 模块 | 名称 | 计算量增加 | 精度提升 | 适用场景 |
------|------|------------|----------|----------|
| **SE** | Squeeze-and-Excitation | +2-5% | +1-3% | 通用，移动端友好 |
| **CBAM** | Convolutional Block Attention Module | +5-10% | +2-5% | 小目标、复杂背景 |
| **C2PSA** | Cross Stage Partial with Attention | +10-15% | +3-6% | 高精度需求 |

### 1.2 卷积改进

| 模块 | 名称 | 计算量变化 | 特点 |
------|------|------------|------|
| **GhostConv** | Ghost Convolution | -30-50% | 轻量级，移动端优化 |
| **RepConv** | Re-parameterizable Conv | 0% | 推理时融合，无额外开销 |
| **DWConv** | Depthwise Separable Conv | -60-70% | 极致轻量 |
| **LightConv** | Light Convolution | -20-30% | 平衡轻量和精度 |

### 1.3 结构改进

| 模块 | 名称 | 特点 |
------|------|------|
| **C2fCIB** | C2f with Compact Inverted Block | 增强特征提取 |
| **C3k2** | CSP Bottleneck with 2 convolutions | 标准结构，平衡性能 |
| **C3k** | CSP Bottleneck with kernel | 大感受野 |

---

## 2. 预置改进模型

### 2.1 模型配置文件

| 配置文件 | 改进内容 | 推荐场景 |
----------|----------|----------|
| `yolo11.yaml` | 原始模型 | 基准测试 |
| `yolo11-se.yaml` | +SE 注意力 | 移动端优化 |
| `yolo11-cbam.yaml` | +CBAM 注意力 | 高精度检测 |
| `yolo11-ghost.yaml` | +GhostConv | 极致轻量 |
| `yolo11-enhanced.yaml` | +CBAM +C2fCIB | 最高精度 |

### 2.2 使用方法

```python
from ultralytics import YOLO

# 使用原始模型
model = YOLO('yolo11n.pt')

# 使用改进模型（需要从 YAML 构建）
model = YOLO('training/configs/models/yolo11-cbam.yaml')
model.load('yolo11n.pt')  # 加载预训练权重

# 训练
model.train(data='configs/dataset_custom.yaml', epochs=200)
```

---

## 3. 注意力机制详解

### 3.1 SE (Squeeze-and-Excitation)

**原理**: 通过全局平均池化压缩特征，然后通过全连接层学习通道权重。

```python
class SEBlock(nn.Module):
    def __init__(self, in_channels, reduction=16):
        super().__init__()
        self.avg_pool = nn.AdaptiveAvgPool2d(1)
        self.fc = nn.Sequential(
            nn.Linear(in_channels, in_channels // reduction),
            nn.ReLU(),
            nn.Linear(in_channels // reduction, in_channels),
            nn.Sigmoid()
        )

    def forward(self, x):
        b, c, _, _ = x.size()
        y = self.avg_pool(x).view(b, c)
        y = self.fc(y).view(b, c, 1, 1)
        return x * y
```

**优点**:
- 计算量增加极小 (+2-5%)
- 易于集成
- 对通道特征进行自适应加权

**缺点**:
- 只关注通道维度，忽略空间信息

**适用场景**:
- 移动端部署
- 计算资源受限
- 通用目标检测

### 3.2 CBAM (Convolutional Block Attention Module)

**原理**: 结合通道注意力和空间注意力，先通道后空间。

```python
class CBAMBlock(nn.Module):
    def __init__(self, in_channels, reduction=16, kernel_size=7):
        super().__init__()
        self.channel_attention = SEBlock(in_channels, reduction)
        self.spatial_attention = nn.Sequential(
            nn.Conv2d(2, 1, kernel_size, padding=kernel_size//2),
            nn.Sigmoid()
        )

    def forward(self, x):
        x = self.channel_attention(x)  # 通道注意力
        max_pool = torch.max(x, dim=1, keepdim=True)[0]
        avg_pool = torch.mean(x, dim=1, keepdim=True)
        spatial_input = torch.cat([max_pool, avg_pool], dim=1)
        spatial_weight = self.spatial_attention(spatial_input)
        return x * spatial_weight  # 空间注意力
```

**优点**:
- 同时关注通道和空间维度
- 对小目标检测效果好
- 提升复杂背景下的检测精度

**缺点**:
- 计算量增加较多 (+5-10%)
- 空间注意力需要额外的卷积操作

**适用场景**:
- 小目标检测
- 复杂背景
- 高精度需求

### 3.3 C2PSA (Cross Stage Partial with Attention)

**原理**: YOLO11 原生的注意力机制，结合了 PSA (Pyramid Squeeze Attention)。

**优点**:
- 原生支持，无需修改代码
- 性能稳定
- 计算效率高

**适用场景**:
- 通用检测任务
- 需要稳定性能的场景

---

## 4. 卷积改进详解

### 4.1 GhostConv

**原理**: 使用少量卷积核生成特征图，然后通过线性变换生成"幽灵"特征图。

```python
class GhostConv(nn.Module):
    def __init__(self, in_channels, out_channels, kernel_size=1, ratio=2):
        super().__init__()
        init_channels = math.ceil(out_channels / ratio)
        new_channels = init_channels * (ratio - 1)
        self.primary_conv = nn.Conv2d(in_channels, init_channels, kernel_size, ...)
        self.cheap_operation = nn.Conv2d(init_channels, new_channels, kernel_size-1, ...)
```

**优点**:
- 计算量减少 30-50%
- 模型体积减小
- 推理速度提升

**缺点**:
- 精度略有下降
- 特征表达能力受限

**适用场景**:
- 极致移动端优化
- 实时性要求高
- 计算资源极度受限

### 4.2 RepConv (Re-parameterizable Convolution)

**原理**: 训练时使用多分支结构，推理时融合为单分支。

**优点**:
- 推理时无额外开销
- 训练时提升特征表达能力
- 兼容现有部署框架

**适用场景**:
- 需要精度提升但不想增加推理开销
- 支持模型重参数化的部署环境

---

## 5. 改进方案推荐

### 5.1 移动端部署（推荐）

```yaml
# 使用 SE 注意力
# 训练配置
model: training/configs/models/yolo11-se.yaml
imgsz: 640
batch: 32
optimizer: AdamW
amp: true
```

**预期效果**:
- 精度提升: +1-3% mAP
- 速度影响: <5%
- 模型大小: 增加 <3%

### 5.2 高精度检测

```yaml
# 使用 CBAM 注意力
model: training/configs/models/yolo11-cbam.yaml
imgsz: 640
batch: 32
optimizer: AdamW
amp: true
epochs: 200
```

**预期效果**:
- 精度提升: +2-5% mAP
- 速度影响: 5-10%
- 模型大小: 增加 5-8%

### 5.3 极致轻量

```yaml
# 使用 GhostConv
model: training/configs/models/yolo11-ghost.yaml
imgsz: 640
batch: 32
optimizer: AdamW
amp: true
```

**预期效果**:
- 精度变化: -1-2% mAP
- 速度提升: 20-30%
- 模型大小: 减少 20-30%

### 5.4 最高精度

```yaml
# 使用综合改进
model: training/configs/models/yolo11-enhanced.yaml
imgsz: 640
batch: 16
accumulate: 2
optimizer: AdamW
amp: true
epochs: 300
```

**预期效果**:
- 精度提升: +3-6% mAP
- 速度影响: 15-25%
- 模型大小: 增加 10-15%

---

## 6. 自定义改进模块

### 6.1 添加自定义注意力机制

```python
# 1. 在 ultralytics/nn/modules/block.py 中添加
class MyAttention(nn.Module):
    def __init__(self, channels):
        super().__init__()
        # 你的实现

    def forward(self, x):
        # 你的实现
        return x

# 2. 在 __init__.py 中导出
from .block import MyAttention

# 3. 在 YAML 中使用
# backbone:
#   - [-1, 1, MyAttention, [512]]
```

### 6.2 修改现有模块

```python
# 修改 C3k2 添加注意力
class C3k2WithAttention(C2f):
    def __init__(self, c1, c2, n=1, shortcut=False, g=1, e=0.5):
        super().__init__(c1, c2, n, shortcut, g, e)
        self.attention = CBAM(c2)  # 添加 CBAM

    def forward(self, x):
        x = super().forward(x)
        return self.attention(x)
```

---

## 7. 注意事项

### 7.1 移动端部署限制

1. **避免使用复杂的注意力机制** (如 Transformer)
2. **控制模型大小** - nano 模型 + 轻量注意力
3. **保持输入尺寸一致** - 固定 640
4. **测试推理速度** - 确保满足实时性要求

### 7.2 训练建议

1. **先用原始模型建立基准**
2. **逐步添加改进** - 不要一次添加太多
3. **监控训练曲线** - 确保改进有效
4. **对比实验** - 量化改进效果

### 7.3 NCNN 兼容性

1. **确保自定义模块支持 ONNX 导出**
2. **测试 NCNN 转换** - 某些操作可能不支持
3. **验证推理结果** - 确保精度一致

---

## 8. 快速开始

```bash
# 1. 使用 CBAM 改进模型训练
python training/scripts/train.py \
    --model training/configs/models/yolo11-cbam.yaml \
    --data configs/dataset_custom.yaml \
    --epochs 200

# 2. 使用改进模型训练（带预训练权重）
python -c "
from ultralytics import YOLO
model = YOLO('training/configs/models/yolo11-cbam.yaml')
model.load('yolo11n.pt')
model.train(data='configs/dataset_custom.yaml', epochs=200, imgsz=640)
"

# 3. 导出改进模型
python training/tools/export_pipeline.py \
    --weights runs/train/exp/weights/best.pt \
    --assets ncnn-android-yolov11/app/src/main/assets/models/improved
```

---

## 9. 性能对比

| 模型 | mAP50 | mAP50-95 | 参数量 | GFLOPs | 推理速度 |
------|-------|----------|--------|--------|----------|
| yolo11n (原始) | - | - | 2.6M | 6.6 | 基准 |
| yolo11n + SE | +1-3% | +1-2% | 2.7M | 6.8 | -2-5% |
| yolo11n + CBAM | +2-5% | +2-4% | 2.8M | 7.2 | -5-10% |
| yolo11n + Ghost | -1-2% | -1-2% | 1.8M | 4.5 | +20-30% |
| yolo11n + Enhanced | +3-6% | +3-5% | 3.0M | 7.8 | -15-25% |

*注: 实际效果取决于数据集和任务复杂度*

# 依赖对比分析

## D:\YOLO11Preinit 参考环境 vs OpenIris 项目需求

---

## 1. 环境对比

### 1.1 Python 环境

| 项目 | 参考环境 (YOLO11Pre) | OpenIris 要求 | 状态 |
------|----------------------|---------------|------|
| Python | 3.13.2 | >= 3.9 | ✅ 兼容 |
| 环境管理 | Conda | Conda/venv | ✅ 可用 |
| 安装位置 | D:\YOLO11Preinit\Yolo11Pre | 项目内或系统 | - |

### 1.2 核心依赖

| 包名 | 参考环境版本 | OpenIris 要求 | 状态 |
------|-------------|---------------|------|
| PyTorch | 2.6.0+cu126 | >= 2.0.0 | ✅ 满足 |
| torchvision | 0.21.0+cu126 | >= 0.15.0 | ✅ 满足 |
| ultralytics | 8.3.105 | >= 8.3.0 | ✅ 满足 |
| OpenCV | 已安装 | >= 4.8.0 | ✅ 满足 |
| NumPy | 已安装 | >= 1.24.0 | ✅ 满足 |
| CUDA | 12.4/12.6 | 12.x | ✅ 满足 |

### 1.3 可选依赖

| 包名 | 参考环境 | OpenIris 需要 | 状态 |
------|----------|---------------|------|
| PyYAML | 可能已安装 | 需要 | ⚠️ 需检查 |
| matplotlib | 可能已安装 | 需要 | ⚠️ 需检查 |
| seaborn | 未确认 | 可选 | ⚠️ 需安装 |
| pandas | 未确认 | 需要 | ⚠️ 需检查 |
| scikit-learn | 未确认 | 可选 | ⚠️ 需安装 |
| tensorboard | 未确认 | 可选 | ⚠️ 需安装 |

---

## 2. 硬件对比

### 2.1 GPU 配置

| 项目 | 参考环境假设 | 当前机器 | 建议 |
------|-------------|----------|------|
| GPU | 未知 | RTX 4060 Laptop | - |
| 显存 | 未知 | 8GB | batch=32 |
| CUDA | 12.4/12.6 | 13.3 (UMD) | ✅ 兼容 |
| 计算能力 | 未知 | 8.9 | ✅ 支持 |

### 2.2 训练配置建议

| 配置项 | 参考环境 | RTX 4060 建议 | 说明 |
--------|----------|---------------|------|
| batch | 8-64 | **32** | 8GB 显存最佳 |
| amp | 可选 | **True** | 必须启用 |
| workers | 0-16 | **4** | Windows 推荐 |
| imgsz | 416-640 | **640** | 固定值 |

---

## 3. 缺失依赖安装指南

### 3.1 使用参考环境（推荐）

```bash
# 激活参考环境
D:\YOLO11Preinit\Yolo11Pre\Scripts\activate

# 检查已安装的包
pip list

# 安装缺失的包
pip install pyyaml matplotlib seaborn pandas scikit-learn
```

### 3.2 创建新环境（备选）

```bash
# 方案A: 使用 Conda
conda create -n openiris python=3.11
conda activate openiris
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install ultralytics opencv-python pyyaml matplotlib seaborn pandas scikit-learn

# 方案B: 使用 venv
python -m venv training/venv
training\venv\Scripts\activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install ultralytics opencv-python pyyaml matplotlib seaborn pandas scikit-learn
```

---

## 4. 依赖检查脚本

```python
# training/scripts/check_dependencies.py
import sys
import importlib

def check_package(name, min_version=None):
    try:
        module = importlib.import_module(name)
        version = getattr(module, '__version__', 'unknown')
        status = '✅'
        if min_version and version != 'unknown':
            from packaging.version import Version
            if Version(version) < Version(min_version):
                status = '⚠️'
        return status, version
    except ImportError:
        return '❌', 'not installed'

# 检查列表
packages = [
    ('torch', '2.0.0'),
    ('torchvision', '0.15.0'),
    ('ultralytics', '8.3.0'),
    ('cv2', '4.8.0'),
    ('numpy', '1.24.0'),
    ('yaml', None),
    ('matplotlib', '3.7.0'),
    ('pandas', '2.0.0'),
]

print("依赖检查结果:")
print("-" * 50)
for name, min_ver in packages:
    status, version = check_package(name, min_ver)
    print(f"{status} {name}: {version}")
```

---

## 5. 常见问题

### Q1: 参考环境的 Python 版本太高怎么办？

OpenIris 要求 Python >= 3.9，参考环境的 3.13.2 完全兼容，无需降级。

### Q2: 参考环境的 PyTorch 版本太新怎么办？

参考环境的 PyTorch 2.6.0 完全满足 OpenIris 的 >= 2.0.0 要求，且包含最新优化。

### Q3: 如何确认 CUDA 是否正常工作？

```python
import torch
print(f"CUDA available: {torch.cuda.is_available()}")
print(f"CUDA version: {torch.version.cuda}")
print(f"GPU: {torch.cuda.get_device_name(0)}")
print(f"GPU memory: {torch.cuda.get_device_properties(0).total_mem / 1024**3:.1f} GB")
```

### Q4: 参考环境没有安装 matplotlib 怎么办？

```bash
pip install matplotlib seaborn
```

### Q5: 参考环境的 ultralytics 版本太旧怎么办？

```bash
pip install --upgrade ultralytics
```

---

## 6. 总结

### 参考环境可直接使用

参考环境 (D:\YOLO11Preinit\Yolo11Pre) 满足 OpenIris 的所有核心依赖要求，只需安装少量可选依赖即可。

### 需要安装的额外依赖

```bash
pip install pyyaml matplotlib seaborn pandas scikit-learn
```

### 需要更改的配置

1. **模型**: yolo11x → yolo11n
2. **输入尺寸**: 416/640 → 固定 640
3. **优化器**: SGD → AdamW
4. **AMP**: 可选 → 必须启用
5. **路径**: 绝对路径 → 相对路径

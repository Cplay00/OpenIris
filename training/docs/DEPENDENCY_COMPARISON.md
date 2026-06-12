# 渚濊禆瀵规瘮鍒嗘瀽

## <your-training-root> 鍙傝€冪幆澧?vs OpenIris 椤圭洰闇€姹?

---

## 1. 鐜瀵规瘮

### 1.1 Python 鐜

| 椤圭洰 | 鍙傝€冪幆澧?(YOLO11Pre) | OpenIris 瑕佹眰 | 鐘舵€?|
------|----------------------|---------------|------|
| Python | 3.13.2 | >= 3.9 | 鉁?鍏煎 |
| 鐜绠$悊 | Conda | Conda/venv | 鉁?鍙敤 |
| 瀹夎浣嶇疆 | <your-training-env> | 椤圭洰鍐呮垨绯荤粺 | - |

### 1.2 鏍稿績渚濊禆

| 鍖呭悕 | 鍙傝€冪幆澧冪増鏈?| OpenIris 瑕佹眰 | 鐘舵€?|
------|-------------|---------------|------|
| PyTorch | 2.6.0+cu126 | >= 2.0.0 | 鉁?婊¤冻 |
| torchvision | 0.21.0+cu126 | >= 0.15.0 | 鉁?婊¤冻 |
| ultralytics | 8.3.105 | >= 8.3.0 | 鉁?婊¤冻 |
| OpenCV | 宸插畨瑁?| >= 4.8.0 | 鉁?婊¤冻 |
| NumPy | 宸插畨瑁?| >= 1.24.0 | 鉁?婊¤冻 |
| CUDA | 12.4/12.6 | 12.x | 鉁?婊¤冻 |

### 1.3 鍙€変緷璧?

| 鍖呭悕 | 鍙傝€冪幆澧?| OpenIris 闇€瑕?| 鐘舵€?|
------|----------|---------------|------|
| PyYAML | 鍙兘宸插畨瑁?| 闇€瑕?| 鈿狅笍 闇€妫€鏌?|
| matplotlib | 鍙兘宸插畨瑁?| 闇€瑕?| 鈿狅笍 闇€妫€鏌?|
| seaborn | 鏈'璁?| 鍙€?| 鈿狅笍 闇€瀹夎 |
| pandas | 鏈'璁?| 闇€瑕?| 鈿狅笍 闇€妫€鏌?|
| scikit-learn | 鏈'璁?| 鍙€?| 鈿狅笍 闇€瀹夎 |
| tensorboard | 鏈'璁?| 鍙€?| 鈿狅笍 闇€瀹夎 |

---

## 2. 纭欢瀵规瘮

### 2.1 GPU 閰嶇疆

| 椤圭洰 | 鍙傝€冪幆澧冨亣璁?| 褰撳墠鏈哄櫒 | 寤鸿 |
------|-------------|----------|------|
| GPU | 鏈煡 | RTX 4060 Laptop | - |
| 鏄惧瓨 | 鏈煡 | 8GB | batch=32 |
| CUDA | 12.4/12.6 | 13.3 (UMD) | 鉁?鍏煎 |
| 璁$畻鑳藉姏 | 鏈煡 | 8.9 | 鉁?鏀寔 |

### 2.2 璁粌閰嶇疆寤鸿

| 閰嶇疆椤?| 鍙傝€冪幆澧?| RTX 4060 寤鸿 | 璇存槑 |
--------|----------|---------------|------|
| batch | 8-64 | **32** | 8GB 鏄惧瓨鏈€浣?|
| amp | 鍙€?| **True** | 蹇呴』鍚敤 |
| workers | 0-16 | **4** | Windows 鎺ㄨ崘 |
| imgsz | 416-640 | **640** | 鍥哄畾鍊?|

---

## 3. 缂哄け渚濊禆瀹夎鎸囧崡

### 3.1 浣跨敤鍙傝€冪幆澧冿紙鎺ㄨ崘锛?

```bash
# 婵€娲诲弬鑰冪幆澧?
<your-training-env>\Scripts\activate

# 妫€鏌ュ凡瀹夎鐨勫寘
pip list

# 瀹夎缂哄け鐨勫寘
pip install pyyaml matplotlib seaborn pandas scikit-learn
```

### 3.2 鍒涘缓鏂扮幆澧冿紙澶囬€夛級

```bash
# 鏂规A: 浣跨敤 Conda
conda create -n openiris python=3.11
conda activate openiris
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install ultralytics opencv-python pyyaml matplotlib seaborn pandas scikit-learn

# 鏂规B: 浣跨敤 venv
python -m venv training/venv
training\venv\Scripts\activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install ultralytics opencv-python pyyaml matplotlib seaborn pandas scikit-learn
```

---

## 4. 渚濊禆妫€鏌ヨ剼鏈?

```python
# training/scripts/check_dependencies.py
import sys
import importlib

def check_package(name, min_version=None):
    try:
        module = importlib.import_module(name)
        version = getattr(module, '__version__', 'unknown')
        status = '鉁?
        if min_version and version != 'unknown':
            from packaging.version import Version
            if Version(version) < Version(min_version):
                status = '鈿狅笍'
        return status, version
    except ImportError:
        return '鉂?, 'not installed'

# 妫€鏌ュ垪琛?
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

print("渚濊禆妫€鏌ョ粨鏋?")
print("-" * 50)
for name, min_ver in packages:
    status, version = check_package(name, min_ver)
    print(f"{status} {name}: {version}")
```

---

## 5. 甯歌闂

### Q1: 鍙傝€冪幆澧冪殑 Python 鐗堟湰澶珮鎬庝箞鍔烇紵

OpenIris 瑕佹眰 Python >= 3.9锛屽弬鑰冪幆澧冪殑 3.13.2 瀹屽叏鍏煎锛屾棤闇€闄嶇骇銆?

### Q2: 鍙傝€冪幆澧冪殑 PyTorch 鐗堟湰澶柊鎬庝箞鍔烇紵

鍙傝€冪幆澧冪殑 PyTorch 2.6.0 瀹屽叏婊¤冻 OpenIris 鐨?>= 2.0.0 瑕佹眰锛屼笖鍖呭惈鏈€鏂颁紭鍖栥€?

### Q3: 濡備綍纭 CUDA 鏄惁姝e父宸ヤ綔锛?

```python
import torch
print(f"CUDA available: {torch.cuda.is_available()}")
print(f"CUDA version: {torch.version.cuda}")
print(f"GPU: {torch.cuda.get_device_name(0)}")
print(f"GPU memory: {torch.cuda.get_device_properties(0).total_mem / 1024**3:.1f} GB")
```

### Q4: 鍙傝€冪幆澧冩病鏈夊畨瑁?matplotlib 鎬庝箞鍔烇紵

```bash
pip install matplotlib seaborn
```

### Q5: 鍙傝€冪幆澧冪殑 ultralytics 鐗堟湰澶棫鎬庝箞鍔烇紵

```bash
pip install --upgrade ultralytics
```

---

## 6. 鎬荤粨

### 鍙傝€冪幆澧冨彲鐩存帴浣跨敤

鍙傝€冪幆澧?(<your-training-env>) 婊¤冻 OpenIris 鐨勬墍鏈夋牳蹇冧緷璧栬姹傦紝鍙渶瀹夎灏戦噺鍙€変緷璧栧嵆鍙€?

### 闇€瑕佸畨瑁呯殑棰濆渚濊禆

```bash
pip install pyyaml matplotlib seaborn pandas scikit-learn
```

### 闇€瑕佹洿鏀圭殑閰嶇疆

1. **妯″瀷**: yolo11x 鈫?yolo11n
2. **杈撳叆灏哄**: 416/640 鈫?鍥哄畾 640
3. **浼樺寲鍣?*: SGD 鈫?AdamW
4. **AMP**: 鍙€?鈫?蹇呴』鍚敤
5. **璺緞**: 缁濆璺緞 鈫?鐩稿璺緞


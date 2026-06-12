# OpenIris 璁粌閫傞厤鎸囧崡

## 鍩轰簬 <your-training-root> 鍙傝€冪幆澧冪殑鍒嗘瀽涓庨€傞厤

---

## 1. 鍙傝€冪幆澧冨垎鏋?

### 1.1 鐜閰嶇疆

| 椤圭洰 | 鍙傝€冪幆澧?(TrainingEnv) | OpenIris 椤圭洰瑕佹眰 |
------|--------------------------|-------------------|
| Python | 3.13.2 (Conda) | >= 3.9 |
| PyTorch | 2.6.0+cu126 | >= 2.0.0 |
| Ultralytics | 8.3.105 | >= 8.3.0 |
| CUDA | 12.4/12.6 | 12.x |
| GPU | 鏈寚瀹?| RTX 4060 Laptop (8GB) |
| 妯″瀷 | yolo11x.pt (澶фā鍨? | **yolo11n.pt** (绉诲姩绔? |
| 杈撳叆灏哄 | 416-640 (涓嶄竴鑷? | **640** (鍥哄畾) |
| 浼樺寲鍣?| SGD | AdamW (鎺ㄨ崘) |
| AMP | 閮ㄥ垎绂佺敤 | **鍚敤** |

### 1.2 鏁版嵁闆?

| 鏁版嵁闆?| 绫诲埆鏁?| 璁粌鍥剧墖 | 鏍煎紡 |
--------|--------|----------|------|
| Mahjong | 42 | 5,376 | Roboflow YOLO11 |
| Pig Behavior | 1 | 2,352 | Roboflow YOLO11 |
| Pig Object Detection | 3 | 844 | Roboflow YOLO11 |
| COCO8 | 2 | 4 | 娴嬭瘯鐢?|

### 1.3 璁粌鑴氭湰妯″紡

鍙傝€冪幆澧冧娇鐢?*缁濆璺緞**鍜?*鍒嗙鍔犺浇**妯″紡锛?
```python
# 鍙傝€冨啓娉?
model = YOLO('<your-training-env>/Lib/site-packages/ultralytics/cfg/models/11/yolo11.yaml')
model.load('yolo11x.pt')
model.train(data='<your-dataset-path>/data.yaml', ...)
```

---

## 2. 蹇呴』鏇存敼鐨勫唴瀹?

### 2.1 妯″瀷鍙樹綋锛氬繀椤讳娇鐢?nano

**鍘熷洜**: OpenIris 閮ㄧ讲鍒?Android NCNN锛屽繀椤讳娇鐢ㄨ交閲忕骇妯″瀷銆?

| 鍙傝€?| OpenIris |
------|----------|
| `yolo11x.pt` (56.9M 鍙傛暟, 196 GFLOPs) | `yolo11n.pt` (2.6M 鍙傛暟, 6.6 GFLOPs) |

```python
# 鍙傝€冨啓娉曪紙涓嶉€傚悎绉诲姩绔級
model = YOLO('yolo11x.pt')

# OpenIris 姝g'鍐欐硶
model = YOLO('yolo11n.pt')
```

### 2.2 杈撳叆灏哄锛氬繀椤诲浐瀹?640

**鍘熷洜**: NCNN 鎺ㄧ悊绔浐瀹氫娇鐢?640锛岃缁冩椂蹇呴』涓€鑷淬€?

| 鍙傝€?| OpenIris |
------|----------|
| `imgsz=416` 鎴?`imgsz=640` (涓嶄竴鑷? | `imgsz=640` (鍥哄畾) |

```python
# 鍙傝€冨啓娉曪紙灏哄涓嶄竴鑷翠細瀵艰嚧绮惧害闂锛?
model.train(imgsz=416)  # Pig Behavior
model.train(imgsz=640)  # Mahjong

# OpenIris 姝g'鍐欐硶锛堝繀椤诲浐瀹氾級
model.train(imgsz=640)
```

### 2.3 璺緞澶勭悊锛氱浉瀵硅矾寰勪紭鍏?

**鍘熷洜**: 渚夸簬椤圭洰杩佺Щ鍜屽洟闃熷崗浣溿€?

```python
# 鍙傝€冨啓娉曪紙缁濆璺緞锛屼笉渚胯縼绉伙級
data='<your-dataset-path>/data.yaml'

# OpenIris 姝g'鍐欐硶锛堢浉瀵硅矾寰勶級
data='configs/dataset_custom.yaml'
# 鎴栦娇鐢?Path 瀵硅薄
from pathlib import Path
data = Path(__file__).parent.parent / 'configs' / 'dataset_custom.yaml'
```

### 2.4 浼樺寲鍣細鎺ㄨ崘 AdamW

**鍘熷洜**: AdamW 瀵圭Щ鍔ㄧ灏忔ā鍨嬫洿绋冲畾銆?

| 鍙傝€?| OpenIris |
------|----------|
| `optimizer='SGD'` | `optimizer='AdamW'` |

### 2.5 AMP 蹇呴』鍚敤

**鍘熷洜**: 鍔犻€熻缁冿紝鑺傜渷鏄惧瓨锛孯TX 4060 瀹屽叏鏀寔銆?

| 鍙傝€?| OpenIris |
------|----------|
| `amp=False` (閮ㄥ垎绂佺敤) | `amp=True` (蹇呴』鍚敤) |

```python
# 鍙傝€冨啓娉曪紙涓嶆帹鑽愶級
model.train(amp=False)

# OpenIris 姝g'鍐欐硶
model.train(amp=True)
```

---

## 3. 鏁版嵁闆嗛€傞厤

### 3.1 浠?Roboflow 瀵煎嚭鐨勬暟鎹泦

鍙傝€冪幆澧冪殑鏁版嵁闆嗘潵鑷?Roboflow锛屾牸寮忎负 YOLO11锛屼絾闇€瑕佽皟鏁磋矾寰勩€?

#### Mahjong 鏁版嵁闆嗛€傞厤

```yaml
# 鍘熷 data.yaml锛堢浉瀵硅矾寰勶級
train: ../train/images
val: ../valid/images
test: ../test/images

# OpenIris 閫傞厤鍚庯紙浣跨敤缁濆璺緞鎴栨纭殑鐩稿璺緞锛?
train: <your-dataset-path>/train/images
val: <your-dataset-path>/valid/images
test: <your-dataset-path>/test/images
```

### 3.2 鍒涘缓 OpenIris 鑷畾涔夋暟鎹泦

```yaml
# training/configs/dataset_custom.yaml
path: ./datasets/your_dataset
train: images/train
val: images/val

nc: 浣犵殑绫诲埆鏁?
names:
  0: class_0
  1: class_1
  # ...
```

### 3.3 浣跨敤鍙傝€冩暟鎹泦璁粌

濡傛灉瑕佷娇鐢ㄥ弬鑰冪幆澧冪殑鏁版嵁闆嗚繘琛岃缁冿細

```bash
# 澶嶅埗鏁版嵁闆嗗埌 OpenIris 椤圭洰
xcopy "<your-dataset-path>\train" "training\datasets\mahjong\images\train" /E /I
xcopy "<your-dataset-path>\valid" "training\datasets\mahjong\images\val" /E /I
xcopy "<your-dataset-path>\train\labels" "training\datasets\mahjong\labels\train" /E /I
xcopy "<your-dataset-path>\valid\labels" "training\datasets\mahjong\labels\val" /E /I
```

---

## 4. 纭欢閫傞厤 (RTX 4060 Laptop)

### 4.1 鏄惧瓨闄愬埗

RTX 4060 Laptop 鍙湁 **8GB 鏄惧瓨**锛岄渶瑕佽皟鏁存壒娆″ぇ灏忋€?

| 妯″瀷 | 鍙傝€冩壒娆?| OpenIris 鎺ㄨ崘鎵规 | 璇存槑 |
------|----------|-------------------|------|
| yolo11x | 16 | 涓嶉€傜敤 | 澶фā鍨嬩笉閫傚悎绉诲姩绔?|
| yolo11n | - | **32-48** | nano 妯″瀷鏄惧瓨鍗犵敤灏?|

### 4.2 鏈€浣抽厤缃?

```python
# RTX 4060 Laptop (8GB) 鏈€浣抽厤缃?
model.train(
    model='yolo11n.pt',
    imgsz=640,
    batch=32,        # 8GB 鏄惧瓨鍙敮鎸?
    amp=True,        # 鍚敤娣峰悎绮惧害锛岃妭鐪佹樉瀛?
    workers=4,       # Windows 涓嬪缓璁?4
    device=0,
)
```

### 4.3 鏄惧瓨涓嶈冻鏃剁殑瑙e喅鏂规

```python
# 鏂规1: 鍑忓皬鎵规
batch=16

# 鏂规2: 姊害绱Н锛堟ā鎷熷ぇ鎵规锛?
batch=16
accumulate=2  # 鏈夋晥 batch = 16 * 2 = 32

# 鏂规3: 鍚敤姊害妫€鏌ョ偣
gradient_checkpointing=True
```

---

## 5. 璁粌鑴氭湰閫傞厤

### 5.1 鍙傝€冭剼鏈?vs OpenIris 鑴氭湰

| 鐗规€?| 鍙傝€冭剼鏈?| OpenIris 鑴氭湰 |
------|----------|---------------|
| 璺緞 | 缁濆璺緞 | 鐩稿璺緞 |
| 妯″瀷 | yolo11x | yolo11n |
| 灏哄 | 416/640 娣风敤 | 鍥哄畾 640 |
| 浼樺寲鍣?| SGD | AdamW |
| AMP | 鍙€?| 蹇呴』鍚敤 |
| 鏃╁仠 | 鏃?| patience=50 |
| EMA | 鏃?| 鍚敤 |
| 鏍囩骞虫粦 | 鏃?| 0.02 |

### 5.2 閫傞厤鍚庣殑璁粌鑴氭湰

```python
# training/scripts/train_adapted.py
import warnings
warnings.filterwarnings('ignore')

from ultralytics import YOLO
from pathlib import Path

# 浣跨敤鐩稿璺緞
PROJECT_ROOT = Path(__file__).parent.parent.parent

if __name__ == '__main__':
    # 浣跨敤 nano 妯″瀷锛堢Щ鍔ㄧ蹇呴』锛?
    model = YOLO('yolo11n.pt')

    results = model.train(
        # 鏁版嵁闆嗛厤缃紙鐩稿璺緞锛?
        data=str(PROJECT_ROOT / 'training' / 'configs' / 'dataset_custom.yaml'),

        # 妯″瀷閰嶇疆
        imgsz=640,           # 蹇呴』鍥哄畾 640
        epochs=200,
        batch=32,

        # 浼樺寲鍣紙鎺ㄨ崘 AdamW锛?
        optimizer='AdamW',
        lr0=0.001,

        # 纭欢
        device=0,
        amp=True,            # 蹇呴』鍚敤
        workers=4,           # Windows 鎺ㄨ崘 4

        # 姝e垯鍖?
        label_smoothing=0.02,
        patience=50,

        # 杈撳嚭
        project=str(PROJECT_ROOT / 'runs' / 'train'),
        name='openiris_v1',
    )
```

---

## 6. 鐜鎼缓

### 6.1 鏂规A: 浣跨敤鐜版湁 Conda 鐜

濡傛灉宸插畨瑁?Anaconda/Miniconda锛?

```bash
# 鍒涘缓鏂扮幆澧?
conda create -n openiris python=3.11
conda activate openiris

# 瀹夎 PyTorch (CUDA 12.x)
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

# 瀹夎鍏朵粬渚濊禆
pip install -r training/requirements.txt
```

### 6.2 鏂规B: 浣跨敤 venv (鏃犻渶 Anaconda)

```bash
# 鍒涘缓铏氭嫙鐜
python -m venv training/venv
training\venv\Scripts\activate

# 瀹夎 PyTorch
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121

# 瀹夎鍏朵粬渚濊禆
pip install -r training/requirements.txt
```

### 6.3 鏂规C: 鐩存帴浣跨敤鍙傝€冪幆澧?

濡傛灉涓嶆兂鏂板缓鐜锛屽彲浠ョ洿鎺ヤ娇鐢?<your-training-env>锛?

```bash
# 婵€娲诲弬鑰冪幆澧?
<your-training-env>\Scripts\activate

# 瀹夎棰濆渚濊禆锛堝鏋滄湁锛?
pip install pyyaml matplotlib seaborn
```

---

## 7. 鍏抽敭宸紓鎬荤粨

### 蹇呴』鏇存敼

| 椤圭洰 | 鍙傝€冨€?| OpenIris 瑕佹眰 | 鍘熷洜 |
------|--------|---------------|------|
| 妯″瀷 | yolo11x | **yolo11n** | 绉诲姩绔儴缃?|
| 杈撳叆灏哄 | 416/640 | **640** | NCNN 鎺ㄧ悊鍥哄畾 |
| AMP | 鍙€?| **蹇呴』鍚敤** | 鎬ц兘浼樺寲 |
| 璺緞 | 缁濆璺緞 | **鐩稿璺緞** | 鍙Щ妞嶆€?|

### 鎺ㄨ崘鏇存敼

| 椤圭洰 | 鍙傝€冨€?| OpenIris 鎺ㄨ崘 | 鍘熷洜 |
------|--------|---------------|------|
| 浼樺寲鍣?| SGD | **AdamW** | 灏忔ā鍨嬫洿绋冲畾 |
| 瀛︿範鐜?| 0.01 | **0.001** | AdamW 鎺ㄨ崘 |
| 鏍囩骞虫粦 | 0 | **0.02** | 闃叉杩囨嫙鍚?|
| 鏃╁仠 | 鏃?| **patience=50** | 閬垮厤杩囪缁?|
| EMA | 鏃?| **鍚敤** | 妯″瀷骞虫粦 |

---

## 8. 蹇€熷紑濮嬪懡浠?

```bash
# 1. 婵€娲荤幆澧?
<your-training-env>\Scripts\activate

# 2. 杩涘叆椤圭洰鐩綍
cd D:\Opencode,OCCM\Items\Yolo11forAndroid

# 3. 杩愯閫傞厤鍚庣殑璁粌
python training/scripts/train.py --data training/configs/dataset_custom.yaml --epochs 200

# 4. 鎴栦娇鐢?GUI
python training/gui/launcher.py
```



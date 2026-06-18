# OpenIris APP NCNN 推理全链路深度审查报告

**审查日期**: 2026-06-16  
**审查范围**: `buildapk/app/src/main/jni/` + `buildapk/app/src/main/java/com/yolo/openiris/`  
**审查目标**: 定位并修复用户自训练模型导入后出现的三个严重 bug  

---

## 一、问题现象总览

| # | 现象 | 严重程度 |
---|------|----------|
1 | 所有标签都是 "unknown" | 🔴 关键 |
2 | 框图总在左上角（位置完全错误） | 🔴 关键 |
3 | 置信度达 100% | 🔴 关键 |

**根本原因一句话**: ultralytics NCNN 导出的模型已完成全部后处理（DFL 解码 + sigmoid），输出 `[8400, 5]`，但 C++ `generate_proposals()` 仍按原始格式 `[64+nc, 8400]` 解析——越界读垃圾数据。

---

## 二、端到端推理数据流

```
用户选择图片
  ↓
ImageDetectActivity.kt:runYoloDetection(bitmap)          [L324]
  ↓
Yolov11Ncnn.java:detectBitmap(bitmap, 0, 0)              [JNI]
  ↓
yolov11ncnn.cpp:Java_..._detectBitmap()                   [L379]
  ├─ AndroidBitmap_lockPixels → RGBA pixels
  ├─ cv::cvtColor(rgba, bgr, COLOR_RGBA2BGR)              [L410-411]
  └─ g_yolo->runInference(bgr)                            [L418]
      ↓
yolov11.cpp:Inference::runInference(bgr)                  [L545]
  ├─ ncnn::Mat::from_pixels_resize(bgr, PIXEL_BGR2RGB)   [L568] ← 已做 BGR→RGB ✓
  ├─ ncnn::copy_make_border(…, 114.f)                    [L577] ← letterbox padding
  ├─ in_pad.substract_mean_normalize(meanVals, normVals)  [L579]
  ├─ ex.input("in0", in_pad)                             [L583]
  │
  │  ⚠️ 以下为问题区域 ↓
  ├─ ex.extract("out0", out) → generate_proposals(8, …)  [L590-595]
  ├─ ex.extract("out1", out) → generate_proposals(16, …) [L601-605]
  └─ ex.extract("out2", out) → generate_proposals(32, …) [L613-617]
      ↓
generate_proposals(stride, feat_blob, threshold, objects) [L56]
  ├─ num_w = feat_blob.w = 5（ultralytics 模型）
  ├─ num_class = num_w - 4*16 = 5 - 64 = -59  ← 🔴 负数！
  ├─ matat[64+0] 越界读取 → class_score 垃圾值  ← 🔴
  ├─ softmax(matat+0..15) 越界读取 → 坐标垃圾值  ← 🔴
  └─ sigmoid(class_score) 对垃圾值再 sigmoid     ← 🔴
      ↓
non_max_suppression(proposals, objects, …)                [L135]
  ├─ NMS + scale_unpad
  └─ 坐标格式: rect.x = 左上角x, rect.y = 左上角y        [L224-227]
      ↓
JNI 返回 int[] 每 6 个元素: [x, y, w, h, label, conf*1000]  [L432-438]
      ↓
ImageDetectActivity.kt:runYoloDetection() 解析            [L331-349]
  ├─ labelIndex = rawResults[i+4]
  ├─ label = if (labelIndex in labels.indices) labels[labelIndex] else "unknown"
  └─ confidence = rawResults[i+5] / 1000f
```

---

## 三、逐文件/逐函数深度分析

### 3.1 yolov11.cpp（核心推理引擎）

#### 3.1.1 `generate_proposals()` — 🔴 关键问题集中区

**文件**: `yolov11.cpp` **行 56-114**

```cpp
// 行 62-69: 维度解析
const int reg_max = 16;
const int num_w = feat_blob.w;      // ultralytics 输出: 5
const int num_grid_y = feat_blob.c; // 8400（或子集）
const int num_grid_x = feat_blob.h; // 1
const int num_class = num_w - 4 * reg_max; // 5 - 64 = -59 ← 🔴

// 行 78-85: class_score 从 offset 64 开始读
for (int c = 0; c < num_class; c++)  // num_class=-59 → 循环不执行！
{
    float score = matat[4 * reg_max + c]; // offset 64 越界
    ...
}

// 行 91-94: DFL 解码（对已解码坐标重新解码）
float x0 = j + 0.5f - softmax(matat, dst, 16);       // matat[0..15] 越界读
float y0 = j + 0.5f - softmax(matat + 16, dst, 16);   // matat[16..31] 越界读
float x1 = j + 0.5f + softmax(matat + 2 * 16, dst, 16);
float y1 = j + 0.5f + softmax(matat + 3 * 16, dst, 16);

// 行 107: sigmoid 对未做 sigmoid 的原始 logits
obj.prob = 1.0f / (1.0f + exp(-class_score));
```

**问题详解**:

| 问题 | 原因 | 后果 |
|------|------|------|
num_class 为负数 | `5 - 64 = -59` | class_score 循环不执行，class_score 保持 `-FLT_MAX` |
class_index 永远为 0 | 循环不执行 | labelIndex=0 → 取 labels[0] 或越界 → "unknown" |
class_score = -FLT_MAX | 无有效分数 | sigmoid(-FLT_MAX) ≈ 0.0 → 但被 prob_threshold 过滤 |
DFL 解码越界 | matat 只有 5 个 float | 读取堆/栈垃圾数据 → 随机坐标 → 框在左上角 |
sigmoid 重复 | 模型已 sigmoid | 值域被压缩 → 部分结果接近 1.0 |

**实际模型输出格式** (ultralytics NCNN 导出):
```
Blob "out0": shape [8400, 5]
  matat[0] = cx  (已解码中心 x，像素坐标)
  matat[1] = cy  (已解码中心 y，像素坐标)
  matat[2] = w   (已解码宽度)
  matat[3] = h   (已解码高度)
  matat[4] = prob (已 sigmoid 的类别概率)
```

**代码期望格式** (原始 YOLO 输出):
```
Blob "out0": shape [8400, 64+nc]
  matat[0..15]   = DFL left 预测 (16 个 bin)
  matat[16..31]  = DFL top 预测
  matat[32..47]  = DFL right 预测
  matat[48..63]  = DFL bottom 预测
  matat[64..64+nc-1] = class raw logits
```

#### 3.1.2 `runInference()` — 三尺度提取逻辑

**文件**: `yolov11.cpp` **行 545-629**

```cpp
// 行 590-618: 三个尺度分别提取
ex.extract("out0", out);   // stride 8
ex.extract("out1", out);   // stride 16
ex.extract("out2", out);   // stride 32
```

**问题**: ultralytics NCNN 导出通常只有 **一个** 输出 blob（已包含所有尺度），而代码期望 **三个** 分开的输出 blob。

**内置模型** (`assets/models/yolov11n/model.param`) 仍然使用三输出格式：
```
Concat cat_19 → out0  (stride 8)
Concat cat_18 → out1  (stride 16)
Concat cat_17 → out2  (stride 32)
```
每个输出的 `feat_blob.w = 144`（64 DFL + 80 class logits）。

**因此**：此问题仅影响**用户自训练的 ultralytics 导出模型**，不影响内置模型。

#### 3.1.3 `preprocess()` / letterbox — ✅ 基本正确

**文件**: `yolov11.cpp` **行 545-579**

```cpp
// 行 568: BGR → RGB 转换 ← ✅ 正确
ncnn::Mat in = ncnn::Mat::from_pixels_resize(bgr.data, ncnn::Mat::PIXEL_BGR2RGB, img_w, img_h, w, h);

// 行 573-577: letterbox padding ← ✅ 正确
int wpad = target_size - w;
int hpad = target_size - h;
ncnn::copy_make_border(in, in_pad, hpad/2, hpad-hpad/2, wpad/2, wpad-wpad/2, ncnn::BORDER_CONSTANT, 114.f);

// 行 579: mean/norm ← ✅ 正确（由调用方传入）
in_pad.substract_mean_normalize(meanVals, normVals);
```

**注意**: BGR→RGB 已在 `from_pixels_resize` 中完成（第 568 行），**不是** bug。但需确认自定义模型导入时 mean/norm 值是否正确设置。

#### 3.1.4 `non_max_suppression()` — ✅ 逻辑正确

**文件**: `yolov11.cpp` **行 135-234**

NMS 实现使用标准 IoU 计算 + 排序后贪心选择。坐标变换正确：
```cpp
// 行 213-227: scale_unpad 变换
x0 = (x0 - dw) / ratio_w;  // 减去 padding 再除以缩放比
y0 = (y0 - dh) / ratio_h;
x1 = (x1 - dw) / ratio_w;
y1 = (y1 - dh) / ratio_h;

// 行 224-227: 输出格式 (左上角 x, 左上角 y, 宽, 高)
obj.rect.x = x0;
obj.rect.y = y0;
obj.rect.width = x1 - x0;
obj.rect.height = y1 - y0;
```

**注意**: 此处假设 `generate_proposals()` 输出的是 `x0,y0,x1,y1`（左上角+右下角），但 ultralytics 输出的是 `cx,cy,w,h`（中心点+宽高）。修复 `generate_proposals()` 时需要适配。

#### 3.1.5 `loadLabelsFromPath()` — ✅ 正确（有 BOM 和 CRLF 处理）

**文件**: `yolov11.cpp` **行 479-540**

```cpp
// 行 496-502: UTF-8 BOM 处理
if (first_line && len >= 3 &&
    (unsigned char)line[0] == 0xEF &&
    (unsigned char)line[1] == 0xBB &&
    (unsigned char)line[2] == 0xBF)
{
    memmove(line, line + 3, len - 3 + 1);
    len -= 3;
}

// 行 490-494: CRLF 处理
while (len > 0 && (line[len-1] == '\n' || line[len-1] == '\r'))
    line[--len] = '\0';
```

**回退链路**: 加载失败 → 使用 COCO 80 类默认标签（行 522-538）。

**潜在问题**: 如果标签文件的行数与模型实际类别数不匹配，`labelIndex` 越界时 Kotlin 端会回退到 "unknown"。这不是 bug，但会导致混淆。

#### 3.1.6 `draw()` — ✅ 正确

**文件**: `yolov11.cpp` **行 711-743**

使用 `cv::rectangle` + `cv::putText` 绘制，`obj.rect` 的 x,y 是左上角坐标，与 NMS 输出一致。

---

### 3.2 yolov11ncnn.cpp（JNI 桥接层）

#### 3.2.1 `detectBitmap()` — 行 379-443

```cpp
// 行 407-411: Bitmap → BGR Mat
cv::Mat rgba(height, width, CV_8UC4, pixels);
cv::Mat bgr;
cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);  // ← ✅ 正确

// 行 418: 推理
objects = g_yolo->runInference(bgr);

// 行 432-438: 结果打包为 int[]
result_data[i * 6 + 0] = (int)obj.rect.x;       // 左上角 x
result_data[i * 6 + 1] = (int)obj.rect.y;       // 左上角 y
result_data[i * 6 + 2] = (int)obj.rect.width;   // 宽
result_data[i * 6 + 3] = (int)obj.rect.height;  // 高
result_data[i * 6 + 4] = obj.label;              // 标签索引
result_data[i * 6 + 5] = (int)(obj.prob * 1000); // 置信度 ×1000
```

**问题**: `obj.prob` 经过双重 sigmoid 后值接近 1.0 → `prob * 1000 ≈ 1000` → Kotlin 端 `confidence = 1000/1000 = 1.0` → 显示 100%。

**JNI 内存管理**: ✅ 正确使用 `GetIntArrayElements` / `ReleaseIntArrayElements`。

#### 3.2.2 `loadModelFromPath()` — 行 ~289-470

```cpp
// 加载 .param + .bin
int ret_param = net.load_param(paramPath);
int ret_model = net.load_model(modelPath);

// 加载标签
loadLabelsFromPath(labelsPath);
```

**潜在问题**: 如果 `labelsPath` 指向不存在的文件（如用户 zip 中没有 labels.txt），`loadLabelsFromPath` 会回退到 COCO 默认标签。但如果模型的类别数与 COCO 80 不同，标签索引会错位。

---

### 3.3 ImageDetectActivity.kt（Kotlin 检测逻辑）

#### 3.3.1 `runYoloDetection()` — 行 324-357

```kotlin
val rawResults = yolov11Ncnn.detectBitmap(bitmap, 0, 0)
val labels = loadLabels(config.selectedModel)

var i = 0
while (i + 5 < rawResults.size) {
    val x = rawResults[i].toFloat()         // 左上角 x
    val y = rawResults[i + 1].toFloat()     // 左上角 y
    val w = rawResults[i + 2].toFloat()     // 宽
    val h = rawResults[i + 3].toFloat()     // 高
    val labelIndex = rawResults[i + 4]      // 标签索引
    val confidence = rawResults[i + 5] / 1000f  // 置信度

    val label = if (labelIndex in labels.indices) labels[labelIndex] else "unknown"
    // ...
    i += 6
}
```

**分析**:
- ✅ 每 6 个元素一组解析，与 JNI 打包格式一致
- ✅ `labelIndex in labels.indices` 防御性检查正确
- ⚠️ 当 C++ 返回的 labelIndex 因越界读取为垃圾值时，必然不在 `labels.indices` 范围内 → "unknown"

#### 3.3.2 `loadLabels()` — 行 361-387

```kotlin
private fun loadLabels(modelName: String): List<String> {
    return try {
        // 1. 尝试 assets 加载
        assets.open("models/$modelName/labels.txt").use { ... }
    } catch (e: Exception) {
        try {
            // 2. 尝试内部存储加载（自定义模型）
            val labelsFile = File(modelDir, "$modelName/labels.txt")
            if (labelsFile.exists()) {
                labelPresetManager.loadLabelsFromFile(labelsFile) ?: ...
            } else {
                // 3. 回退到 COCO 80 默认标签
                labelPresetManager.loadPresetLabels(PRESET_COCO_80) ?: DEFAULT_COCO_LABELS
            }
        } catch (e2: Exception) {
            // 4. 最终回退
            labelPresetManager.loadPresetLabels(PRESET_COCO_80) ?: DEFAULT_COCO_LABELS
        }
    }
}
```

**回退链路**: ✅ 完整，不会崩溃。但标签加载在 C++ 端和 Kotlin 端**各做一次**，可能存在不一致。

#### 3.3.3 `loadModel()` — 行 180-206

```kotlin
val isCustomModel = customModelDir.exists() &&
    customModelDir.listFiles()?.any { it.extension == "param" } == true &&
    customModelDir.listFiles()?.any { it.extension == "bin" } == true

if (isCustomModel) {
    ret = yolov11Ncnn.loadModelFromPath(paramFile.absolutePath, binFile.absolutePath, labelsFile.absolutePath, cpuGpu)
} else {
    ret = yolov11Ncnn.loadModel(assets, 0, cpuGpu)
}
```

**分析**: 自定义模型走 `loadModelFromPath`（单输出），内置模型走 `loadModel`（三输出）。两条路径的后处理逻辑在 C++ 中是**同一套** `generate_proposals()`，但模型输出格式不同。

---

### 3.4 SettingsActivity.kt（模型导入）

#### 3.4.1 ZIP 解压逻辑 — 行 348-450

```kotlin
ZipInputStream(inputStream).use { zip ->
    var entry = zip.nextEntry
    while (entry != null) {
        if (!entry.isDirectory) {
            val entryExt = entryName.substringAfterLast(".").lowercase()
            // 只提取 .param, .bin, .txt 文件
            if (entryExt in listOf("param", "bin", "txt")) {
                extractedFiles[entryExt] = targetFile
            }
        }
    }
}
```

**问题**: 只取每种扩展名的**一个**文件。如果 zip 中有多个 .param 文件（如 model.param 和 model_ncnn.param），可能取错。

**标签处理**: 如果 zip 中没有 labels.txt，会弹出对话框让用户选择预设标签（COCO 80/VOC 20 等）。

---

## 四、发现的所有问题

### 🔴 关键问题

#### 🔴-1: `generate_proposals()` 格式错配（根本原因）

| 属性 | 详情 |
|------|------|
**位置** | `yolov11.cpp` 行 56-114 |
**原因** | ultralytics NCNN 导出自动嵌入后处理，输出 `[8400, 5]`，但代码按 `[8400, 64+nc]` 解析 |
**影响** | 三个 bug 全部由此引起 |
**触发条件** | 使用 ultralytics 导出的 NCNN 模型（非内置模型） |

**详细分析**:

1. **`num_class = 5 - 64 = -59`** → class_score 循环不执行 → `class_index = 0`
2. **DFL softmax 越界**: `matat[0..15]` 只有 5 个有效 float，读取 16 个 → 堆/栈垃圾
3. **sigmoid 重复**: 模型输出已 sigmoid，代码再 sigmoid → 值域压缩，部分结果 ≈ 1.0
4. **坐标错误**: DFL 解码对已解码坐标操作 → 随机值 → 框在左上角

#### 🔴-2: 三输出 vs 单输出不匹配

| 属性 | 详情 |
|------|------|
**位置** | `yolov11.cpp` 行 588-618 |
**原因** | 代码提取 `out0`/`out1`/`out2` 三个 blob，ultralytics 导出只有 `out0` 一个 |
**影响** | `extract("out1")` 和 `extract("out2")` 返回空 Mat → 无检测结果 |
**触发条件** | 同 🔴-1 |

### 🟡 高优先级问题

#### 🟡-1: 标签加载双重路径不一致

| 属性 | 详情 |
|------|------|
**位置** | C++ `loadLabelsFromPath()` + Kotlin `loadLabels()` |
**原因** | C++ 和 Kotlin 各自独立加载标签，回退逻辑不同 |
**影响** | 极端情况下两端标签列表不一致 |

#### 🟡-2: 坐标格式假设不一致

| 属性 | 详情 |
|------|------|
**位置** | `generate_proposals()` → `non_max_suppression()` |
**原因** | ultralytics 输出 `cx,cy,w,h`，但 NMS 期望 `x0,y0,x1,y1` |
**影响** | 修复 🔴-1 后需同步修复坐标转换 |

### 🟢 中/低优先级问题

#### 🟢-1: ZIP 解压只取一个同扩展名文件

| 属性 | 详情 |
|------|------|
**位置** | `SettingsActivity.kt` 行 348-450 |
**原因** | `extractedFiles[ext] = file` 只保留最后一个 |
**影响** | 多模型 zip 可能取错文件 |

#### 🟢-2: `detectBitmap` 硬编码 modelid=0

| 属性 | 详情 |
|------|------|
**位置** | `ImageDetectActivity.kt` 行 325 |
**原因** | `detectBitmap(bitmap, 0, 0)` 第二个参数 modelid 未使用 |
**影响** | 无实际影响（C++ 端忽略此参数） |

---

## 五、修复方案

### 修复 🔴-1 + 🔴-2: 自适应后处理 `generate_proposals()`

**核心思路**: 检测模型输出维度，自动选择处理模式。

#### 方案 A: 修改 `generate_proposals()` 支持两种格式（推荐）

**修改文件**: `yolov11.cpp`

```cpp
// ===== 修改前 (行 56-114) =====
static void generate_proposals(
        int stride,
        const ncnn::Mat& feat_blob,
        const float prob_threshold,
        std::vector<Object>& objects
)
{
    const int reg_max = 16;
    float dst[16];
    const int num_w = feat_blob.w;
    const int num_grid_y = feat_blob.c;
    const int num_grid_x = feat_blob.h;

    const int num_class = num_w - 4 * reg_max;

    for (int i = 0; i < num_grid_y; i++)
    {
        for (int j = 0; j < num_grid_x; j++)
        {
            const float* matat = feat_blob.channel(i).row(j);

            int class_index = 0;
            float class_score = -FLT_MAX;
            for (int c = 0; c < num_class; c++)
            {
                float score = matat[4 * reg_max + c];
                if (score > class_score)
                {
                    class_index = c;
                    class_score = score;
                }
            }

            if (class_score >= prob_threshold)
            {
                float x0 = j + 0.5f - softmax(matat, dst, 16);
                float y0 = i + 0.5f - softmax(matat + 16, dst, 16);
                float x1 = j + 0.5f + softmax(matat + 2 * 16, dst, 16);
                float y1 = i + 0.5f + softmax(matat + 3 * 16, dst, 16);

                x0 *= stride;
                y0 *= stride;
                x1 *= stride;
                y1 *= stride;

                Object obj;
                obj.rect.x = x0;
                obj.rect.y = y0;
                obj.rect.width = x1 - x0;
                obj.rect.height = y1 - y0;
                obj.label = class_index;
                obj.prob = 1.0f / (1.0f + exp(-class_score));
                objects.push_back(obj);
            }
        }
    }
}

// ===== 修改后 =====
static void generate_proposals(
        int stride,
        const ncnn::Mat& feat_blob,
        const float prob_threshold,
        std::vector<Object>& objects
)
{
    const int reg_max = 16;
    const int num_w = feat_blob.w;
    const int num_grid_y = feat_blob.c;
    const int num_grid_x = feat_blob.h;

    // 自动检测模型输出格式
    // 格式 A (原始): num_w = 4*reg_max + nc (如 64+80=144)
    // 格式 B (ultralytics 已后处理): num_w = 5 (4 坐标 + 1 概率)
    // DFL format: num_w = 4*reg_max + nc >= 65; Decoded: num_w = 4 + nc_classes < 65
    const bool is_decoded_format = (num_w < 65);

    if (is_decoded_format)
    {
        // ultralytics NCNN 导出格式: [cx, cy, w, h, prob]
        // 坐标已解码到输入图像尺度，prob 已 sigmoid
        for (int i = 0; i < num_grid_y; i++)
        {
            for (int j = 0; j < num_grid_x; j++)
            {
                const float* matat = feat_blob.channel(i).row(j);

                float cx = matat[0];
                float cy = matat[1];
                float w  = matat[2];
                float h  = matat[3];
                float prob = matat[4];  // 已 sigmoid

                if (prob < prob_threshold)
                    continue;

                // 转换为中心点+宽高 → 左上角+宽高
                // 注意: ultralytics 输出的是绝对像素坐标（相对于输入图像）
                // 不需要乘以 stride，也不需要锚点偏移
                Object obj;
                obj.rect.x = cx - w * 0.5f;
                obj.rect.y = cy - h * 0.5f;
                obj.rect.width = w;
                obj.rect.height = h;
                obj.label = 0;  // 单类或多类由模型决定
                obj.prob = prob;
                objects.push_back(obj);
            }
        }
    }
    else
    {
        // 原始 DFL 格式: [DFL_left(16), DFL_top(16), DFL_right(16), DFL_bottom(16), class_logits(nc)]
        float dst[16];
        const int num_class = num_w - 4 * reg_max;

        for (int i = 0; i < num_grid_y; i++)
        {
            for (int j = 0; j < num_grid_x; j++)
            {
                const float* matat = feat_blob.channel(i).row(j);

                int class_index = 0;
                float class_score = -FLT_MAX;
                for (int c = 0; c < num_class; c++)
                {
                    float score = matat[4 * reg_max + c];
                    if (score > class_score)
                    {
                        class_index = c;
                        class_score = score;
                    }
                }

                if (class_score >= prob_threshold)
                {
                    float x0 = j + 0.5f - softmax(matat, dst, 16);
                    float y0 = i + 0.5f - softmax(matat + 16, dst, 16);
                    float x1 = j + 0.5f + softmax(matat + 2 * 16, dst, 16);
                    float y1 = i + 0.5f + softmax(matat + 3 * 16, dst, 16);

                    x0 *= stride;
                    y0 *= stride;
                    x1 *= stride;
                    y1 *= stride;

                    Object obj;
                    obj.rect.x = x0;
                    obj.rect.y = y0;
                    obj.rect.width = x1 - x0;
                    obj.rect.height = y1 - y0;
                    obj.label = class_index;
                    obj.prob = 1.0f / (1.0f + exp(-class_score));
                    objects.push_back(obj);
                }
            }
        }
    }
}
```

#### 方案 B: 修改 `runInference()` 支持单输出

**修改文件**: `yolov11.cpp`

```cpp
// ===== 修改前 (行 586-618) =====
std::vector<Object> proposals;

// stride 8
{
    ncnn::Mat out;
    ex.extract("out0", out);
    std::vector<Object> objects8;
    generate_proposals(8, out, prob_threshold, objects8);
    proposals.insert(proposals.end(), objects8.begin(), objects8.end());
}

// stride 16
{
    ncnn::Mat out;
    ex.extract("out1", out);
    std::vector<Object> objects16;
    generate_proposals(16, out, prob_threshold, objects16);
    proposals.insert(proposals.end(), objects16.begin(), objects16.end());
}

// stride 32
{
    ncnn::Mat out;
    ex.extract("out2", out);
    std::vector<Object> objects32;
    generate_proposals(32, out, prob_threshold, objects32);
    proposals.insert(proposals.end(), objects32.begin(), objects32.end());
}

// ===== 修改后 =====
std::vector<Object> proposals;

// 尝试提取所有可用的输出 blob
// ultralytics 导出: 只有 "out0"，已包含所有尺度
// 内置模型: "out0"(stride8), "out1"(stride16), "out2"(stride32)
const char* output_names[] = {"out0", "out1", "out2"};
const int strides[] = {8, 16, 32};

for (int s = 0; s < 3; s++)
{
    ncnn::Mat out;
    int ret = ex.extract(output_names[s], out);
    if (ret == 0 && !out.empty())
    {
        std::vector<Object> objects_s;
        generate_proposals(strides[s], out, prob_threshold, objects_s);
        proposals.insert(proposals.end(), objects_s.begin(), objects_s.end());
    }
    // 如果 out0 提取失败，直接返回空
    if (s == 0 && ret != 0)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to extract out0");
        return {};
    }
}
```

### 修复 🟡-2: 坐标格式适配

在 `generate_proposals()` 的 decoded format 分支中，ultralytics 输出的 `cx, cy, w, h` 是**绝对像素坐标**（相对于 letterbox 后的输入图像），不需要乘以 stride。

NMS 中的 `scale_unpad` 会处理 padding 和缩放的逆变换：
```cpp
// generate_proposals 已解码格式分支输出:
obj.rect.x = cx - w * 0.5f;  // 左上角 x（letterbox 坐标系）
obj.rect.y = cy - h * 0.5f;  // 左上角 y

// NMS 中 scale_unpad 变换:
x0 = (x0 - dw) / ratio_w;   // 减去 padding，除以缩放比 → 原图坐标系
y0 = (y0 - dh) / ratio_h;
```

**但需注意**: ultralytics 导出的坐标是否已经过 letterbox 逆变换？如果模型图内已包含逆变换，则 `generate_proposals()` 输出的坐标直接就是原图坐标，NMS 的 `scale_unpad` 会重复变换。

**验证方法**: 打印 `generate_proposals()` 输出的坐标范围，检查是否在 `[0, target_size]` 范围内（letterbox 坐标系）还是 `[0, img_w/img_h]` 范围内（原图坐标系）。

**如果坐标已在原图坐标系**: 需要在 `runInference()` 中跳过 NMS 的 scale_unpad，或在 `generate_proposals()` 中重新映射到 letterbox 坐标系。

### 修复 🟡-1: 统一标签加载

**方案**: 让 C++ 端的标签加载结果通过 JNI 返回给 Kotlin，避免双重加载。

```cpp
// yolov11ncnn.cpp 新增 JNI 方法
JNIEXPORT jint JNICALL Java_com_yolo_openiris_Yolov11Ncnn_getClassCount(JNIEnv* env, jobject thiz)
{
    if (!g_yolo) return 0;
    return (int)g_yolo->class_names.size();
}
```

```kotlin
// Yolov11Ncnn.java 新增
public native int getClassCount();
```

```kotlin
// ImageDetectActivity.kt 修改 loadLabels
private fun loadLabels(modelName: String): List<String> {
    val cppClassCount = yolov11Ncnn.getClassCount()
    if (cppClassCount > 0) {
        // C++ 端已加载标签，信任 C++ 端
        // 但仍需在 Kotlin 端维护一份用于 UI 显示
    }
    // ... 保持现有逻辑作为 UI 显示用
}
```

**更简单的方案**: 确保 Kotlin 端和 C++ 端使用完全相同的标签文件路径和回退逻辑。当前代码已基本一致，只需确保 `labels.txt` 文件在 zip 导入时被正确提取。

---

## 六、修复优先级排序

| 优先级 | 问题 | 修复方案 | 影响范围 | 工作量 |
|--------|------|----------|----------|--------|
| **P0** | 🔴-1 格式错配 | 修改 `generate_proposals()` 支持 decoded format | 所有自定义模型 | 中 |
| **P0** | 🔴-2 三输出 vs 单输出 | 修改 `runInference()` 尝试提取多个输出 | 所有自定义模型 | 小 |
| **P1** | 🟡-2 坐标格式 | 在 decoded format 分支正确处理 cx,cy→x0,y0 | 修复 🔴-1 后验证 | 小 |
| **P2** | 🟡-1 标签一致性 | 统一标签加载路径 | 边缘场景 | 中 |
| **P3** | 🟢-1 ZIP 解压 | 改为列表选择或多文件支持 | 少见场景 | 中 |

---

## 七、修复验证清单

修复后需验证以下场景：

- [ ] 内置 COCO 模型检测正常（三输出格式）
- [ ] ultralytics 导出的自定义模型检测正常（单输出 decoded format）
- [ ] 标签正确显示（非 "unknown"）
- [ ] 框位置正确（不在左上角）
- [ ] 置信度合理（非 100%）
- [ ] 多类检测正常（非全部 label=0）
- [ ] BGR→RGB 预处理正确
- [ ] letterbox padding/缩放正确
- [ ] NMS 去重正常
- [ ] 标签文件缺失时回退到默认标签

---

## 八、附录：关键代码位置索引

| 文件 | 函数 | 行号 | 作用 |
|------|------|------|------|
| yolov11.cpp | `generate_proposals()` | 56-114 | 🔴 后处理解码（需修复） |
| yolov11.cpp | `runInference()` | 545-629 | 🔴 输出提取（需修复） |
| yolov11.cpp | `non_max_suppression()` | 135-234 | NMS + 坐标变换 |
| yolov11.cpp | `loadLabelsFromPath()` | 479-540 | 标签加载 + BOM 处理 |
| yolov11.cpp | `draw()` | 711-743 | 绘制检测框 |
| yolov11ncnn.cpp | `detectBitmap()` | 379-443 | JNI 桥接 |
| ImageDetectActivity.kt | `runYoloDetection()` | 324-357 | Kotlin 结果解析 |
| ImageDetectActivity.kt | `loadLabels()` | 361-387 | 标签加载回退链 |
| ImageDetectActivity.kt | `loadModel()` | 180-206 | 模型加载分支 |
| SettingsActivity.kt | `addModelFromZip()` | 348-450 | ZIP 导入 |

---

*报告生成时间: 2026-06-16*

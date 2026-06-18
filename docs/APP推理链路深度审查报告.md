# OpenIris APP 端 NCNN 推理链路深度审查报告

**审查日期**: 2026-06-16
**审查范围**: C++ 推理引擎、JNI 接口、标签加载、模型输出格式、坐标转换、NMS
**已知问题**: 标签显示 "unknown"、置信度异常（43814%）、框图位置在左上角

---

## 一、模型架构对比分析

### 1.1 内置模型（3 输出，原始 YOLO 格式）

**文件**: `buildapk/app/src/main/assets/models/yolov11n/model.param`

```
输出: out0 (stride 8), out1 (stride 16), out2 (stride 32)
Concat 轴: 0=2 (沿 channels 维度拼接)
每层组成: [DFL_bbox(64ch), sigmoid_class_scores(80ch)]
sigmoid 实现: Convolution 9=4 (convsigmoid，sigmoid 融合进卷积)
```

**out2（stride 32）示例**:
- 空间维度: 20×20 = 400 个位置
- 输出形状: `[w=20, h=20, c=144]` (64 DFL + 80 类别)
- `num_w = 144 >= 64` → 进入 DFL 分支 ✓
- `num_class = 144 - 4×16 = 80` ✓

### 1.2 Ultralytics NCNN 导出模型（单输出，已解码格式）

**文件**: `models/model.param` / `training/run/.../model.ncnn.param`

```
输出: out0 (单输出)
Concat 轴: 0=0 (沿 w 维度拼接)
图内后处理: DFL softmax + weighted sum + sigmoid
每锚组成: [cx, cy, w, h, class_prob_0, ..., class_prob_{nc-1}]
```

**输出层结构追踪**:
```
Reshape reshape_175: 1 1 305 306 0=8400 1=4       → 解码后 bbox [w=8400, h=4]
MemoryData anchor_points: 0 1 307 0=8400 1=2       → 锚点中心 [w=8400, h=2]
Slice chunk_0: 1 2 306 310 311 ... 1=0              → 沿 w 切分 bbox
BinaryOp sub/add/div: 计算 cx, cy, w, h
Concat cat_19: 2 1 319 320 321 0=0                  → [w=4, h=8400] (cx,cy,w,h)
Reshape reshape_176: 1 1 251 322 0=8400 1=1         → 置信度 [w=8400, h=1]
BinaryOp mul_20: 321 * 322 323 0=2                  → bbox × 置信度
Sigmoid sigmoid_162: 301 → 324                      → 类别概率（已 sigmoid）
Concat cat_20: 323 + 324 out0 0=0                   → 最终输出
```

**最终 out0 形状**: `[w=4+nc, h=8400, c=1]`
- 1 类: `[w=5, h=8400, c=1]`
- 80 类: `[w=84, h=8400, c=1]`

---

## 二、C++ 推理引擎审查

### 2.1 `generate_proposals()` 函数审查

#### 2.1.1 `is_decoded_format` 检测逻辑

```cpp
const int num_w = feat_blob.w;
const int num_h = feat_blob.h;
const int num_c = feat_blob.c;
const bool is_decoded_format = (num_w < 64);
```

| 模型 | num_w | is_decoded_format | 预期 | 结果 |
|------|-------|-------------------|------|------|
| 内置模型 out2 | 144 | false | DFL 分支 | ✓ |
| Ultralytics 1类 | 5 | true | 解码分支 | ✓ |
| Ultralytics 80类 | 84 | false | 解码分支 | ✗ **BUG** |

**🔴 严重问题 [P0]**: `is_decoded_format = (num_w < 64)` 对于 80 类的 ultralytics 模型（num_w=84）会错误进入 DFL 分支。阈值 64 是硬编码的，与 `reg_max=16` 绑定（4×16=64），但未考虑类别数较多的情况。

**修复建议**: 应改为检测输出 blob 是否包含 DFL 编码的 64 通道（即检查 `num_w >= 4*reg_max` 且 `num_c > 1`），或通过模型元数据/文件名显式标记格式。

#### 2.1.2 `values_in_w` 布局检测

```cpp
bool values_in_w = (num_w > num_h);
if (values_in_w)
{
    num_anchors = num_h * num_c;
    num_values = num_w;
}
else
{
    num_anchors = num_c;      // ← 问题所在
    num_values = num_h;
}
```

**🔴 严重问题 [P0] — 核心根因**: 对于 ultralytics 导出模型的输出 `[w=5, h=8400, c=1]`：

| 变量 | 实际值 | 期望值 | 说明 |
|------|--------|--------|------|
| `values_in_w` | `false` | `true` | 5 > 8400 = false |
| `num_anchors` | `1` | `8400` | 错误！只处理 1 个锚点 |
| `num_values` | `8400` | `5` | 错误！把 8400 当作每锚值数 |

**根因**: `values_in_w` 的判断条件 `(num_w > num_h)` 对于 `[w=小值, h=大量锚点]` 的布局产生错误结果。ultralytics 导出的解码后输出中，w 维度存放每个锚点的 4+nc 个值（小值），h 维度存放 8400 个锚点（大值），导致 `values_in_w = false`。

**影响链**:
1. `num_anchors = 1` → 只处理第 1 个锚点，丢失 8399 个检测
2. `num_values = 8400` → 从 index 4 到 8399 搜索类别概率，实际读取的是其他锚点的空间坐标数据

**修复建议**: 对于 2D Mat（c=1），应根据 w 和 h 的语义判断：
```cpp
// 对于已解码格式，w 存放每锚值数（通常 4+nc < 100），h 存放锚点数（通常 8400）
bool values_in_w = (num_w <= num_h) || (num_c == 1 && num_w < 100);
```
或更稳健的方案：通过模型元数据显式指定布局。

#### 2.1.3 decoded 分支坐标提取逻辑

```cpp
for (int i = 0; i < num_anchors; i++)
{
    const float* ptr = feat_blob.channel(0).row(i);
    float cx = ptr[0];
    float cy = ptr[1];
    float w_box = ptr[2];
    float h_box = ptr[3];

    float max_prob = ptr[4];
    int class_index = 0;
    for (int c = 4; c < num_values; c++)
        if (ptr[c] > max_prob) { max_prob = ptr[c]; class_index = c - 4; }
```

当 `num_anchors=1, num_values=8400`（由于上述 bug）：
- `ptr = feat_blob.channel(0).row(0)` → 指向第 1 个锚点的数据起始
- `cx, cy, w_box, h_box` = data[0..3] → 恰好是第 1 个锚点的正确解码坐标
- `max_prob = data[4]` = 第 1 个锚点的类别概率（正确）
- 但循环 `for (c=4; c<8400; c++)` 读取 data[4..8399]，包含：
  - data[4] = 锚点0 的 class_prob
  - data[5..8] = 锚点1 的 cx, cy, w, h（空间坐标！）
  - data[9] = 锚点1 的 class_prob
  - ... 以此类推

#### 2.1.4 DFL 分支坐标解码逻辑

DFL 分支代码本身是正确的：
- 正确使用 softmax 对 16 个 DFL bin 做加权求和
- 正确解码 left, top, right, bottom 距离
- 正确计算 (x1, y1, x2, y2)
- 坐标乘以 stride 还原到模型输入空间

**但此分支不适用于 ultralytics 已解码模型**（坐标不应再乘以 stride）。

#### 2.1.5 sigmoid/softmax 处理

| 模型格式 | 模型内 sigmoid | 代码内 sigmoid | 结果 |
|----------|---------------|---------------|------|
| 内置模型（convsigmoid） | ✓ (9=4) | ✗ (DFL 分支不加) | ✓ 正确 |
| Ultralytics（Sigmoid 层） | ✓ (sigmoid_162) | ✗ (decoded 分支不加) | ✓ 正确 |

**结论**: 不存在 sigmoid 重复应用的问题。置信度异常完全由 `values_in_w` bug 导致。

---

### 2.2 `runInference()` 函数审查

#### 2.2.1 输出 blob 提取逻辑

```cpp
if (strstr(modelPath.c_str(), "detect") != nullptr || 
    strstr(modelPath.c_str(), "best") != nullptr ||
    modelPath.find(".ncnn") != std::string::npos)
{
    ex.extract("out0", out);
    stride_num = {32};
}
else if (strstr(modelPath.c_str(), "yolov11n") != nullptr)
{
    ex.extract("out0", out0);
    ex.extract("out1", out1);
    ex.extract("out2", out2);
    stride_num = {8, 16, 32};
}
```

**🟡 中等问题 [P1]**: 模型格式检测依赖文件名字符串匹配（"detect", "best", ".ncnn"），非常脆弱：
- 用户自定义模型文件名如果不含这些关键词，会走到默认分支（3 输出），导致 `ex.extract("out1/out2")` 失败
- `stride_num = {32}` 对于已解码模型是正确的（stride 不参与坐标计算），但作为语义标记不清晰

#### 2.2.2 stride 分配逻辑

对于 ultralytics 已解码模型，stride=32 传递给 `generate_proposals`，但在 decoded 分支中 stride 未被使用（坐标已经是像素空间），所以不影响结果。

#### 2.2.3 坐标转换逻辑

```cpp
float scale_x = (float)input.cols / (float)target_size;
float scale_y = (float)input.rows / (float)target_size;
float pad_x = 0.0f, pad_y = 0.0f;
if (scale_x > scale_y) { pad_y = ...; } else { pad_x = ...; }

// 后处理每个检测
float x1 = obj.rect.x / scale_x + pad_x;
float y1 = obj.rect.y / scale_y + pad_y;
```

**🟡 中等问题 [P1]**: ultralytics 导出模型的解码坐标已经是模型输入空间（如 640×640）中的像素坐标，代码中的缩放和填充补偿逻辑假设坐标来自 DFL 解码（需要乘以 stride），对于已解码模型可能导致微小的坐标偏移。但由于 decoded 分支中 stride 未被使用，实际坐标已经是正确的像素空间值，缩放逻辑本身是通用的。

**潜在问题**: 如果 ultralytics 模型使用 letterbox 填充（保持宽高比），但 C++ 端的预处理使用简单 resize（不保持宽高比），坐标会出现系统性偏移。需要确认两端的预处理方式一致。

---

## 三、标签加载审查

### 3.1 `loadLabelsFromPath()` 函数

```cpp
int Inference::loadLabelsFromPath(const char* labelsPath)
{
    class_names.clear();
    FILE* fp = fopen(labelsPath, "r");
    if (!fp)
    {
        __android_log_print(ANDROID_LOG_WARN, "YOLOv11",
            "Failed to open labels file: %s, using default labels", labelsPath);
        // 填充 COCO 默认标签
        ...
        return 0;  // ← 返回 0（成功），不是错误
    }
    ...
}
```

**🟡 中等问题 [P1]**: 标签文件打开失败时返回 0（成功）而非错误码。调用方无法区分"加载成功"和"使用了默认标签"。

**🟡 中等问题 [P1]**: 如果自定义模型的 `labels.txt` 路径不存在（如用户未放置文件），会静默回退到 COCO 标签。对于自定义训练的模型（如只有 "pig" 一个类别），这会导致：
- 标签显示为 COCO 类名（如 "person"）而非正确的 "pig"
- 如果检测到的类别索引 >= 80，显示 "unknown"

### 3.2 `getClassName()` 函数

```cpp
const char* Inference::getClassName(int label) const
{
    if (label >= 0 && label < (int)class_names.size())
        return class_names[label].c_str();
    return "unknown";
}
```

**逻辑正确**，但 `class_names` 的内容取决于加载是否成功。当 `values_in_w` bug 导致 `class_index` 越界时，此函数正确返回 "unknown"。

---

## 四、NCNN 模型输出格式分析

### 4.1 两种格式对比

| 属性 | 内置模型（3 输出） | Ultralytics 导出（单输出） |
|------|-------------------|--------------------------|
| 输出数量 | 3 (out0/1/2) | 1 (out0) |
| 输出形状 | [w=spatial, h=spatial, c=144] | [w=4+nc, h=8400, c=1] |
| 坐标格式 | DFL 编码（16 bin × 4 方向） | 已解码（cx, cy, w, h） |
| 分类格式 | sigmoid 后 logit | sigmoid 后概率 |
| sigmoid 实现 | Convolution 9=4 (融合) | Sigmoid 独立层 |
| 后处理 | 需要 C++ DFL 解码 | 图内已完成 |

### 4.2 C++ 代码对两种格式的处理路径

```
                    ┌─ num_w >= 64 ──→ DFL 分支（内置模型）✓ 正确
generate_proposals ─┤
                    └─ num_w < 64 ──→ decoded 分支（ultralytics）
                                        ├─ values_in_w=true  → 正确处理
                                        └─ values_in_w=false → ✗ BUG（当前情况）
```

---

## 五、坐标转换和 NMS 审查

### 5.1 `non_max_suppression()` 函数

NMS 实现本身逻辑正确：
- 按置信度降序排序
- 对每个类别独立执行 IoU 抑制
- 使用标准 IoU 计算
- `min_boxes` 参数支持最小框尺寸过滤

**无问题**。

### 5.2 `clamp()` 函数

```cpp
static inline float clamp(float val, float min, float max)
{
    return val > max ? max : val < min ? min : val;
}
```

**无问题**，标准 clamp 实现。

### 5.3 坐标从模型空间到图像空间的转换

在 `runInference()` 中：
```cpp
float scale_x = (float)input.cols / (float)target_size;
float scale_y = (float)input.rows / (float)target_size;
```

对于 ultralytics 已解码模型，坐标已经是模型输入空间的像素值（如 640×640），缩放回原始图像尺寸的逻辑是正确的。但需要注意 `pad_x/pad_y` 的计算是否与 ultralytics 的 letterbox 预处理一致。

---

## 六、JNI 接口审查

### 6.1 `detectBitmap` JNI 函数

```cpp+jni
jintArray Java_com_yolo_openiris_Yolov11Ncnn_detectBitmap(
    JNIEnv *env, jobject thiz, jobject bitmap, jint modelid, jint useGpu)
```

流程：
1. 从 Bitmap 读取像素 → 转换为 BGR cv::Mat
2. resize 到 target_size（保持宽高比，letterbox 填充）
3. 调用 `g_detector->runInference(mat)`
4. 返回 `int[]` 格式: `[count, x1,y1,x2,y2,confidence,label, ...]`

**🟡 中等问题 [P1]**: JNI 层直接使用 `g_detector` 全局单例，不支持并发调用。如果同时有实时检测和图片检测，可能产生竞争条件。

### 6.2 Java 层结果解析

`ImageDetectActivity.runYoloDetection()` 读取 `detectBitmap` 返回的 int 数组，按固定步长解析：

```kotlin
val rawResults = yolov11Ncnn.detectBitmap(bitmap, 0, 0)
val objects = mutableListOf<DetectedObject>()
var i = 0
while (i < rawResults.size) {
    val count = rawResults[i++]
    for (j in 0 until count) {
        val x1 = rawResults[i++]
        val y1 = rawResults[i++]
        val x2 = rawResults[i++]
        val y2 = rawResults[i++]
        val confidence = rawResults[i++]
        val label = rawResults[i++]
        objects.add(DetectedObject(x1, y1, x2, y2, confidence / 1000f, label))
    }
}
```

**无问题**，解析逻辑与 JNI 返回格式匹配。

---

## 七、问题关联性分析

### 7.1 三大症状的统一根因

所有三个已知问题（unknown 标签、置信度爆表、框图左上角）都源于**同一个根因**：

**`values_in_w` 布局检测 bug** → `num_anchors=1, num_values=8400`

```
values_in_w = (5 > 8400) = false
          ↓
num_anchors = 1, num_values = 8400
          ↓
    ┌─────┼─────┐
    ↓     ↓     ↓
  框图   标签   置信度
  左上角  unknown 爆表
```

| 症状 | 直接原因 | 根因 |
|------|---------|------|
| 框图在左上角 | 只处理第 1 个锚点（stride-8 网格位置 0,0 ≈ 像素 4,4） | values_in_w 误判 |
| 标签 unknown | class_index = max_index - 4，其中 max_index 可能 > 83 | values_in_w 误判 |
| 置信度 43814% | 从空间坐标数据中取最大值作为 class_prob | values_in_w 误判 |

### 7.2 潜在连锁问题

即使修复了 `values_in_w`，还有以下潜在问题需要关注：

1. **80 类模型的 `is_decoded_format` 误判**: num_w=84 >= 64，会进入 DFL 分支，对已解码坐标再做 DFL 解码 → 完全错误的结果
2. **标签回退到 COCO**: 自定义模型如果 labels.txt 缺失，会静默使用 COCO 标签
3. **预处理一致性**: C++ 端的 letterbox 填充方式需要与 ultralytics 导出时的预处理一致

---

## 八、根因分析和建议

### 8.1 优先级排序

| 优先级 | 问题 | 影响 | 建议修复方案 |
|--------|------|------|-------------|
| **P0** | `values_in_w` 布局检测错误 | 所有 ultralytics 单输出模型推理完全失败 | 修改判断逻辑：当 c=1 时，w 是每锚值数，h 是锚点数 |
| **P0** | `is_decoded_format` 阈值硬编码 | 80 类 ultralytics 模型会错误进入 DFL 分支 | 改用更可靠的格式检测（如检查 c 维度、文件名、元数据） |
| **P1** | 标签文件缺失时静默回退 COCO | 自定义模型显示错误类别名 | 失败时返回错误码，或标记为"自定义模型标签未找到" |
| **P1** | 模型格式检测依赖文件名 | 非标准文件名的模型无法正确识别 | 使用模型输出层名称或元数据文件检测 |
| **P2** | 预处理一致性未保证 | 坐标可能有系统性偏移 | 文档化预处理流程，或在模型元数据中记录预处理参数 |

### 8.2 `generate_proposals` 修复方案

```cpp
// 推荐的格式检测逻辑
static void generate_proposals(
    int stride,
    const ncnn::Mat& feat_blob,
    const float prob_threshold,
    std::vector<Object>& objects,
    bool is_decoded = false,        // 新增：由调用方显式指定
    int expected_num_classes = -1   // 新增：预期类别数
)
{
    const int num_w = feat_blob.w;
    const int num_h = feat_blob.h;
    const int num_c = feat_blob.c;

    // 格式检测：优先使用显式标记，回退到启发式
    bool decoded_format = is_decoded;
    if (!decoded_format) {
        // 启发式：如果 c=1 且 w 很小（< 100），可能是已解码的 2D 输出
        decoded_format = (num_c == 1 && num_w < 100 && num_h > 100);
    }

    if (decoded_format) {
        // 对于已解码格式，假设布局为 [w=4+nc, h=num_anchors, c=1]
        // 或 [w=num_anchors, h=4+nc, c=1]
        int num_anchors, num_values;
        if (num_c == 1) {
            // 2D Mat: w 是每锚值数（小），h 是锚点数（大）
            num_values = num_w;
            num_anchors = num_h;
        } else {
            // 3D Mat: 需要进一步判断
            // ...
        }
        // ... 处理逻辑
    } else {
        // DFL 分支
        // ...
    }
}
```

### 8.3 模型格式检测改进

建议在 `models/` 目录下增加元数据文件 `model_meta.json`：

```json
{
    "format": "decoded",           // "decoded" 或 "dfl"
    "num_classes": 1,
    "class_names": ["pig"],
    "input_size": 640,
    "preprocessing": "letterbox"   // 预处理方式
}
```

在 `runInference()` 中优先读取元数据，回退到文件名启发式检测。

---

## 九、审查结论

### 9.1 问题统计

| 严重程度 | 数量 | 问题 |
----------|------|------|
| 🔴 P0 关键 | 2 | values_in_w 误判、is_decoded_format 阈值 |
| 🟡 P1 中等 | 3 | 标签回退、模型格式检测、预处理一致性 |
| 🟢 P2 低 | 1 | 全局单例线程安全 |

### 9.2 核心结论

**APP 推理链路的根本问题在于 `generate_proposals()` 函数对 ultralytics 导出的单输出已解码模型的输出布局假设错误。**

ultralytics NCNN 导出将后处理（DFL 解码 + sigmoid + 坐标转换）嵌入模型计算图，输出为 `[w=4+nc, h=8400, c=1]` 的已解码格式。但 C++ 代码的 `values_in_w` 检测逻辑错误地将 w=5（小值）判断为"值不在 w 维度"，导致只处理 1 个锚点、读取 8400 个垃圾值作为类别概率。

修复 `values_in_w` 的判断逻辑（当 c=1 且 w < h 时，w 是每锚值数）即可同时解决全部三个已知问题。

### 9.3 测试建议

修复后应验证以下场景：
1. 内置 COCO 模型（3 输出 DFL 格式）推理正常
2. ultralytics 导出的 1 类模型（如 pig）推理正常
3. ultralytics 导出的 80 类模型推理正常
4. 标签显示正确
5. 置信度在 [0, 100]% 范围内
6. 框图位置准确

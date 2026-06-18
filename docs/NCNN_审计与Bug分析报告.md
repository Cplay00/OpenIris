# OpenIris NCNN 模块综合审查与 Bug 分析报告

**日期**: 2026-06-16  
**审查范围**: 训练导出流水线 / APP NCNN 推理引擎 / 自训练模型导入流程  
**触发原因**: 用户导入自训练 NCNN 模型后出现三个严重推理异常

---

## 第一部分：三模块审查报告

### 模块一：训练程序 NCNN 导出流水线

**审查文件**: `training/tools/export_pipeline.py`, `training/scripts/train.py`, `train_adapted.py` 等

#### 关键问题

| # | 严重程度 | 问题 | 位置 |
|---|---------|------|------|
| E1 | **🔴 关键** | **导出格式文档与实际不一致**：文档声称输出 `[class_id, confidence, x, y, w, h]`（归一化坐标），但 ultralytics NCNN 自动导出的实际格式为 `[4已解码坐标, nc个sigmoid概率, 8400个anchor]` | `export_pipeline.py` 文档注释 |
| E2 | **🔴 关键** | **未禁用模型内后处理**：ultralytics 的 NCNN 导出默认将 DFL 解码、锚点计算、sigmoid 等后处理嵌入模型图中，但 C++ 推理代码期望原始 YOLO 输出（需要自行解码 DFL） | `export_pipeline.py` export 步骤 |
| E3 | **🟡 高** | **预处理颜色空间不一致**：export 文档声称 `BGR->RGB`，但 C++ 代码保持 BGR 输入，模型训练时使用 RGB。会导致红蓝通道互换，降低精度 | `export_pipeline.py` 文档 vs `yolov11.cpp` |
| E4 | **🟡 高** | **未传 reg_max 参数**：C++ 代码硬编码 `reg_max=16`，但 `export_pipeline.py` 未在导出时记录此参数。如果训练时修改了 reg_max，C++ 端会解码错误 | `export_pipeline.py` |
| E5 | **🟢 中** | **labels.txt 使用 CRLF**：Windows 环境导出的 labels.txt 使用 `\r\n` 行尾，C++ 端需兼容处理 | `export_pipeline.py` |
| E6 | **🟢 低** | **缺少导出格式验证**：导出后未验证 NCNN 模型的输出层名称、维度是否与 C++ 代码期望一致 | `export_pipeline.py` |

#### 训练脚本分析

训练脚本 (`train.py`) 使用 ultralytics API，默认参数：
- `model`: yolov11n.pt
- `imgsz`: 640
- `rect`: 自动适应图片比例

训练脚本本身无问题，关键问题在导出环节。

---

### 模块二：APP NCNN 推理引擎

**审查文件**: `jni/yolov11.cpp`, `jni/yolov11.h`, `jni/yolov11ncnn.cpp`, `Yolov11Ncnn.java`

#### 关键问题

| # | 严重程度 | 问题 | 位置 |
|---|---------|------|------|
| C1 | **🔴 关键** | **输出解析格式与实际模型不匹配**：`generate_proposals()` 假设输出为 `[64+nc, 8400]`（64个DFL原始值+nc个原始logits），但 ultralytics NCNN 导出的实际输出为 `[4+nc, 8400]`（4个已解码坐标+nc个sigmoid概率） | `yolov11.cpp:generate_proposals` |
| C2 | **🔴 关键** | **越界内存读取**：当模型输出只有5个通道时，C++ 代码从 offset=64 读取 class_scores，实际读取的是内存中的垃圾数据 | `yolov11.cpp:generate_proposals` |
| C3 | **🔴 关键** | **重复 sigmoid**：模型已在图中对分类概率应用了 sigmoid，C++ 代码再次应用 sigmoid，导致概率值被推向极端（0 或 1） | `yolov11.cpp:generate_proposals` |
| C4 | **🔴 关键** | **重复 DFL 解码**：模型已在图中完成 DFL 解码（softmax+conv+anchor），C++ 代码再次进行 DFL softmax+expected value 计算，产生垃圾坐标 | `yolov11.cpp:generate_proposals` |
| C5 | **🟡 高** | **预处理颜色空间错误**：`preprocess` 保持 BGR 输入，但模型训练使用 RGB，导致红蓝通道互换 | `yolov11.cpp:preprocess` |
| C6 | **🟡 高** | **`fast_exp` 精度不足**：使用位运算近似指数函数，在边界值时精度偏差较大 | `yolov11.cpp:fast_exp` |
| C7 | **🟢 中** | **JNI 内存泄漏风险**：`loadModelFromPath` 中如果中途返回 JNI_FALSE，可能未释放已获取的 String 资源 | `yolov11ncnn.cpp` |

#### 代码架构优势

- 支持从 assets 和文件路径两种加载方式
- 支持 GPU/CPU 切换
- 有 zip 解压回退机制
- NMS 实现正确

---

### 模块三：自训练模型导入与推理流程

**审查文件**: `ImageDetectActivity.kt`, `SettingsActivity.kt`, `AiModelManager.kt`, `LabelPresetManager.kt`

#### 关键问题

| # | 严重程度 | 问题 | 位置 |
|---|---------|------|------|
| K1 | **🟡 高** | **标签加载双重路径不一致**：C++ 端通过 JNI 直接加载 labels.txt（正确），Kotlin 端通过 `loadLabels(config.selectedModel)` 加载（可能失败回退到 COCO）。两端可能使用不同的标签集 | `ImageDetectActivity.kt:loadLabels` |
| K2 | **🟡 高** | **loadLabels 回退逻辑缺陷**：当自定义模型的 labels.txt 无法从 assets 加载时，回退到 COCO 80 类标签，与自训练模型的标签数不匹配 | `ImageDetectActivity.kt:loadLabels` |
| K3 | **🟢 中** | **modelInfo 解析脆弱**：C++ 端 `getModelInfo` 返回的格式字符串如果变化，Kotlin 的解析会失败 | `ImageDetectActivity.kt` |
| K4 | **🟢 中** | **zip 解压覆盖风险**：同名模型重新导入时，未完全清理旧文件 | `SettingsActivity.kt` |
| K5 | **🟢 低** | **Spinner 选择模型后未验证模型完整性**：选择自定义模型后未检查 param/bin/labels 是否完整 | `ImageDetectActivity.kt` |

#### 导入流程分析

```
用户选择 ZIP → SettingsActivity 解压到临时目录 → 提取 param/bin/labels
→ 复制到 getDir("models")/modelName/ → 更新 Spinner → 选择模型
→ ImageDetectActivity.loadModel() → C++ loadModelFromPath()
```

流程设计合理，关键问题在 C++ 端的输出解析格式错误（C1-C4）。

---

## 第二部分：三个 Bug 深度分析

### Bug 1：所有识别结果标签都是 "unknown"

**根因**：C++ 推理代码的输出格式解析错误

**详细分析**：

1. 模型 `out0` 的实际输出格式为 `[5, 8400]`，即每个 anchor 有 5 个值：
   - `[0-3]`: 已解码的 cx, cy, w, h 坐标
   - `[4]`: sigmoid 后的分类概率（单类别为 1 个值，多类别为 nc 个值）

2. C++ `generate_proposals()` 期望的格式为 `[64+nc, 8400]`：
   - `[0-63]`: 16 个 DFL 分布值 × 4 个边
   - `[64-64+nc-1]`: nc 个原始 logits

3. 实际执行时：
   ```cpp
   // C++ 认为 offset=64 开始是 class scores
   // 但实际模型只有 5 个通道 (0-4)
   // 从 offset=64 读取时，读取的是越界的内存垃圾数据
   class_scores_ptr = feat_blob.row(64) + anchor_idx;
   ```

4. 垃圾数据的 `class_index` 值不可预测，多数情况下不在 `[0, class_names.size())` 范围内

5. `getClassName()` 对越界的 label 返回 `"unknown"`

**证据链**：
- `model.param` 第 275 行：`Concat cat_20 2 1 323 324 out0 0=0`（2 个输入：box[8400,4] + score[8400,1]）
- C++ 代码 `generate_proposals()` 中 `class_scores_ptr = feat_blob.row(64)` 越界读取
- `getClassName()` 对 `label >= class_names.size()` 返回 `"unknown"`

---

### Bug 2：框图位置极其怪异，总是出现在左上角

**根因**：C++ 对已解码坐标重新进行错误的 DFL 解码

**详细分析**：

1. 模型图已在内部完成完整的 box 解码：
   ```
   DFL(softmax+conv) → [8400,4] (cx, cy, w, h)
   → anchor_sub → anchor_add → div_2 → 归一化坐标
   ```

2. C++ `generate_proposals()` 错误地将 `[0-63]` 范围的数据当作 DFL 分布：
   ```cpp
   for (int k = 0; k < reg_max; k++) {
       // 实际读取的是 4 个真实坐标值 + 60 个垃圾值
       // softmax 后结果不可预测
       dst[k] = fast_exp(feat_ptr[k * 4 + 0] - alpha);  // LTRB 的 "L" DFL
   }
   ```

3. 垃圾数据经过 softmax + expected_value 计算后，产出的坐标值极小（接近 0）

4. 经过 `scale_unpad` 变换后，极小的坐标映射到原图左上角区域

5. 同时，由于 C++ 计算的 `rect = {cx, cy, w, h}` 中 cx/cy 是垃圾值，`cv::rectangle` 直接用这些值绘图，导致框图位置完全错误

**证据链**：
- `generate_proposals()` 中 DFL 解码循环读取 offset 0-63 的数据
- 模型实际输出只有 5 个通道，offset 4-63 是越界垃圾
- softmax 对垃圾数据的处理结果不可预测，但通常产生极小值
- 极小坐标值 → 左上角

---

### Bug 3：部分置信度达到 100% 上限

**根因**：双重 sigmoid 导致概率被推向极端

**详细分析**：

1. 模型图已在 `sigmoid_162` 层对分类 logits 应用了 sigmoid：
   ```
   class logits → sigmoid → [0, 1] 范围的概率
   ```

2. C++ `generate_proposals()` 再次对已激活的概率应用 sigmoid：
   ```cpp
   float conf = 1.f / (1.f + expf(-class_score));  // 第二次 sigmoid
   ```

3. 数学分析：设原始 logit 为 `z`，则：
   - 模型输出 = sigmoid(z) = σ(z)
   - C++ 计算 = sigmoid(σ(z)) = σ(σ(z))

4. 当 σ(z) > 0.5 时（即 z > 0），σ(σ(z)) 会更快趋向 1.0：
   - σ(0.7) ≈ 0.668 → σ(0.668) ≈ 0.661（影响不大）
   - σ(2.0) ≈ 0.881 → σ(0.881) ≈ 0.707（被压缩）
   - σ(5.0) ≈ 0.993 → σ(0.993) ≈ 0.730（被大幅压缩）

5. 但由于 Bug 1（读取垃圾数据），`class_score` 可能是一个很大的正值
   - 垃圾值 100.0 → σ(100.0) ≈ 1.0 → 显示 100%
   - 这解释了为什么部分结果置信度恰好为 100%

6. `detectBitmap()` 中 `result_data[i * 6 + 5] = (int)(obj.prob * 1000)` 将 1.0 × 1000 = 1000
   - Kotlin 端 `rawResults[i + 5] / 1000f = 1.0f = 100%`

**证据链**：
- 模型 param 第 274 行：`Sigmoid sigmoid_162 1 1 301 324`（sigmoid 已在图中）
- C++ `generate_proposals()` 中 `conf = 1.f / (1.f + expf(-class_score))`（重复 sigmoid）
- 垃圾数据中大正值 → σ(large) ≈ 1.0 → 100% 置信度

---

## 第三部分：模型输出格式详细对比

### 实际模型输出（from model.param）

```
最终输出层: out0 = Concat(cat_20)
  输入1: blob 323 (box coordinates) - shape [8400, 4]
         经过: DFL → softmax → conv1x1 → reshape → anchor_sub → anchor_add → div_2
         含义: 已解码的 (cx, cy, w, h) 归一化坐标
  输入2: blob 324 (class scores) - shape [8400, 1]
         经过: sigmoid_162
         含义: 已激活的分类概率 [0, 1]
  输出: out0 [8400, 5] 或转置后 [5, 8400]
```

### C++ 代码期望的格式

```
out0 [nc+64, 8400]
  [0:64, :]   = 16个DFL值 × 4个边（原始logits，未经softmax）
  [64:64+nc,:] = nc个分类logits（原始值，未经sigmoid）
```

### 差异总结

| 维度 | 模型实际输出 | C++ 期望 | 影响 |
|------|------------|---------|------|
| 通道数 | 4+nc | 64+nc | C++ 越界读取垃圾数据 |
| Box 编码 | 已解码坐标 (cx,cy,w,h) | DFL 原始分布 (16值/边) | C++ 对已解码值重新 DFL → 垃圾坐标 |
| 分类编码 | sigmoid 概率 | 原始 logits | C++ 重复 sigmoid → 概率推向极端 |
| 坐标范围 | 归一化 [0,1] | 原始 DFL 值 | 坐标系统完全不匹配 |

---

## 第四部分：附加发现

### 4.1 预处理颜色空间不一致

| 环节 | 颜色空间 | 归一化 |
|------|---------|--------|
| Python 训练 (ultralytics) | RGB | /255.0 |
| C++ 推理 (当前) | **BGR** | /255.0 |
| export_pipeline 文档 | 声称 BGR→RGB | /255.0 |

**影响**：红蓝通道互换，模型识别精度下降。不会导致完全失效，但会降低检测质量。

### 4.2 锚点计算已在模型内完成

模型图中已包含 `MemoryData pnnx_fold_anchor_points.1`，存储了所有 8400 个锚点的 xy 坐标，并在图内通过 `sub_15`, `add_16`, `div_18` 完成了锚点运算。C++ 代码中 `generate_proposals()` 的 `x = (j + 0.5f - offset) * stride` 锚点计算是**多余且错误的**。

### 4.3 C++ 代码中 NMS 和 draw 函数

`nms()` 和 `draw()` 函数的实现本身是正确的。它们期望输入为 (x, y, w, h) 的左上角坐标格式，这与模型输出的 (cx, cy, w, h) 中心点格式不同，但由于上游 `generate_proposals()` 已经错误，传入的数据本身已经是垃圾值。

---

## 第五部分：修复方向建议（仅讨论，不修复）

### 方案 A：修改 C++ 推理代码（推荐）

**优点**：无需重新导出模型，兼容 ultralytics 标准导出格式

**需要修改**：
1. `generate_proposals()` 改为直接读取已解码的坐标和概率：
   - box: `feat_blob.row(0..3)` → 直接取 cx, cy, w, h
   - score: `feat_blob.row(4..4+nc-1)` → 直接取 sigmoid 概率（不再 sigmoid）
2. 从 cx, cy, w, h 转换为 x1, y1, x2, y2（减去半宽半高）
3. 修复预处理：BGR → RGB
4. `draw()` 函数适配中心点坐标

**关键代码修改点**：
```cpp
// 旧代码（错误）
float class_score = feat_blob[row64 + idx];
float conf = sigmoid(class_score);  // 重复sigmoid
// DFL 解码 16 个值...

// 新代码（正确）
float cx = feat_blob[0 * 8400 + i];  // 已解码坐标
float cy = feat_blob[1 * 8400 + i];
float w  = feat_blob[2 * 8400 + i];
float h  = feat_blob[3 * 8400 + i];
float conf = feat_blob[(4 + class_id) * 8400 + i];  // 已sigmoid概率
```

### 方案 B：修改导出流水线

**优点**：C++ 代码改动最小

**需要修改**：
1. 使用 `export_pipeline.py` 时指定 `opset=11` 并禁用后处理
2. 或手动修改 NCNN param 文件，移除后处理层
3. 确保输出为原始 DFL 值和 logits

**风险**：ultralytics 的 NCNN 导出后处理是自动添加的，禁用可能需要深入修改导出流程

### 方案 C：双模式兼容（最稳健）

**思路**：C++ 代码自动检测输出格式，兼容两种模式

**实现**：
1. 检查 `out0` 的维度：如果 `h == 64+nc`，使用原始 DFL 解码；如果 `h == 4+nc`，直接读取已解码值
2. 在 `loadLabelsFromPath` 后自动确定 `nc`，从而推断输出格式

---

**结论**：三个 bug 的**根本原因是同一个**——ultralytics NCNN 导出自动嵌入了后处理（DFL 解码、锚点计算、sigmoid），但 C++ 推理代码期望的是原始 YOLO 输出格式。修复的核心是让两端的输出格式约定一致。推荐方案 A，直接修改 C++ 代码适配 ultralytics 标准 NCNN 导出格式。


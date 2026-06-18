# OpenIris APP 功能与推理链路深度审查报告

> 审查日期: 2026-06-17  
> 审查范围: 功能缺陷、推理链路、UI 逻辑  
> 审查模式: 只读规划（不做代码修改）

---

## 目录

1. [导出设置：用户手动设置路径无效](#1-导出设置用户手动设置路径无效)
2. [实时检测-检测结果汇总无法使用](#2-实时检测-检测结果汇总无法使用)
3. [实时检测-AI识别是否可用](#3-实时检测-ai识别是否可用)
4. [实时检测-无法保存截图](#4-实时检测-无法保存截图)
5. [视频检测问题](#5-视频检测问题)
6. [图片/视频检测-"综合分析"卡片栏显示逻辑问题](#6-综合分析卡片栏显示逻辑问题)
7. [AI模型参数控制：新增"思考"开关](#7-ai模型参数控制思考开关)
8. [添加*.pt文件弹窗问题](#8-添加pt文件弹窗问题)
9. [优先级排序总结](#9-优先级排序总结)

---

## 1. 导出设置：用户手动设置路径无效

### 涉及文件
- `training/gui/main_window.py` — `_export_model()` 方法（行 716-748）
- `training/tools/export_pipeline.py` — `main()` 参数解析（行 370-443）

### 根因分析

**核心问题：打包模式下导出功能完全禁用**

`_export_model()` 方法在 `IS_PACKAGED = True`（PyInstaller 打包的 EXE）时，**直接跳过实际导出**，仅输出提示信息：

```python
# main_window.py 行 737-740
if IS_PACKAGED:
    self._log(f"{t('export')}... (direct mode)")
    self._log("Export requires Python environment. Use command line.")
```

这意味着：无论用户选择了什么路径，EXE 模式下都不会执行实际导出。GUI 仍然弹出文件选择对话框（`filedialog.askdirectory`），用户选择路径后得到的只是一条日志提示。

**次要问题：格式选项逻辑错误**

```python
# main_window.py 行 745-747
fmt = self.export_format_var.get()
if t("export_format_pt") in fmt or "PyTorch" in fmt:
    cmd.extend(["--format", "onnx"])
```

当用户选择 "PyTorch" 格式时，实际传递的是 `--format onnx`。而 `export_pipeline.py` 的 `--format` 参数只支持 `["ncnn", "onnx"]`，没有 `pt` 选项。GUI 下拉框中的 "PyTorch" 选项具有误导性——实际导出的是 ONNX 格式。

**第三：BOM 编码问题**

`export_pipeline.py` 文件包含 UTF-8 BOM (`\xEF\xBB\xBF`)，在某些 Windows 环境下可能导致 `argparse` 解析路径参数异常。

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | EXE 模式支持导出 | 方案 A：在打包时嵌入 Python 运行时，通过 `_find_python()` 已有的逻辑查找嵌入 Python，用 subprocess 调用 `export_pipeline.py`<br>方案 B：将 `export_pipeline.py` 的核心逻辑重构为可直接 import 的模块，在 EXE 进程内直接调用（无需 subprocess） |
| P1 | 格式选项修正 | 移除 "PyTorch" 格式选项（`export_pipeline.py` 不支持 `--format pt`），或在 GUI 中明确标注 "PyTorch → ONNX（中间格式）" |
| P2 | BOM 编码清理 | 移除 `export_pipeline.py` 的 UTF-8 BOM，使用无 BOM 的 UTF-8 编码 |
| P2 | 路径校验 | 在 `_export_model()` 中添加 `--output` 路径有效性校验（目录是否存在、是否有写权限） |

---

## 2. 实时检测-检测结果汇总无法使用

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/RealtimeDetectActivity.kt` — `updateResultSummary()` 方法（行 527-571）
- `buildapk/app/src/main/res/layout/activity_realtime_detect.xml` — 结果汇总卡片布局
- `buildapk/app/src/main/java/com/yolo/openiris/detection/SlidingWindowTracker.kt` — 滑动窗口追踪器

### 根因分析

**`updateResultSummary()` 方法本身是完整的**，它正确地：
1. 从 `yoloTracker` 获取 YOLO 检测摘要
2. 从 `aiTracker` 获取 AI 识别摘要
3. 合并两者到 `combinedMap`（取 count 最大值，平均置信度）
4. 更新 FlexboxLayout 显示胶囊标签

**问题在于触发时机和 AI 数据来源：**

1. **YOLO 数据来源**：`analyzeCurrentFrame()` 方法（行 456-487）每 1 秒执行一次，调用 `yolov11Ncnn.detectBitmap()` 获取检测结果并喂给 `yoloTracker`。此路径正常工作。

2. **AI 数据来源**：`callAiModel()` 方法（行 489-525）按 `aiModelManager.getCallIntervalSeconds()` 间隔调用 AI 模型。**仅当 `isAiEnabled = true` 且 AI 调用成功时**，`aiTracker` 才会有数据。

3. **汇总卡片可见性**：布局中 `resultSummaryCard` 位于底部，使用 `BottomSheet` 样式。如果卡片高度为 0 或被遮挡，用户看不到汇总内容。

**最可能的原因**：用户未启用 AI（`isAiEnabled = false`），此时 `aiTracker` 始终为空，"综合" 标签栏只显示 YOLO 结果。如果用户期望看到更丰富的汇总（如场景描述、差异分析），仅靠 YOLO 数据无法满足。

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P1 | 无 AI 时的汇总增强 | 当 `isAiEnabled = false` 时，在 `updateResultSummary()` 中基于 YOLO 结果生成更详细的汇总文本（如 "检测到 N 类 M 个对象"），直接显示在综合卡片中 |
| P1 | 汇总卡片可见性保证 | 在 `onCreate()` 中确保 `resultSummaryCard` 的 `minHeight` 不为 0，添加初始占位文本 "等待检测结果..." |
| P2 | 汇总文本化 | 在综合卡片中添加一个 `MaterialTextView` 用于显示文字摘要（而不仅是胶囊标签），例如 "画面中有 3 个人、2 辆车" |

---

## 3. 实时检测-AI识别是否可用

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/RealtimeDetectActivity.kt` — `callAiModel()`（行 489-525）、`analyzeCurrentFrame()`（行 456-487）
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiModelManager.kt` — `callWithFallback()`、`callWithFallbackAndImage()`
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiApiClient.kt` — API 调用实现
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiProvider.kt` — 提供商配置
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiModel.kt` — 模型配置

### 根因分析

**AI 识别功能已完整实现，但依赖多个前置条件。** 调用链路如下：

```
RealtimeDetectActivity
  ├─ startAiCallLoop() → 定时调用 callAiModel()
  │   └─ aiModelManager.callWithFallback(prompt, onError)
  │       └─ AiApiClient.callModelStream(provider, model, prompt)
  │           └─ HTTP POST → API 端点
  │               └─ 解析响应 → StructuredOutput
  │                   └─ aiTracker.addDetection() → updateResultSummary()
  │
  └─ startScreenshotAnalysis() → 每秒调用 analyzeCurrentFrame()
      └─ yolov11Ncnn.detectBitmap() → yoloTracker.addDetection()
          └─ updateResultSummary()
```

**关键前置条件（全部满足才可用）：**

1. `isAiEnabled = true` — 用户需在 UI 中点击 AI 开关启用
2. `aiModelManager.getEnabledModels()` 非空 — 需在设置中配置至少一个启用的 AI 模型
3. `AiProvider.apiKey` 非空 — 需配置有效的 API 密钥
4. `AiProvider.baseUrl` 可达 — API 端点需网络可达
5. AI 模型 `hasVision = true` — `callWithFallbackAndImage()` 需要视觉模型（实时检测场景中 `callAiModel()` 使用纯文本 prompt，不需要 vision，但 `analyzeCurrentFrame()` 中的截图分析需要）

**潜在问题点：**

1. **`callAiModel()` 使用纯文本 prompt**（行 496-501）：发送的是 "当前画面 YOLO 检测到：xxx，请基于以上检测结果补充识别"。这意味着 AI 模型**看不到实际画面**，只能基于 YOLO 检测结果做推测性补充。这是一个设计缺陷——AI 无法真正"看到"画面。

2. **`analyzeCurrentFrame()` 独立运行**：每秒捕获帧并运行 YOLO 检测，但**不调用 AI**。AI 调用由 `callAiModel()` 单独处理。两个循环是并行的，但 `callAiModel()` 不传图像给 AI。

3. **无降级处理**：如果 AI 调用失败（网络超时、API 错误），仅 Toast 提示，`aiTracker` 不更新，综合结果卡片不刷新。

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | 传递图像给 AI | 修改 `callAiModel()` 方法，在调用前捕获当前帧（复用 `yolov11Ncnn.captureFrame()`），将图像 base64 传给 `aiModelManager.callWithFallbackAndImage()`。这是 AI 识别功能**能否真正可用**的关键 |
| P1 | AI 配置检查 | 在 `onResume()` 中检查 AI 配置是否完整（API key、模型是否启用），不完整时禁用 AI 开关并提示用户 |
| P1 | 失败重试 | AI 调用失败时，增加指数退避重试机制（最多 3 次），而非直接放弃 |
| P2 | 状态指示器 | 在 UI 中添加 AI 状态指示（如 "AI 就绪" / "AI 连接中..." / "AI 不可用"），让用户明确知道 AI 是否在工作 |

---

## 4. 实时检测-无法保存截图

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/RealtimeDetectActivity.kt` — `captureAndSave()`（行 280-310）、`saveBitmap()`（行 347-380）

### 根因分析

`saveBitmap()` 方法（行 347-380）：

```kotlin
private fun saveBitmap(bitmap: Bitmap) {
    // Android 10+ 不检查 WRITE_EXTERNAL_STORAGE
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "无存储权限，无法保存截图", Toast.LENGTH_SHORT).show()
            return
        }
    }

    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "OpenIris_$timeStamp.jpg"

    val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val configPath = configManager.loadConfig().imageExportPath
    val storageDir = if (!configPath.isNullOrBlank()) {
        File(baseDir, configPath).apply { mkdirs() }
    } else {
        File(baseDir, "OpenIris").apply { mkdirs() }
    }
    val file = File(storageDir, fileName)

    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
}
```

**问题 1：Android 10+ Scoped Storage 违规**（关键）

在 Android 10（API 29）及以上，`Environment.getExternalStoragePublicDirectory()` 已被标记为 `@Deprecated`。虽然当前代码跳过了 `WRITE_EXTERNAL_STORAGE` 权限检查，但直接使用 `File` API 写入公共目录**在某些设备/ROM 上可能失败**（尤其是 Android 11+ 的 Scoped Storage 严格执行模式）。

**问题 2：`mkdirs()` 返回值未检查**

`File(baseDir, "OpenIris").apply { mkdirs() }` — 如果 `mkdirs()` 返回 `false`（目录创建失败），后续的 `file.outputStream()` 会抛出 `FileNotFoundException`。

**问题 3：`captureAndSave()` 中的帧捕获可能失败**

```kotlin
if (yolov11Ncnn.captureFrame(bitmap)) { ... }
```

`captureFrame()` 返回 `false` 时，仅 Toast 提示 "截图失败：无法获取当前帧"。可能原因：
- SurfaceView 未就绪（`surfaceCreated` 未回调）
- 相机未打开（`onResume` 之前）
- Bitmap 尺寸与相机分辨率不匹配

**问题 4：AndroidManifest.xml 中的权限声明**

需要确认是否声明了 `WRITE_EXTERNAL_STORAGE` 和 `maxSdkVersion`：
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | 使用 MediaStore API | 对 Android 10+，使用 `MediaStore.Images.Media` API 保存图片到公共相册，而非直接 File I/O。这是最可靠的跨版本方案 |
| P0 | 错误处理增强 | 在 `saveBitmap()` 中添加 try-catch，捕获 `FileNotFoundException`、`SecurityException` 等，给出具体错误提示 |
| P1 | 目录创建校验 | 检查 `mkdirs()` 返回值，失败时 fallback 到应用私有目录 `getExternalFilesDir(Environment.DIRECTORY_PICTURES)` |
| P1 | 帧捕获超时 | 在 `captureAndSave()` 中添加 SurfaceView 就绪检查，未就绪时提示 "相机初始化中，请稍候" |
| P2 | 权限声明检查 | 确认 AndroidManifest.xml 中 `WRITE_EXTERNAL_STORAGE` 的 `maxSdkVersion="28"` |

---

## 5. 视频检测问题

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/VideoDetectActivity.kt` — 完整 Activity
- `buildapk/app/src/main/res/layout/activity_video_detect.xml` — 布局文件

### 问题 A：显示位置不居中，不自适应大小

#### 根因分析

布局文件中 `VideoView` 设置为：
```xml
<VideoView
    android:id="@+id/videoView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

`VideoView` 使用 `match_parent` 填满整个屏幕，但**未设置缩放模式**。Android 的 `VideoView` 默认基于 `MediaPlayer` 的 `setDisplay()`，其缩放行为取决于：
- 视频原始分辨率与 View 尺寸的宽高比差异
- 未设置 `android:scaleType`（`VideoView` 不支持 `scaleType` 属性）

结果：视频可能被拉伸、裁剪或留黑边，无法自适应居中。

#### 修复方案

**方案 A（推荐）：替换 VideoView 为 ExoPlayer + AspectRatioFrameLayout**

```xml
<com.google.android.exoplayer2.ui.AspectRatioFrameLayout
    android:id="@+id/aspectRatioFrame"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layout_gravity="center"
    app:resize_mode="fit">

    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</com.google.android.exoplayer2.ui.AspectRatioFrameLayout>
```

ExoPlayer 的 `PlayerView` 支持 `resize_mode`：`fit`（自适应居中）、`zoom`（填充裁剪）、`fill`（拉伸）。

**方案 B（轻量）：自定义 VideoView 缩放**

继承 `VideoView`，重写 `onMeasure()` 实现等比缩放居中：

```kotlin
class CenterCropVideoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : VideoView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
```

### 问题 B：没有视频播放进度条

#### 根因分析

布局文件中**已存在** `LinearProgressIndicator`（`progressBar`），但它仅用于**视频分析进度**（`startAnalysis()` 中更新），而非播放进度。

代码中虽有播放进度更新逻辑（行 304）：
```kotlin
progressBar.progress = ((currentTimeMs.toFloat() / durationMs) * 100).toInt()
```

但这仅在 `startAnalysis()` 的协程中执行，**不在普通播放时执行**。`togglePlayPause()` 方法只调用 `videoView.start()/pause()`，不更新任何进度 UI。

**缺少：**
1. 播放时的进度条 UI（SeekBar 或独立的播放进度条）
2. `MediaPlayer.setOnTimedTickListener()` 或 `Handler` 轮询播放位置
3. 拖动进度条跳转功能

#### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | 添加播放进度条 | 在布局中添加 `SeekBar`（或复用 `LinearProgressIndicator`），在 `togglePlayPause()` 启动播放时，用 `Handler.postDelayed()` 每 500ms 更新进度 |
| P1 | 进度拖动 | 实现 `SeekBar.OnSeekBarChangeListener`，`onStopTrackingTouch()` 时调用 `videoView.seekTo(progress)` |
| P1 | 播放完成处理 | 设置 `videoView.setOnCompletionListener()` 重置进度和按钮状态 |
| P2 | ExoPlayer 迁移 | 长期方案：迁移到 ExoPlayer，自带进度条、手势控制、画中画等能力 |

---

## 6. 综合分析卡片栏显示逻辑问题

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/ImageDetectActivity.kt` — `updateCombinedResults()`（行 433-465）
- `buildapk/app/src/main/java/com/yolo/openiris/VideoDetectActivity.kt` — `updateCombinedResults()`（行 406-433）
- `buildapk/app/src/main/java/com/yolo/openiris/RealtimeDetectActivity.kt` — `updateResultSummary()`（行 527-571）

### 根因分析

三个检测 Activity 的综合分析逻辑**不一致**：

#### ImageDetectActivity（图片检测）— 逻辑较好
```kotlin
// 行 311-318: YOLO 结果立即显示
displayYoloResults(yoloResult)
analysisResult = AnalysisResult.fromResults(mode = DetectionMode.IMAGE, yoloResult = yoloResult)

// 行 320-345: AI 结果异步到达后更新
if (isAiEnabled) {
    val aiResult = aiModelManager.callWithFallbackAndImage(...)
    if (aiResult.success && aiResult.structuredOutput != null) {
        displayAiResults(aiResult.structuredOutput) // 内部调用 updateCombinedResults()
    }
}
```

✅ **YOLO 先显示，AI 完成后合并** — 符合预期。

#### VideoDetectActivity（视频检测）— 逻辑有缺陷
```kotlin
// 行 406-433: updateCombinedResults() 只镜像 YOLO 结果
private fun updateCombinedResults() {
    flexboxCombined.removeAllViews()
    // ⚠️ 只取 yoloResult，完全忽略 aiTracker
    val allObjects = analysisResults.flatMap { it.yoloResult?.objects ?: emptyList() }
    ...
}
```

❌ **综合结果只显示 YOLO，AI 结果被忽略。** `startAiCallLoop()` 中的 AI 结果（行 452-475）调用了 `aiTracker.addDetection()`，但 `updateCombinedResults()` 没有读取 `aiTracker`。

#### RealtimeDetectActivity（实时检测）— 逻辑正确
```kotlin
// 行 558-571: 合并 YOLO + AI
val combinedMap = mutableMapOf<String, SlidingWindowTracker.ObjectStats>()
yoloSummary.forEach { combinedMap[stats.name] = stats.copy() }
aiSummary.forEach { /* 合并逻辑 */ }
```

✅ **正确合并 YOLO + AI** — 但依赖 AI 成功调用。

### 各场景预期行为 vs 实际行为

| 场景 | 预期行为 | 图片检测 | 视频检测 | 实时检测 |
|------|---------|---------|---------|---------|
| 不使用 AI | 综合栏直接输出 YOLO 结果 | ✅ | ✅（但只镜像 YOLO） | ✅ |
| 使用 AI | 等待 AI 完成再汇总 | ✅ | ❌（AI 结果被忽略） | ✅（依赖 AI 成功） |

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | VideoDetectActivity 综合结果修复 | 重写 `updateCombinedResults()`，参考 `RealtimeDetectActivity` 的实现，合并 `yoloTracker` 和 `aiTracker` 数据 |
| P1 | 统一综合分析逻辑 | 将三个 Activity 的综合分析逻辑抽取到一个共享工具类 `CombinedResultBuilder`，避免代码重复和逻辑不一致 |
| P1 | AI 未启用时的即时汇总 | 当 `isAiEnabled = false` 时，YOLO 检测完成后立即更新综合栏（当前行为正确，但需确保 UI 刷新时机一致） |
| P2 | 加载状态指示 | AI 分析进行中时，在综合栏显示 "AI 分析中..." 占位符，避免用户以为功能无响应 |

---

## 7. AI模型参数控制：新增"思考"开关

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiModel.kt` — `enableReasoning` 字段（行 17）
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiApiClient.kt` — thinking 参数注入（行 505-506）
- `buildapk/app/src/main/res/layout/dialog_model_settings_basic.xml` — `switchReasoning` UI（行 57）
- `buildapk/app/src/main/java/com/yolo/openiris/AiProviderEditActivity.kt` — `onModelSettingsConfirmed()` 回调（行 373-384）

### 根因分析

**"思考"开关的基础架构已存在**，但存在以下问题：

1. **数据模型已有**：`AiModel.enableReasoning: Boolean = false`（行 17）
2. **UI 控件已有**：`dialog_model_settings_basic.xml` 中有 `switchReasoning`（行 57）
3. **API 调用已处理**：`AiApiClient.kt` 行 505-506：
   ```kotlin
   if (!model.enableReasoning) {
       body["thinking"] = mapOf("type" to "disabled")
   }
   ```
4. **回调已连接**：`AiProviderEditActivity.onModelSettingsConfirmed()` 接收 `enableReasoning` 参数并更新模型

**潜在问题：**

1. **仅支持 Anthropic 格式**：`thinking` 参数注释写明 "仅 Anthropic 格式支持"。对于 OpenAI 兼容格式（如 DeepSeek、Qwen），`thinking` 参数可能被 API 忽略或报错。不同提供商的思考模式参数名不同：
   - Anthropic: `thinking.type = "enabled"/"disabled"`
   - DeepSeek: `enable_thinking = true/false`
   - Qwen: 不同模型有不同参数

2. **开关标签不明确**：`switchReasoning` 的标签文案需要确认（当前 XML 中是中文 "推理" 还是 "深度思考"）。

3. **开关状态持久化**：需确认 `AiProviderEditActivity` 保存模型时，`enableReasoning` 是否正确序列化到 `AiModelConfigStore`。

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | 多提供商适配 | 在 `AiApiClient.kt` 中根据 `provider.apiFormat` 和模型特征判断应使用哪种 thinking 参数格式：<br>- Anthropic: `thinking.type`<br>- DeepSeek: `enable_thinking`<br>- 通用 OpenAI: 不发送 thinking 参数（由模型自行决定） |
| P1 | UI 标签优化 | 将 `switchReasoning` 标签改为 "深度思考 (Reasoning)"，添加副标题说明："启用后 AI 会进行更深入的分析，但响应时间更长" |
| P1 | 状态持久化验证 | 检查 `AiModelConfigStore` 的序列化逻辑，确认 `enableReasoning` 字段正确保存和读取 |
| P2 | 思考预算控制 | 对支持 thinking 的模型，添加 `thinking_budget` 参数（如 Anthropic 的 `budget_tokens`），防止思考过程消耗过多 token |

---

## 8. 添加*.pt文件弹窗问题

### 涉及文件
- `buildapk/app/src/main/java/com/yolo/openiris/SettingsActivity.kt` — `addModelFromUri()`（行 182-210）、`showPtConversionDialog()`（行 294-330）

### 根因分析

当用户通过文件选择器选中 `.pt` 文件时，`addModelFromUri()` 会调用 `showPtConversionDialog()`。该弹窗有**三个按钮**：

```kotlin
// 行 316-328
.setNeutralButton("分享文件") { _, _ -> sharePtFile(ptFilePath) }
.setPositiveButton("复制命令") { _, _ ->
    // 复制 export_pipeline.py 命令到剪贴板
}
.setNegativeButton("取消", null)
```

**问题描述：**

1. **没有 "确认/导入" 按钮**：弹窗只提供 "复制命令"（正向按钮）、"分享文件"（中性按钮）和 "取消"（负向按钮）。用户无法直接导入 `.pt` 文件。

2. **用户体验断裂**：用户选择 `.pt` 文件后期望导入，但看到的是 "需要转换格式" 的提示。"复制命令" 按钮只是复制命令到剪贴板，**不执行任何导入操作**。

3. **用户可能将 "复制命令" 误认为 "确认"**：点击后只看到 Toast "命令已复制到剪贴板"，没有后续操作引导。用户可能认为 "确认按钮无效"。

4. **`.pt` 文件被复制到 `share_temp` 但未清理**：`addModelFromUri()` 将 `.pt` 文件复制到 `filesDir/share_temp/` 目录（行 193-199），但只有 "分享文件" 按钮会使用它。如果用户点 "复制命令" 或 "取消"，临时文件不会被清理。

### 修复方案

| 优先级 | 修复项 | 具体方案 |
|--------|--------|---------|
| P0 | 弹窗文案优化 | 标题改为 "此文件需要转换格式"，正文用更简洁的语言说明 `.pt` 文件不能直接导入，需要先转换为 NCNN 格式 |
| P0 | 按钮文案明确化 | 将 "复制命令" 改为 "复制转换命令"，让用户明确知道点击后只是复制命令 |
| P1 | 添加 "分享到电脑" 引导 | 将 "分享文件" 从 Neutral 按钮提升为主要操作，或在弹窗中添加图文说明 "步骤 1: 分享到电脑 → 步骤 2: 运行命令 → 步骤 3: 导入 .zip" |
| P1 | 临时文件清理 | 在 `showPtConversionDialog()` 的所有按钮回调中清理 `share_temp` 目录，或在 `onDestroy()` 中统一清理 |
| P2 | 长期方案：设备端转换 | 考虑集成 ONNX Runtime Mobile，在设备端完成 `.pt` → ONNX → NCNN 转换（工作量大，优先级低） |

---

## 9. 优先级排序总结

### P0（必须修复 — 功能不可用或数据丢失风险）

| # | 问题 | 修复项 | 工作量 |
|---|------|--------|--------|
| 1 | 导出设置 | EXE 模式支持导出 | 大 |
| 2 | AI 识别 | 传递图像给 AI（`callAiModel()` 改用 `callWithFallbackAndImage()`） | 中 |
| 3 | 截图保存 | 使用 MediaStore API 替代 File I/O | 中 |
| 4 | 视频检测 | 添加播放进度条 | 小 |
| 5 | 综合分析 | VideoDetectActivity 综合结果修复（加入 AI 数据） | 小 |
| 6 | 思考开关 | 多提供商 thinking 参数适配 | 中 |
| 7 | pt 弹窗 | 弹窗文案和按钮优化 | 小 |

### P1（应该修复 — 影响用户体验）

| # | 问题 | 修复项 | 工作量 |
|---|------|--------|--------|
| 1 | 导出设置 | 格式选项修正（移除误导性 "PyTorch"） | 小 |
| 2 | 检测汇总 | 无 AI 时汇总增强 + 卡片可见性保证 | 小 |
| 3 | AI 识别 | 配置完整性检查 + 失败重试 | 中 |
| 4 | 截图保存 | 目录创建校验 + 帧捕获超时处理 | 小 |
| 5 | 视频检测 | 进度拖动 + 播放完成处理 | 小 |
| 6 | 综合分析 | 统一综合分析逻辑到共享工具类 | 中 |
| 7 | 思考开关 | UI 标签优化 + 状态持久化验证 | 小 |
| 8 | pt 弹窗 | 临时文件清理 + 引导流程优化 | 小 |

### P2（可以修复 — 优化和健壮性）

| # | 问题 | 修复项 | 工作量 |
|---|------|--------|--------|
| 1 | 导出设置 | BOM 编码清理 + 路径校验 | 小 |
| 2 | 检测汇总 | 汇总文本化 | 小 |
| 3 | AI 识别 | 状态指示器 | 小 |
| 4 | 截图保存 | 权限声明检查 | 小 |
| 5 | 视频检测 | ExoPlayer 迁移（长期方案） | 大 |
| 6 | 综合分析 | AI 分析中占位符 | 小 |
| 7 | 思考开关 | 思考预算控制 | 中 |
| 8 | pt 弹窗 | 设备端转换（长期方案） | 很大 |

---

## 附录：关键文件清单

| 文件路径 | 用途 |
|---------|------|
| `training/gui/main_window.py` | 训练 GUI 主窗口（导出设置） |
| `training/tools/export_pipeline.py` | 模型导出流水线 |
| `buildapk/app/src/main/java/com/yolo/openiris/RealtimeDetectActivity.kt` | 实时检测 Activity |
| `buildapk/app/src/main/java/com/yolo/openiris/ImageDetectActivity.kt` | 图片检测 Activity |
| `buildapk/app/src/main/java/com/yolo/openiris/VideoDetectActivity.kt` | 视频检测 Activity |
| `buildapk/app/src/main/java/com/yolo/openiris/SettingsActivity.kt` | 设置页面（模型导入） |
| `buildapk/app/src/main/java/com/yolo/openiris/ai/AiModelManager.kt` | AI 模型管理器 |
| `buildapk/app/src/main/java/com/yolo/openiris/ai/AiApiClient.kt` | AI API 客户端 |
| `buildapk/app/src/main/java/com/yolo/openiris/ai/AiModel.kt` | AI 模型数据类 |
| `buildapk/app/src/main/java/com/yolo/openiris/ai/AiProvider.kt` | AI 提供商数据类 |
| `buildapk/app/src/main/java/com/yolo/openiris/config/AppConfig.kt` | 应用全局配置 |
| `buildapk/app/src/main/res/layout/activity_video_detect.xml` | 视频检测布局 |
| `buildapk/app/src/main/res/layout/activity_realtime_detect.xml` | 实时检测布局 |
| `buildapk/app/src/main/res/layout/dialog_model_settings_basic.xml` | 模型设置弹窗（含思考开关） |

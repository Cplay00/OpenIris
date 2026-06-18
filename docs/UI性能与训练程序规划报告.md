# OpenIris UI/性能审查与训练程序规划报告

> **审查日期**: 2026-06-17  
> **审查范围**: Android App UI 全局审查 + 训练程序增强规划  
> **审查方式**: 全量源码逐文件审查（只读，不做修改）

---

## 目录

- [A. UI 全局审查与优化](#a-ui-全局审查与优化)
  - [A1. UI 全局审查](#a1-ui-全局审查)
  - [A2. 图标风格优化](#a2-图标风格优化)
  - [A3. 操作交互优化](#a3-操作交互优化)
  - [A4. 性能优化](#a4-性能优化)
- [B. 训练程序增强规划](#b-训练程序增强规划)
  - [B1. pt→NCNN 模型转换程序](#b1-ptncnn-模型转换程序)
  - [B2. 蒸馏模式](#b2-蒸馏模式)
- [优先级总览](#优先级总览)

---

## A. UI 全局审查与优化

### A1. UI 全局审查

#### A1-1. 编码问题：XML 注释乱码 🔴 关键

**问题描述**: 多个布局文件和 Kotlin 源文件中的中文注释显示为乱码（如 `椤堕儴宸ュ叿鏍?` 应为 `顶部工具栏`，`妯″瀷璁剧疆` 应为 `模型设置`）。这是 UTF-8 BOM 编码或文件编码不一致导致的。

**涉及文件**:
- `buildapk/app/src/main/res/layout/activity_ai_model_settings.xml` — toolbar 注释乱码
- `buildapk/app/src/main/res/layout/activity_ai_provider_edit.xml` — 多处注释乱码
- `buildapk/app/src/main/res/layout/activity_realtime_detect.xml` — 注释乱码
- `buildapk/app/src/main/res/layout/activity_settings.xml` — 注释乱码
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiApiClient.kt` — 系统提示词乱码
- `buildapk/app/src/main/java/com/yolo/openiris/vlm/VlmClient.kt` — 注释乱码
- `buildapk/app/src/main/java/com/yolo/openiris/vlm/VlmScheduler.kt` — 注释乱码
- `buildapk/app/src/main/java/com/yolo/openiris/detection/SlidingWindowTracker.kt` — 注释乱码
- 几乎所有 Kotlin 文件的中文注释均受影响

**根因分析**: 早期文件可能以 GBK/GB2312 编码保存，后转换为 UTF-8 时未正确处理；或 Git 在 Windows 上的 `autocrlf` + `encoding` 配置问题。

**修复方案**:
1. 统一所有源码文件为 **UTF-8 无 BOM** 编码
2. 对已有乱码注释，根据上下文推断原文并修复
3. 在 `.gitattributes` 中添加 `*.kt text working-tree-encoding=utf-8`
4. **注意**: 不要使用 `ftfy` 库自动修复 Kotlin 字符串字面量（仅修复注释和文档字符串）

**优先级**: 🔴 P0 — 影响代码可维护性

---

#### A1-2. 深色模式完全缺失 🔴 关键

**问题描述**: 当前应用仅定义了浅色主题 (`Theme.Material3.Light.NoActionBar`)，完全没有深色模式支持。

**涉及文件**:
- `buildapk/app/src/main/res/values/themes.xml` — 仅定义 `Theme.OpenIris`（浅色）
- `buildapk/app/src/main/res/values/colors.xml` — 仅有浅色调色板
- **缺失**: `res/values-night/themes.xml` 和 `res/values-night/colors.xml`

**根因分析**: 项目初期未规划深色模式，Material 3 的动态颜色（Dynamic Color）也未启用。

**修复方案**:
1. 创建 `res/values-night/colors.xml`，定义深色调色板（建议使用 Material Theme Builder 生成）
2. 在 `themes.xml` 中添加 `Theme.OpenIris` 的 night 变体
3. 关键颜色映射：
   - `md_theme_background` → `#1B1B1F`（深色背景）
   - `md_theme_surface` → `#1B1B1F`
   - `md_theme_onSurface` → `#E3E2E6`
   - `md_theme_primary` → `#AEC6FF`（深色主色调）
4. 检测详情页的胶囊颜色（capsule_*）也需要深色变体
5. 考虑支持 Android 12+ 的 Dynamic Color（`DynamicColors.applyToActivitiesIfAvailable(this)`）

**优先级**: 🔴 P0 — 现代 Android 应用的基本要求

---

#### A1-3. 硬编码字符串 🟡 中等

**问题描述**: 部分布局和代码中使用了硬编码的中文字符串，未通过 `@string/` 资源引用。

**涉及文件**:
- `activity_ai_model_settings.xml`: `app:title="AI 模型设置"` — 应使用 `@string/`
- `AndroidManifest.xml`: `android:label="AI 模型设置"` / `android:label="编辑提供商"` — 硬编码
- `ImageDetectActivity.kt`: 多处 `Toast` 消息使用硬编码字符串
- `VideoDetectActivity.kt`: `"播放"` / `"暂停"` 硬编码
- `RealtimeDetectActivity.kt`: AI 结果展示使用硬编码标签

**修复方案**:
1. 将所有硬编码字符串提取到 `res/values/strings.xml`
2. 为后续 i18n（国际化）做准备，至少支持中英双语

**优先级**: 🟡 P2 — 不影响功能但影响可维护性

---

#### A1-4. 布局间距不一致 🟡 中等

**问题描述**: 各 Activity 的 padding/margin 值不统一。

**具体发现**:
- `main.xml`: 外层 padding `24dp`，卡片间距 `16dp`
- `activity_settings.xml`: 内容区 padding `16dp`
- `activity_ai_model_settings.xml`: 内容区 padding `16dp`
- `activity_image_detect.xml`: 工具栏和内容区间距不一致
- MaterialCardView 的 `cornerRadius` 有 `12dp` 和 `16dp` 两种

**修复方案**:
1. 定义统一的间距系统（`dimens.xml`）：`spacing_xs=4dp`, `spacing_sm=8dp`, `spacing_md=16dp`, `spacing_lg=24dp`, `spacing_xl=32dp`
2. 统一卡片圆角为 `12dp`（Material 3 推荐）
3. 统一内容区 padding 为 `16dp`

**优先级**: 🟡 P2

---

#### A1-5. 系统图标依赖 🟡 中等

**问题描述**: 多处使用 `@android:drawable/` 系统图标，这些图标在不同 Android 版本和厂商 ROM 上外观差异很大。

**涉及位置**:
- `activity_ai_model_settings.xml`: `@android:drawable/ic_menu_revert`（返回箭头）
- `main.xml`: `@android:drawable/ic_menu_preferences`（设置）、`@android:drawable/ic_media_play`（播放）
- `activity_realtime_detect.xml`: 多个 `ImageButton` 使用系统图标

**修复方案**:
1. 使用 Material Symbols 图标替代所有系统图标
2. 通过 `res/drawable/` 添加自定义 vector drawable
3. 或使用 `material-icons-extended` 依赖

**优先级**: 🟡 P2

---

### A2. 图标风格优化

#### A2-1. Provider 图标风格过于简陋 🔴 关键

**问题描述**: 6 个 AI Provider 图标（Claude、DeepSeek、Gemini、Moonshot、OpenAI、Qwen）都是简单的"圆形 + 横线"组合，视觉上完全无法区分，用户体验极差。

**涉及文件**:
- `res/drawable/ic_provider_claude.xml` — 圆形 + 两条横线（填充色 `#D97757`）
- `res/drawable/ic_provider_deepseek.xml` — 同样结构（填充色 `#4D6BFE`）
- `res/drawable/ic_provider_gemini.xml` — 同样结构（填充色 `#4285F4`）
- `res/drawable/ic_provider_moonshot.xml` — 同样结构（填充色 `#6C5CE7`）
- `res/drawable/ic_provider_openai.xml` — 同样结构（填充色 `#10A37F`）
- `res/drawable/ic_provider_qwen.xml` — 同样结构（填充色 `#FF6A00`）

**根因分析**: 这些图标明显是占位符，使用了相同的 SVG path 模板仅修改了颜色。

**修复方案**:
1. **方案 A（推荐）**: 使用各 Provider 官方 Logo 的简化版本制作 vector drawable
   - Claude: 带圆角的 "C" 字母或 Anthropic 标志
   - DeepSeek: 深度求索的鲸鱼标志简化版
   - Gemini: Google 的双星标志
   - Moonshot: 月亮/星星标志
   - OpenAI: 旋转花瓣标志
   - Qwen: 通义千问的 "Q" 标志
2. **方案 B**: 使用首字母 + 品牌色的圆形头像（类似 `AiProviderEditActivity` 中的 `textInitial` 实现）
3. 统一尺寸为 `24dp` x `24dp`，确保在列表和对话框中都清晰可见

**优先级**: 🔴 P1 — 直接影响用户识别和专业感

---

#### A2-2. 应用图标缺失多密度版本 🟡 中等

**问题描述**: `mipmap-hdpi` 中仅有 `detect.png` 一个文件，缺少：
- 其他密度变体（`mipmap-mdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`）
- Adaptive Icon 定义（`mipmap-anydpi-v26/ic_launcher.xml`）
- 前景/背景分层图标

**修复方案**:
1. 创建 Adaptive Icon：`res/drawable/ic_launcher_foreground.xml` + `res/drawable/ic_launcher_background.xml`
2. 在 `mipmap-anydpi-v26/` 中定义 `ic_launcher.xml` 和 `ic_launcher_round.xml`
3. 为各密度生成 PNG 回退版本

**优先级**: 🟡 P2

---

#### A2-3. 拖拽手柄图标样式 🟢 低

**问题描述**: `ic_drag_handle.xml` 使用灰色实心矩形（`#CCCCCC`），与 Material Design 3 的拖拽手柄风格不一致。

**修复方案**: 使用标准 Material 拖拽手柄样式（两条短横线，颜色使用 `?attr/colorOnSurfaceVariant`）。

**优先级**: 🟢 P3

---

### A3. 操作交互优化

#### A3-1. 全局 FLAG_KEEP_SCREEN_ON 设置不当 🟡 中等

**问题描述**: `MainActivity.kt` 中设置了 `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`，这意味着只要用户在主页面，屏幕就不会熄灭。

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/MainActivity.kt` 第 42 行

**根因分析**: 此标志应该只在实时检测页面（`RealtimeDetectActivity`）中设置，因为只有实时检测需要保持屏幕常亮。

**修复方案**:
1. 从 `MainActivity` 中移除 `FLAG_KEEP_SCREEN_ON`
2. 在 `RealtimeDetectActivity` 的 `onResume` 中添加，在 `onPause` 中移除
3. 考虑在 `VideoDetectActivity` 播放视频时也添加

**优先级**: 🟡 P1 — 影响电池续航

---

#### A3-2. AI API 调用无加载状态指示 🟡 中等

**问题描述**: 在 `ImageDetectActivity`、`VideoDetectActivity`、`RealtimeDetectActivity` 中，AI API 调用期间没有明显的加载指示器（Loading Spinner / Progress Bar），用户无法感知请求状态。

**涉及文件**:
- `ImageDetectActivity.kt` — AI 分析时无 loading
- `VideoDetectActivity.kt` — 有 `LinearProgressIndicator` 但仅用于视频进度
- `RealtimeDetectActivity.kt` — VLM 调用无 loading 指示

**修复方案**:
1. 在 AI 分析按钮旁添加 `CircularProgressIndicator`
2. 分析期间禁用按钮防止重复提交
3. 显示预估剩余时间或请求状态文本
4. VLM 实时模式下，在胶囊视图区域显示 "分析中..." 状态

**优先级**: 🟡 P1

---

#### A3-3. 视频播放器过于简陋 🟡 中等

**问题描述**: `VideoDetectActivity` 使用原生 `VideoView`，缺乏：
- 进度条拖拽（SeekBar）
- 音量控制
- 全屏切换
- 倍速播放
- 手势操作（左右滑动快进/快退，上下滑动调节亮度/音量）

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/VideoDetectActivity.kt`

**修复方案**:
1. **短期**: 使用 `StyledPlayerView`（ExoPlayer）替代 `VideoView`，自带完善的播放控制
2. **长期**: 添加手势控制（GestureDetector 实现滑动调节）

**优先级**: 🟡 P2

---

#### A3-4. 自定义 Toast 潜在内存泄漏 🟠 高

**问题描述**: `CustomToast.show()` 使用 `Handler.postDelayed` 延迟取消 Toast，如果 Activity 在延迟期间销毁，可能导致内存泄漏或崩溃。

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/ui/CustomToast.kt`

**修复方案**:
1. 使用 `LifecycleOwner` 绑定，在 Activity 销毁时自动取消
2. 或使用 Snackbar 替代 Toast（Snackbar 自动随 Activity 生命周期管理）
3. 使用 Material 3 的 `Snackbar` 组件，支持滑动关闭和操作按钮

**优先级**: 🟠 P1

---

#### A3-5. 检测结果展示无点击交互 🟢 低

**问题描述**: 胶囊视图（CapsuleView）仅显示统计信息，无法点击查看详情或高亮对应的检测框。

**修复方案**:
1. 为 CapsuleView 添加点击事件，点击后在 OverlayView 上高亮对应的检测框
2. 添加长按菜单：复制名称、导出单个结果等

**优先级**: 🟢 P3

---

### A4. 性能优化

#### A4-1. OkHttpClient 重复创建 🔴 关键

**问题描述**: `AiApiClient` 每次 API 调用都创建新的 `OkHttpClient` 实例，这会导致：
- 连接池无法复用
- 每次创建新的线程池和调度器
- SSL 握手重复执行
- 内存浪费

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/ai/AiApiClient.kt`

**当前代码**:
```kotlin
class AiApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
```

**问题**: 虽然 `AiApiClient` 是单例使用，但每次 `ImageDetectActivity` 等创建新实例时都会新建 `OkHttpClient`。

**修复方案**:
1. 将 `OkHttpClient` 提取为 companion object 中的单例
2. 或使用 `AiApiClient` 本身作为单例（通过 `AiModelManager` 持有）
3. 使用 `ConnectionPool` 配置连接复用

```kotlin
companion object {
    @Volatile
    private var instance: AiApiClient? = null
    fun getInstance(): AiApiClient = instance ?: synchronized(this) {
        instance ?: AiApiClient().also { instance = it }
    }
}
```

**优先级**: 🔴 P0 — 直接影响网络性能和内存

---

#### A4-2. SlidingWindowTracker 内存无限增长 🟠 高

**问题描述**: `SlidingWindowTracker` 的 `detections` 列表使用 `mutableListOf()` 存储所有检测记录，虽然有基于时间窗口的清理机制（`cleanup()`），但如果 `cleanup()` 不被及时调用，列表会持续增长。

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/detection/SlidingWindowTracker.kt`

**当前代码**:
```kotlin
private val detections = mutableListOf<TimestampedDetection>()
```

**问题**:
1. `detections` 是普通 `MutableList`，非线程安全
2. 虽然方法上有 `@Synchronized`，但 `add()` 调用不在同步块中
3. `cleanup()` 仅在查询时调用，不在添加时调用

**修复方案**:
1. 使用 `ConcurrentLinkedDeque` 替代 `mutableListOf()`
2. 在 `add()` 时同步调用 `cleanup()`
3. 添加硬性上限（如最多 10000 条记录）
4. 使用 `ScheduledExecutorService` 定期清理

**优先级**: 🟠 P1 — 长时间运行可能导致 OOM

---

#### A4-3. 图片检测无缓存策略 🟡 中等

**问题描述**: `ImageDetectActivity` 每次选择图片都从 URI 重新加载，没有内存/磁盘缓存。

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/ImageDetectActivity.kt`

**当前代码**:
```kotlin
private fun loadImageFromUri(uri: Uri) {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
    } else {
        MediaStore.Images.Media.getBitmap(contentResolver, uri)
    }
    // 直接使用，无缓存
}
```

**修复方案**:
1. 使用 Coil 或 Glide 图片加载库，自带内存/磁盘缓存
2. 大图使用 `BitmapFactory.Options.inSampleSize` 降采样
3. 使用 `BitmapFactory.Options.inBitmap` 复用 Bitmap 内存

**优先级**: 🟡 P2

---

#### A4-4. VLM 请求 Bitmap 复制开销 🟡 中等

**问题描述**: 每次 VLM API 调用都将 Bitmap 编码为 Base64，涉及：
1. Bitmap → JPEG 压缩（CPU 密集）
2. 字节数组 → Base64 编码（内存翻倍）
3. JSON 序列化（内存分配）

**涉及文件**:
- `buildapk/app/src/main/java/com/yolo/openiris/vlm/VlmClient.kt`
- `buildapk/app/src/main/java/com/yolo/openiris/vlm/VlmRequestBuilder.kt`

**修复方案**:
1. 预压缩 Bitmap 到较低分辨率（如 512x512）再编码
2. 使用 `ByteBuffer` 直接操作避免中间数组分配
3. 考虑使用 multipart upload 替代 base64 内嵌
4. 添加 Bitmap 尺寸检查，超过阈值自动缩放

**优先级**: 🟡 P2

---

#### A4-5. 检测结果使用 FlexboxLayout 而非 RecyclerView 🟡 中等

**问题描述**: `RealtimeDetectActivity` 和 `ImageDetectActivity` 使用 `FlexboxLayout` 动态添加 `CapsuleView`，每次检测结果更新都 `removeAllViews()` 再重新添加，没有视图复用。

**涉及文件**:
- `RealtimeDetectActivity.kt` — `updateCapsuleUI()` 方法
- `ImageDetectActivity.kt` — 类似的更新逻辑

**修复方案**:
1. 使用 `RecyclerView` + `FlexboxLayoutManager`（来自 `flexbox-layout` 库）
2. 使用 `DiffUtil` 计算差异，仅更新变化的项目
3. 添加 `ItemAnimator` 实现平滑过渡动画

**优先级**: 🟡 P2

---

#### A4-6. 主线程潜在阻塞 🟠 高

**问题描述**: 部分操作可能在主线程执行导致 ANR。

**具体位置**:
1. `SettingsActivity.kt` — NCNN 模型文件验证（`validateNcnnParamFile`）在主线程读取文件
2. `ImageDetectActivity.kt` — `loadImageFromUri` 在主线程解码大图
3. `RealtimeDetectActivity.kt` — `captureFrame()` 中的 Bitmap 操作在 JNI 回调线程

**修复方案**:
1. 文件 I/O 操作移到 `lifecycleScope.launch(Dispatchers.IO)`
2. 图片解码使用 `withContext(Dispatchers.IO)`
3. Bitmap 操作使用 `withContext(Dispatchers.Default)`

**优先级**: 🟠 P1

---

#### A4-7. 过度绘制风险 🟢 低

**问题描述**: 实时检测页面存在多层叠加：
- SurfaceView（相机预览）
- OverlayView（检测框覆盖层）
- FlexboxLayout（胶囊统计）
- MaterialCardView（卡片背景）

**修复方案**:
1. 使用 `GPU Overdraw` 调试工具检查
2. 移除不可见区域的背景绘制
3. 胶囊视图使用 `clipChildren=false` 减少裁剪开销

**优先级**: 🟢 P3

---

#### A4-8. VLM Scheduler 线程安全问题 🟡 中等

**问题描述**: `VlmScheduler` 使用 `AtomicBoolean` 和 `AtomicLong` 管理状态，但 `triggerIfNeeded()` 中的 check-then-act 模式存在竞态条件。

**涉及文件**: `buildapk/app/src/main/java/com/yolo/openiris/vlm/VlmScheduler.kt`

**当前代码**:
```kotlin
fun triggerIfNeeded(bitmap: Bitmap) {
    if (!isRunning.get()) return
    if (isPending.get()) { skipCount.incrementAndGet(); return }
    val now = SystemClock.elapsedRealtime()
    if (now - lastTriggerTime.get() < intervalMs) return
    // ... 执行请求
}
```

**问题**: 多线程同时调用时，可能绕过 `isPending` 检查导致并发请求。

**修复方案**:
1. 使用 `compareAndSet` 替代 `get()` + 后续操作
2. 或使用 `Mutex` / `synchronized` 保护整个触发逻辑

**优先级**: 🟡 P2

---

## B. 训练程序增强规划

### B1. pt→NCNN 模型转换程序

#### 现状分析

当前项目有两个转换脚本：

| 脚本 | 路径 | 功能 | 不足 |
|------|------|------|------|
| `export_yolov11_ncnn.py` | `tools/export_yolov11_ncnn.py` | 基础 .pt→ONNX→NCNN 转换 | 无验证、无批量、无元数据 |
| `export_pipeline.py` | `training/tools/export_pipeline.py` | 完整流水线（含元数据、zip 打包、Android assets 复制） | 无推理验证、无批量支持 |

**`export_pipeline.py` 已有功能**:
- ✅ .pt → ONNX 导出（使用 ultralytics）
- ✅ ONNX 简化（onnxsim）
- ✅ ONNX → NCNN 转换（onnx2ncnn）
- ✅ ncnnoptimize 优化
- ✅ 生成 labels.txt
- ✅ 生成 model_meta.json（模型元数据）
- ✅ 打包为 zip（param + bin + labels + meta）
- ✅ 复制到 Android assets 目录

**`export_pipeline.py` 缺失功能**:
- ❌ 推理验证（导出后自动测试推理结果）
- ❌ 批量转换（一次处理多个模型）
- ❌ 精度对比（原始 .pt vs NCNN 推理结果对比）
- ❌ 模型大小分析和优化建议

#### 规划：增强版转换工具

**目标**: 创建统一的 `tools/model_converter.py`，整合并增强现有功能。

**功能规划**:

```python
# 用法示例
# 单模型转换 + 验证
python tools/model_converter.py --weights best.pt --validate --compare

# 批量转换
python tools/model_converter.py --batch training/run/*/weights/best.pt --validate

# 仅验证已有模型
python tools/model_converter.py --validate-ncnn models/my_model --test-image test.jpg
```

**模块设计**:

1. **`ModelConverter` 类** — 核心转换逻辑
   - `convert_pt_to_onnx()`: .pt → ONNX（复用 ultralytics）
   - `simplify_onnx()`: ONNX 简化（onnxsim）
   - `convert_onnx_to_ncnn()`: ONNX → NCNN（调用 onnx2ncnn）
   - `optimize_ncnn()`: NCNN 优化（ncnnoptimize）
   - `generate_labels()`: 从模型提取类别标签
   - `generate_metadata()`: 生成模型元数据 JSON
   - `package_zip()`: 打包为可导入 zip

2. **`ModelValidator` 类** — 推理验证
   - `validate_ncnn_inference()`: 使用 ncnn Python 绑定运行推理
   - `compare_with_pt()`: 对比 .pt 和 NCNN 的推理结果
   - `benchmark_speed()`: 测量推理耗时
   - `check_model_size()`: 检查模型文件大小

3. **`BatchProcessor` 类** — 批量处理
   - `process_glob()`: 支持 glob 模式匹配多个权重文件
   - `generate_report()`: 生成批量转换报告（Markdown 表格）

4. **输出结构**:
   ```
   models/
   ├── my_model/
   │   ├── my_model.param      # NCNN 模型结构
   │   ├── my_model.bin        # NCNN 模型权重
   │   ├── labels.txt          # 类别标签
   │   ├── model_meta.json     # 元数据
   │   └── my_model.zip        # 可导入包
   └── conversion_report.md    # 转换报告
   ```

5. **验证流程**:
   ```
   输入 .pt → 导出 ONNX → 简化 → 转 NCNN → 优化 → 打包
                                           ↓
                                    Python ncnn 推理测试
                                           ↓
                                    与 .pt 结果对比（mAP 差异 < 1%）
                                           ↓
                                    生成验证报告
   ```

**优先级**: 🟠 P1 — 训练工作流的核心环节

**预计工作量**: 2-3 天

---

### B2. 蒸馏模式

#### 现状分析

**当前训练脚本**:

| 脚本 | 功能 | 蒸馏支持 |
|------|------|---------|
| `train_adapted.py` | 基础训练（适配 RTX 4060） | ❌ 无 |
| `train_highprec.py` | 多阶段高精度训练 | ⚠️ 文档提到 `--teacher` 参数但代码未实现 |

**`train_highprec.py` 文档声称支持**:
```python
# 知识蒸馏训练
python training/scripts/train_highprec.py \
    --data configs/dataset_custom.yaml \
    --teacher yolov11m.pt \
    --epochs 200
```

**实际代码**: `argparse` 中有 `--teacher` 参数定义，但 `build_highprec_args()` 和 `train_stage*()` 函数中**完全没有蒸馏逻辑**。这是一个文档与代码不一致的问题。

#### 蒸馏方案规划

**目标**: 在 `yolo11n`（nano）模型上通过知识蒸馏提升精度，使其接近 `yolo11s`（small）的性能，同时保持 `yolo11n` 的推理速度。

**技术路线**:

##### 方案 A: Ultralytics 内置蒸馏（推荐，最简实现）

Ultralytics 8.3.74+ 已内置知识蒸馏支持。在 `train_highprec.py` 中添加：

```python
# 阶段 2: 蒸馏训练
def train_stage_distill(args, student_weights, teacher_weights):
    from ultralytics import YOLO
    
    student = YOLO(student_weights)
    teacher = YOLO(teacher_weights)
    
    # Ultralytics 内置蒸馏
    results = student.train(
        data=args['data'],
        epochs=args['epochs'],
        batch=args['batch'],
        imgsz=args['imgsz'],
        # 蒸馏参数
        teacher=teacher_weights,     # 教师模型路径
        distillation_loss='cwd',     # 蒸馏损失: ClassificationWiseDivergence
        # 或 'soft' (soft label distillation)
    )
    return results
```

**教师模型选择**:

| 教师模型 | 参数量 | mAP50-95 (COCO) | 推荐场景 |
|---------|--------|-----------------|---------|
| `yolo11s.pt` | 9.4M | 47.0 | 🟢 **推荐** — 精度/速度平衡 |
| `yolo11m.pt` | 20.1M | 51.5 | 高精度需求 |
| `yolo11l.pt` | 25.3M | 53.4 | 追求极致精度 |

**建议**: 对于 OpenIris 的移动部署场景，使用 `yolo11s.pt` 作为教师模型最为合适。太大（如 `yolo11l`）的教师模型蒸馏到 `yolo11n` 时，容量差异过大可能导致蒸馏效果不佳。

##### 方案 B: 自定义蒸馏损失（高级）

如果 Ultralytics 内置蒸馏不满足需求，可自定义蒸馏训练循环：

```python
import torch
import torch.nn as nn
import torch.nn.functional as F

class DistillationLoss(nn.Module):
    """YOLO 知识蒸馏损失"""
    def __init__(self, temperature=20.0, alpha=0.7):
        super().__init__()
        self.temperature = temperature
        self.alpha = alpha  # 蒸馏损失权重
        self.ce_loss = nn.CrossEntropyLoss()
        self.kl_loss = nn.KLDivLoss(reduction='batchmean')
    
    def forward(self, student_logits, teacher_logits, targets):
        # 硬标签损失（原始检测损失）
        hard_loss = self.ce_loss(student_logits, targets)
        
        # 软标签损失（KL 散度）
        soft_student = F.log_softmax(student_logits / self.temperature, dim=-1)
        soft_teacher = F.softmax(teacher_logits / self.temperature, dim=-1)
        soft_loss = self.kl_loss(soft_student, soft_teacher) * (self.temperature ** 2)
        
        # 加权组合
        return self.alpha * soft_loss + (1 - self.alpha) * hard_loss
```

**蒸馏策略参数规划**:

```yaml
# configs/distill_config.yaml
distillation:
  teacher_model: yolo11s.pt       # 教师模型
  temperature: 20.0               # 温度参数（越高越平滑）
  alpha: 0.7                      # 蒸馏损失权重（0.7 蒸馏 + 0.3 原始损失）
  
  # 分层蒸馏
  feature_distillation: true      # 是否进行特征层蒸馏
  feature_layers: [3, 6, 9]       # 蒸馏的特征层索引
  feature_loss_type: "mse"        # 特征损失类型: mse / cosine
  
  # 渐进式蒸馏
  progressive: true               # 是否渐进式增加蒸馏权重
  warmup_epochs: 10               # 蒸馏预热轮次
  alpha_start: 0.3                # 初始蒸馏权重
  alpha_end: 0.7                  # 最终蒸馏权重
```

##### 方案 C: 参数裁剪 + 蒸馏联合优化

在蒸馏的同时进行结构化剪枝，进一步减小模型体积：

```python
def prune_and_distill(student, teacher, pruning_ratio=0.3):
    """
    结构化剪枝 + 知识蒸馏联合训练
    
    1. 使用 L1 范数进行通道剪枝
    2. 蒸馏恢复剪枝后的精度损失
    """
    # Step 1: 评估通道重要性
    importance = compute_channel_importance(student)
    
    # Step 2: 剪枝不重要的通道
    pruned_student = prune_channels(student, importance, pruning_ratio)
    
    # Step 3: 蒸馏恢复精度
    distill(pruned_student, teacher, epochs=50)
    
    return pruned_student
```

**减小模型体积的策略**:

| 策略 | 效果 | 复杂度 | 推荐 |
|------|------|--------|------|
| 知识蒸馏（yolo11n + yolo11s 教师） | +2-4% mAP | 低 | ✅ 优先实施 |
| 结构化通道剪枝 20% | -20% 参数量，-1% mAP | 中 | ✅ 推荐 |
| GhostConv 替换标准 Conv | -30% 参数量 | 中 | ⚠️ 需要修改模型结构 |
| INT8 量化（NCNN 端） | -75% 模型体积 | 低 | ✅ 部署时必备 |
| 模型蒸馏 + 剪枝 + 量化联合 | -70% 体积，+1% mAP | 高 | 🔄 进阶方案 |

#### 实现规划

**Phase 1: 基础蒸馏**（1-2 天）
1. 修复 `train_highprec.py` 中 `--teacher` 参数的实现
2. 添加 `train_stage_distill()` 函数
3. 支持 Ultralytics 内置蒸馏
4. 添加蒸馏配置文件 `configs/distill_config.yaml`

**Phase 2: 自定义蒸馏损失**（2-3 天）
1. 实现 `DistillationLoss` 类
2. 添加特征层蒸馏支持
3. 实现渐进式蒸馏权重调度
4. 添加蒸馏日志和可视化

**Phase 3: 剪枝 + 量化**（3-5 天）
1. 实现结构化通道剪枝
2. 集成 NCNN INT8 量化
3. 端到端的 剪枝→蒸馏→量化 流水线

**优先级**: 🟡 P2 — 对精度有提升但非紧急

**预计总工作量**: 6-10 天（分阶段实施）

---

## 优先级总览

### P0 — 立即修复

| 编号 | 问题 | 类别 | 预计工作量 |
|------|------|------|-----------|
| A1-2 | 深色模式缺失 | UI | 1-2 天 |
| A4-1 | OkHttpClient 重复创建 | 性能 | 0.5 天 |

### P1 — 高优先级

| 编号 | 问题 | 类别 | 预计工作量 |
|------|------|------|-----------|
| A1-1 | XML/代码注释乱码 | UI | 1 天 |
| A2-1 | Provider 图标风格占位 | UI | 1-2 天 |
| A3-1 | FLAG_KEEP_SCREEN_ON 位置不当 | 交互 | 0.5 天 |
| A3-2 | AI 调用无 loading 指示 | 交互 | 1 天 |
| A3-4 | CustomToast 内存泄漏风险 | 交互 | 0.5 天 |
| A4-2 | SlidingWindowTracker 内存增长 | 性能 | 0.5 天 |
| A4-6 | 主线程潜在阻塞 | 性能 | 1 天 |
| B1 | pt→NCNN 增强转换工具 | 训练 | 2-3 天 |

### P2 — 中优先级

| 编号 | 问题 | 类别 | 预计工作量 |
|------|------|------|-----------|
| A1-3 | 硬编码字符串 | UI | 1 天 |
| A1-4 | 布局间距不一致 | UI | 0.5 天 |
| A1-5 | 系统图标依赖 | UI | 1 天 |
| A2-2 | 应用图标缺多密度 | UI | 0.5 天 |
| A3-3 | 视频播放器简陋 | 交互 | 2-3 天 |
| A4-3 | 图片无缓存策略 | 性能 | 1 天 |
| A4-4 | VLM Bitmap 复制开销 | 性能 | 1 天 |
| A4-5 | FlexboxLayout 替换为 RecyclerView | 性能 | 1 天 |
| A4-8 | VLM Scheduler 竞态条件 | 性能 | 0.5 天 |
| B2 | 蒸馏模式实现 | 训练 | 6-10 天 |

### P3 — 低优先级

| 编号 | 问题 | 类别 | 预计工作量 |
|------|------|------|-----------|
| A2-3 | 拖拽手柄图标样式 | UI | 0.5 小时 |
| A3-5 | 检测结果点击交互 | 交互 | 1 天 |
| A4-7 | 过度绘制风险 | 性能 | 0.5 天 |

---

## 附录：项目文件结构参考

```
buildapk/app/src/main/
├── java/com/yolo/openiris/
│   ├── MainActivity.kt              # 主页面
│   ├── RealtimeDetectActivity.kt    # 实时检测（摄像头）
│   ├── ImageDetectActivity.kt       # 图片检测
│   ├── VideoDetectActivity.kt       # 视频检测
│   ├── SettingsActivity.kt          # 设置页面
│   ├── AiModelSettingsActivity.kt   # AI 模型设置
│   ├── AiProviderEditActivity.kt    # AI 提供商编辑
│   ├── OpenIrisApplication.kt       # Application 入口
│   ├── ai/                          # AI API 客户端
│   │   ├── AiApiClient.kt
│   │   ├── AiModel.kt
│   │   ├── AiModelManager.kt
│   │   └── AiProvider.kt
│   ├── config/                      # 配置管理
│   │   ├── AppConfig.kt
│   │   ├── ConfigManager.kt
│   │   └── AppConfigValidator.kt
│   ├── detection/                   # 检测结果模型
│   │   ├── AnalysisResult.kt
│   │   ├── DetectionResult.kt
│   │   ├── SlidingWindowTracker.kt
│   │   ├── JsonExporter.kt
│   │   └── ImageExporter.kt
│   ├── dialog/                      # 对话框
│   ├── label/                       # 标签管理
│   ├── llm/                         # LLM 客户端
│   ├── ui/                          # 自定义 UI 组件
│   │   ├── CapsuleView.kt
│   │   ├── OverlayView.kt
│   │   └── CustomToast.kt
│   ├── utils/                       # 工具类
│   └── vlm/                         # VLM 视觉语言模型
│       ├── VlmClient.kt
│       ├── VlmScheduler.kt
│       └── VlmRequestBuilder.kt
├── jni/                             # C++ 原生代码
│   ├── yolov11.cpp / .h             # YOLOv11 NCNN 推理
│   ├── yolov11ncnn.cpp              # JNI 桥接层
│   ├── ndkcamera.cpp / .h           # NDK 相机
│   └── CMakeLists.txt
├── res/
│   ├── layout/                      # 22 个布局文件
│   ├── drawable/                    # 9 个矢量图标
│   ├── mipmap-hdpi/                 # 仅 1 个应用图标
│   └── values/                      # 颜色/字符串/主题
└── assets/
    ├── labels/                      # 标签文件
    └── models/                      # NCNN 模型

training/
├── scripts/
│   ├── train_adapted.py             # 基础训练脚本
│   ├── train_highprec.py            # 高精度训练（含蒸馏占位）
│   └── dataset_validator.py         # 数据集校验
├── tools/
│   ├── export_pipeline.py           # 模型导出流水线
│   └── visualize_results.py         # 训练结果可视化
├── configs/
│   ├── dataset_custom.yaml          # 数据集配置
│   └── train_highprec.yaml          # 高精度训练超参
├── docs/
│   └── ADAPTATION_GUIDE.md          # 适配指南
└── gui/                             # 训练 GUI（tkinter）

tools/
└── export_yolov11_ncnn.py           # 基础转换脚本
```

---

> 本报告基于 2026-06-17 代码快照生成，覆盖 `buildapk/app/src/main/` 下全部 Kotlin/XML 源码、`training/` 下全部 Python 脚本、以及 `tools/` 下的转换脚本。

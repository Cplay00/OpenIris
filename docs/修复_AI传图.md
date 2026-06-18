# 修复报告：AI 识别不传图像问题

## 问题描述

`callAiModel()` 方法不传图像给 AI 模型，AI 只能基于 YOLO 检测的文本结果推测，无法"看到"摄像头画面。

## 涉及文件

- `buildapk/app/src/main/java/com/yolo/openiris/RealtimeDetectActivity.kt` — `callAiModel()` 方法（行 510-570）
- `buildapk/app/src/main/java/com/yolo/openiris/ai/AiModelManager.kt` — `callWithFallbackAndImage()` 和 `bitmapToBase64()` 方法

## 修复内容

### `RealtimeDetectActivity.kt` — `callAiModel()` 方法

**修改前**（纯文本调用）：
```kotlin
private suspend fun callAiModel() {
    try {
        val yoloSummary = yoloTracker.getSortedSummary()
        val contextPrompt = ...
        val result = aiModelManager.callWithFallback(prompt = contextPrompt, onError = ...)
        ...
    }
}
```

**修改后**（带图像调用 + 降级）：
```kotlin
private suspend fun callAiModel() {
    try {
        // 捕获当前摄像头帧并编码为 base64，传给 AI 模型
        var frameBitmap: Bitmap? = null
        var imageBase64: String? = null
        try {
            val config = configManager.loadConfig()
            frameBitmap = Bitmap.createBitmap(
                config.cameraResolutionWidth,
                config.cameraResolutionHeight,
                Bitmap.Config.ARGB_8888
            )
            if (yolov11Ncnn.captureFrame(frameBitmap)) {
                imageBase64 = aiModelManager.bitmapToBase64(frameBitmap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture frame for AI", e)
        } finally {
            frameBitmap?.recycle()
        }

        val yoloSummary = yoloTracker.getSortedSummary()
        val contextPrompt = if (yoloSummary.isNotEmpty()) {
            val yoloText = yoloSummary.joinToString("、") { "${it.name}(${it.count}个)" }
            "当前画面 YOLO 检测到：$yoloText。请基于以上检测结果，补充识别画面中的其他物体，以JSON格式返回结果。"
        } else {
            "请识别图片中的物体，以JSON格式返回结果。"
        }

        val result = if (imageBase64 != null) {
            aiModelManager.callWithFallbackAndImage(
                prompt = contextPrompt,
                imageBase64 = imageBase64,
                onError = { error -> ... }
            )
        } else {
            aiModelManager.callWithFallback(
                prompt = contextPrompt,
                onError = { error -> ... }
            )
        }
        ...
    }
}
```

### 关键改动点

1. **帧捕获**：调用 `yolov11Ncnn.captureFrame(frameBitmap)` 获取当前摄像头帧
2. **Base64 编码**：通过 `aiModelManager.bitmapToBase64(frameBitmap)` 将 Bitmap 转为 base64 字符串
3. **视觉模型优先**：有图像时调用 `callWithFallbackAndImage()`，该方法会过滤出支持视觉（`hasVision=true`）的模型
4. **安全降级**：帧捕获失败时（`imageBase64 == null`），降级为纯文本 `callWithFallback()`
5. **资源释放**：`finally` 块中 `frameBitmap?.recycle()` 防止内存泄漏

### 配套方法（`AiModelManager.kt`）

- `bitmapToBase64(bitmap, quality=80)` — 行 297：Bitmap → JPEG → Base64 编码
- `callWithFallbackAndImage(prompt, imageBase64, systemPrompt?, onError?)` — 行 225：带图像的多模型 fallback 调用，自动过滤 `hasVision=true` 的模型

## 验证

```
$ .\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease
BUILD SUCCESSFUL in 36s
55 actionable tasks: 13 executed, 42 up-to-date
```

编译通过，无 Kotlin 编译错误。lint 分析因 Kotlin FIR 工具链 bug 跳过（非代码问题）。

## 附注：`saveBitmap()` 返回值修复

同一文件中 `saveBitmap(): Boolean` 函数（行 348）存在返回值缺失问题，已一并修复：

| 行号 | 修改前 | 修改后 |
|------|--------|--------|
| 368 | *(无)* | `return true`（MediaStore 保存成功） |
| 371 | *(无)* | `return false`（MediaStore 创建失败） |
| 377 | `return` | `return false`（缺少存储权限） |
| 386 | *(无)* | `return true`（传统文件保存成功） |

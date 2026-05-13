# OpenIris v0.3-Alpha Bug 修复计划

## 创建时间：2026-05-14

---

## Bug 列表与修复方案

### Bug 1 & 3: 导出路径未应用设置
**优先级：高 | 复杂度：低**

**问题：**
- `ImageDetectActivity.createImageFile()` 硬编码使用 `getExternalFilesDir(Environment.DIRECTORY_PICTURES)`
- `RealtimeDetectActivity.saveBitmap()` 同样硬编码路径
- 没有读取 `ConfigManager.getImageExportPath()` 的配置

**修复方案：**
```kotlin
// ImageDetectActivity.kt - 修改 createImageFile()
private fun createImageFile(): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val configPath = configManager.getImageExportPath()
    val storageDir = if (configPath.isNotBlank()) {
        File(getExternalFilesDir(null), configPath)
    } else {
        getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    }
    storageDir?.mkdirs()
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}

// RealtimeDetectActivity.kt - 修改 saveBitmap()
private fun saveBitmap(bitmap: Bitmap) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "OpenIris_${timeStamp}.jpg"
    val configPath = configManager.getImageExportPath()
    val storageDir = if (configPath.isNotBlank()) {
        File(getExternalFilesDir(null), configPath)
    } else {
        getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    }
    storageDir?.mkdirs()
    val file = File(storageDir, fileName)
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
}
```

**涉及文件：**
- `ImageDetectActivity.kt`
- `RealtimeDetectActivity.kt`

---

### Bug 4: 实时检测右侧UI按钮大小不统一
**优先级：中 | 复杂度：低**

**问题：**
- 三个卡片使用 `wrap_content`，导致大小不一致

**修复方案：**
```xml
<!-- activity_realtime_detect.xml - 为三个卡片设置固定宽度 -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardSwitchCamera"
    android:layout_width="64dp"  <!-- 固定宽度 -->
    android:layout_height="wrap_content"
    ...>
```

**涉及文件：**
- `activity_realtime_detect.xml`

---

### Bug 5: 摄像头画面方向颠倒 + 无法自适应方向
**优先级：低 | 复杂度：高**

**问题：**
- C++ 层旋转逻辑可能有问题
- 可能锁定了竖屏方向

**修复方案：**
1. 检查 `AndroidManifest.xml` 中的 `screenOrientation` 配置
2. 在 `RealtimeDetectActivity` 中添加方向变化处理
3. 修复 C++ 层旋转逻辑

**涉及文件：**
- `AndroidManifest.xml`
- `RealtimeDetectActivity.kt`
- `ndkcamera.cpp`

---

### Bug 6: 分辨率预设问题
**优先级：中 | 复杂度：中**

**问题：**
- 预设为横屏比例
- 自定义预设无法删除
- 没有原生分辨率选项

**修复方案：**
```kotlin
// AppConfig.kt - 修改预设为动态计算
companion object {
    // 预设档位（竖屏）
    const val RESOLUTION_480P = 0
    const val RESOLUTION_720P = 1
    const val RESOLUTION_1080P = 2
    const val RESOLUTION_NATIVE = 3
    
    fun getPresetResolutions(context: android.content.Context): List<Pair<Int, Int>> {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val aspectRatio = screenHeight.toFloat() / screenWidth.toFloat()
        
        return listOf(
            Pair(480, (480 * aspectRatio).toInt()),      // 480P
            Pair(720, (720 * aspectRatio).toInt()),      // 720P
            Pair(1080, (1080 * aspectRatio).toInt()),    // 1080P
            Pair(screenWidth, screenHeight)               // 原生分辨率
        )
    }
}
```

2. 添加自定义分辨率删除功能

**涉及文件：**
- `AppConfig.kt`
- `SettingsActivity.kt`
- `activity_settings.xml`

---

### Bug 7: 抓拍图应带有检测框
**优先级：高 | 复杂度：低**

**问题：**
- `captureAndSave()` 保存的是原始帧

**修复方案：**
```kotlin
// RealtimeDetectActivity.kt - 修改 captureAndSave()
private fun captureAndSave() {
    var bitmap: Bitmap? = null
    try {
        val config = configManager.loadConfig()
        bitmap = Bitmap.createBitmap(
            config.cameraResolutionWidth,
            config.cameraResolutionHeight,
            Bitmap.Config.ARGB_8888
        )
        
        if (yolov11Ncnn.captureFrame(bitmap)) {
            // 在帧上绘制检测框
            val detections = getCurrentDetections()
            val annotatedBitmap = ImageUtils.drawDetections(bitmap, detections)
            
            saveBitmap(annotatedBitmap)
            
            if (config.showCapturePreview) {
                showCapturePreview(annotatedBitmap)
            }
            
            Toast.makeText(this, "截图已保存", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Capture failed", e)
    } finally {
        bitmap?.recycle()
    }
}

private fun getCurrentDetections(): List<DetectedObject> {
    // 从 yoloTracker 获取当前检测结果
    val summary = yoloTracker.getSortedSummary()
    // 转换为 DetectedObject 列表
    return summary.map { stats ->
        DetectedObject(
            label = stats.name,
            labelIndex = 0,
            confidence = stats.avgConfidence,
            bbox = BoundingBox(0f, 0f, 0f, 0f) // 需要从实际检测结果获取
        )
    }
}
```

**涉及文件：**
- `RealtimeDetectActivity.kt`

---

### Bug 8: 实时抓取展示UI更改
**优先级：中 | 复杂度：高**

**问题：**
- 当前使用 BottomSheetDialog
- 需要改为切换底部窗口

**修复方案：**
1. 将底部结果面板改为 ViewPager2
2. 添加"实时抓取"页面
3. 使用非线性动画切换
4. 2秒后自动切换回

**涉及文件：**
- `activity_realtime_detect.xml`
- `RealtimeDetectActivity.kt`
- 新增布局文件

---

### Bug 9: AI提供商列表消失
**优先级：高 | 复杂度：中**

**问题：**
- 可能是数据持久化问题
- 或 Gson 序列化新字段时出错

**修复方案：**
1. 检查 `AiModelConfigStore.loadProviders()` 的异常处理
2. 添加日志记录
3. 确保新字段有默认值
4. 添加数据迁移逻辑

**涉及文件：**
- `AiModelConfigStore.kt`
- `AiProvider.kt`
- `AiModel.kt`

---

### Bug 10: API路径切换问题
**优先级：高 | 复杂度：低**

**问题：**
- 从 Anthropic 切回 OpenAI 时路径不更新

**修复方案：**
```kotlin
// AiProviderEditActivity.kt - 修改 updateApiFormatUI()
private fun updateApiFormatUI() {
    when (currentApiFormat) {
        ApiFormat.OPENAI_COMPATIBLE -> {
            switchResponseApi.visibility = View.VISIBLE
            editBaseUrl.hint = "API Base Url"
            if (editBaseUrl.text.toString().isEmpty() || editBaseUrl.text.toString().contains("anthropic")) {
                editBaseUrl.setText("https://api.openai.com/v1")
            }
            // 只有启用 Response API 时才使用 /responses
            if (switchResponseApi.isChecked) {
                editApiPath.setText("/responses")
            } else {
                editApiPath.setText("/chat/completions")
            }
        }
        ApiFormat.ANTHROPIC -> {
            switchResponseApi.visibility = View.GONE
            editBaseUrl.hint = "API Base Url"
            if (editBaseUrl.text.toString().isEmpty() || editBaseUrl.text.toString().contains("openai")) {
                editBaseUrl.setText("https://api.anthropic.com")
            }
            editApiPath.setText("/v1/messages")
        }
    }
}
```

**涉及文件：**
- `AiProviderEditActivity.kt`

---

### Bug 11: 自定义提示词文本框无法滚动
**优先级：中 | 复杂度：低**

**问题：**
- EditText 嵌套在 ScrollView 中，触摸事件被拦截

**修复方案：**
```xml
<!-- activity_ai_model_settings.xml - 修改 EditText -->
<com.google.android.material.textfield.TextInputEditText
    android:id="@+id/editVisualPrompt"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:inputType="textMultiLine"
    android:minLines="3"
    android:maxLines="10"
    android:gravity="top"
    android:nestedScrollingEnabled="true" />
```

或在代码中：
```kotlin
editVisualPrompt.setOnTouchListener { v, event ->
    v.parent.requestDisallowInterceptTouchEvent(true)
    false
}
```

**涉及文件：**
- `activity_ai_model_settings.xml`
- `AiModelSettingsActivity.kt`

---

## 执行顺序

### 第一批（高优先级、低复杂度）
1. Bug 1 & 3: 修复导出路径
2. Bug 7: 抓拍图带检测框
3. Bug 10: API路径切换
4. Bug 11: 提示词滚动

### 第二批（中优先级）
5. Bug 4: 按钮大小统一
6. Bug 6: 分辨率预设
7. Bug 9: 提供商列表持久化

### 第三批（低优先级、高复杂度）
8. Bug 2: 标题栏功能
9. Bug 5: 摄像头方向
10. Bug 8: 抓取展示UI

---

## 文件修改清单

| 文件 | 修改内容 |
|------|----------|
| `ImageDetectActivity.kt` | 修复导出路径 |
| `RealtimeDetectActivity.kt` | 修复导出路径、抓拍图带检测框、抓取展示UI |
| `AiProviderEditActivity.kt` | 修复API路径切换 |
| `AiModelSettingsActivity.kt` | 提示词滚动 |
| `AppConfig.kt` | 分辨率预设动态计算 |
| `SettingsActivity.kt` | 自定义分辨率删除 |
| `AiModelConfigStore.kt` | 数据持久化修复 |
| `activity_realtime_detect.xml` | 按钮大小统一 |
| `activity_ai_model_settings.xml` | 提示词滚动 |
| `activity_settings.xml` | 分辨率删除UI |
| `AndroidManifest.xml` | 屏幕方向配置 |
| `ndkcamera.cpp` | 摄像头旋转逻辑 |

# OpenIris 数据结构设计

## 1. 配置数据类

### AppConfig

应用全局配置，存储于 EncryptedSharedPreferences。

```kotlin
data class AppConfig(
    val apiBaseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val vlmModel: String = "qwen3.5-35b-a3b",
    val llmModel: String = "deepseek-v4-flash",
    val vlmIntervalSeconds: Int = 5,
    val useGpu: Boolean = false,            // 默认关闭 GPU（兼容不支持 Vulkan 的设备）
    val selectedModel: String = "yolov11n",
    val enableLlmFusion: Boolean = true,
    val enableJsonExport: Boolean = true,
    val enableImageExport: Boolean = true,
    val enableVideoExport: Boolean = false,
    val cameraResolutionWidth: Int = 480,   // 摄像头分辨率宽
    val cameraResolutionHeight: Int = 640,  // 摄像头分辨率高
    val customResolutions: List<String> = emptyList(),  // 自定义分辨率列表
    val showCapturePreview: Boolean = false // 实时抓取后展示原图
)
```

## 2. AI 多提供商数据类 (ai/)

### ApiFormat

```kotlin
enum class ApiFormat {
    OPENAI_COMPATIBLE,    // OpenAI 兼容 API（默认）
    ANTHROPIC             // Anthropic 兼容 API
}
```

### AiProvider

```kotlin
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<AiModel> = emptyList(),
    val isEnabled: Boolean = true,
    val apiFormat: ApiFormat = ApiFormat.OPENAI_COMPATIBLE,
    val apiPath: String = "/chat/completions",
    val useResponseApi: Boolean = false,
    val enableStream: Boolean = true
)
```

### AiModel

```kotlin
data class AiModel(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val hasVision: Boolean = false,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val priority: Int = 0,
    val enableReasoning: Boolean = false,
    val assignedTasks: List<String> = emptyList(),   // e.g. "visual_recognition", "detection_summary"
    val customHeaders: Map<String, String> = emptyMap(),
    val customBody: Map<String, String> = emptyMap()
)
```

### AiResult

```kotlin
data class AiResult(
    val modelId: String,
    val modelName: String,
    val success: Boolean,
    val content: String? = null,
    val structuredOutput: StructuredOutput? = null,
    val error: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0
)

data class StructuredOutput(
    val objects: List<RecognizedObject>,
    val summary: String
)

data class RecognizedObject(
    val name: String,
    val nameCn: String? = null,
    val count: Int,
    val confidence: Float
)
```

### TestResult

```kotlin
data class TestResult(
    val success: Boolean,
    val durationMs: Long,
    val message: String
)
```

## 3. 检测结果数据类 (detection/)

### BoundingBox / DetectedObject / DetectionResult

```kotlin
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class DetectedObject(
    val label: String,              // 类别名称
    val labelIndex: Int,            // 类别索引
    val confidence: Float,          // 置信度 0.0-1.0
    val bbox: BoundingBox
)

data class DetectionResult(
    val source: String = "yolo",
    val timestampMs: Long = System.currentTimeMillis(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val objects: List<DetectedObject> = emptyList()
) {
    fun countByLabel(): Map<String, Int>
    fun uniqueLabels(): List<String>
    fun topConfidentObjects(limit: Int = 10): List<DetectedObject>
    fun unifiedObjects(): List<UnifiedObjectResult>
    fun maxConfidenceByLabel(): Map<String, Float>
}
```

### UnifiedObjectResult

面向融合与导出的派生读模型。YOLO 提供 bbox/confidence，VLM 提供 count/attributes，LLM 提供 evidence/discrepancies。

```kotlin
data class UnifiedObjectResult(
    val name: String,
    val count: Int? = null,
    val attributes: List<String> = emptyList(),
    val confidence: String? = null,
    val score: Float? = null,
    val bbox: BoundingBox? = null,
    val evidence: List<String> = emptyList()
) {
    fun mergeWith(other: UnifiedObjectResult): UnifiedObjectResult
}
```

### AnalysisResult

```kotlin
enum class DetectionMode { IMAGE, VIDEO, REALTIME }

data class AnalysisResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val mode: DetectionMode = DetectionMode.IMAGE,
    val yoloResult: DetectionResult? = null,
    val vlmResult: VlmResult? = null,
    val llmResult: LlmResult? = null,
    val unifiedObjects: List<UnifiedObjectResult> = emptyList(),
    val fusedSummary: String = "",
    val videoTimestampMs: Long? = null
)
```

## 4. VLM 数据类 (vlm/)

```kotlin
data class VlmResult(
    val source: String = "vlm",
    val timestampMs: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,
    val objects: List<VlmObject> = emptyList(),
    val sceneSummary: String = "",
    val rawResponse: String = ""
)

data class VlmObject(
    val name: String,
    val count: Int = 1,
    val attributes: List<String> = emptyList(),
    val confidence: Float? = null
)
```

## 5. LLM 数据类 (llm/)

```kotlin
data class LlmResult(
    val source: String = "llm",
    val timestampMs: Long = System.currentTimeMillis(),
    val summary: String = "",
    val objects: List<UnifiedObjectResult> = emptyList(),
    val discrepancies: List<Discrepancy> = emptyList(),
    val rawResponse: String = ""
)

data class Discrepancy(
    val type: String,
    val description: String
)
```

## 6. 导出数据类 (export/)

```kotlin
data class ExportResult(
    val type: ExportType,
    val filePath: String,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class ExportType { JSON, ANNOTATED_IMAGE, ANNOTATED_VIDEO }

data class ExportData(
    val version: String,
    val exportTime: String,
    val appVersion: String,
    val deviceInfo: DeviceInfo,
    val analysisResult: AnalysisResult
)

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int
)
```

## 7. 数据流转图

### 图片检测模式

```
用户选择图片
  ↓
Bitmap → YOLO NCNN 推理 → DetectionResult
  ↓
Bitmap + DetectionResult → AiApiClient (VLM) → AiResult
  ↓
DetectionResult + AiResult → AiApiClient (LLM) → AiResult
  ↓
AnalysisResult (融合, 含 unifiedObjects)
  ↓
[展示] ← fusedSummary
[导出] ← JSON / 标注图片
```

### 实时检测模式

```
Camera2 实时帧
  ↓
每帧 → YOLO NCNN 推理 → DetectionResult → [OverlayView 实时绘制框]
  ↓ (按配置间隔)
关键帧 → AiApiClient (VLM) → AiResult
  ↓
最近结果 → AiApiClient (LLM) → AiResult
  ↓
[实时面板] ← fusedSummary
```

### 视频检测模式

```
用户选择视频
  ↓
MediaCodec 解码 → 抽帧策略
  ↓
抽样帧 → YOLO NCNN 推理 → List<DetectionResult>
  ↓
关键帧 → AiApiClient (VLM) → List<AiResult>
  ↓
汇总 → AiApiClient (LLM) → AiResult
  ↓
AnalysisResult (时间轴)
  ↓
[展示] ← 视频摘要 + 时间轴
[导出] ← JSON / 标注图片
```

## 8. 架构演进说明

项目经历了从 **单一 VLM/LLM 客户端** 到 **AI 多提供商系统** 的架构升级：

| 阶段 | 架构 | 说明 |
|------|------|------|
| 初始 | `vlm/VlmClient` + `llm/LlmClient` | 单一 OpenAI-compatible API |
| 当前 | `ai/AiModelManager` + `ai/AiApiClient` | 多提供商、多模型、流式支持 |

旧模块（`vlm/`、`llm/`）仍保留在代码中，新的 AI 系统通过 `AiApiClient` 统一处理 OpenAI-compatible 和 Anthropic 两种 API 格式。

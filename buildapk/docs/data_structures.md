# OpenIris 数据结构设计草案

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
    val useGpu: Boolean = true,
    val selectedModel: String = "yolov11n",
    val enableLlmFusion: Boolean = true,
    val enableJsonExport: Boolean = true,
    val enableImageExport: Boolean = true,
    val enableVideoExport: Boolean = false
)
```

## 2. 检测结果数据类

### DetectionResult (YOLO 检测结果)

```kotlin
data class DetectionResult(
    val source: String = "yolo",
    val timestampMs: Long = System.currentTimeMillis(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val objects: List<DetectedObject> = emptyList()
)

data class DetectedObject(
    val label: String,              // 类别名称
    val labelIndex: Int,            // 类别索引
    val confidence: Float,          // 置信度 0.0-1.0
    val bbox: BoundingBox           // 边界框
)

data class BoundingBox(
    val x: Float,                   // 左上角 x
    val y: Float,                   // 左上角 y
    val width: Float,               // 宽度
    val height: Float               // 高度
)
```

### VlmResult (VLM 识别结果)

```kotlin
data class VlmResult(
    val source: String = "vlm",
    val timestampMs: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,    // 输入图片（可选）
    val objects: List<VlmObject> = emptyList(),
    val sceneSummary: String = "",       // 场景摘要
    val rawResponse: String = ""         // 原始响应（用于调试）
)

data class VlmObject(
    val name: String,                   // 对象名称
    val count: Int = 1,                 // 数量
    val attributes: List<String> = emptyList(),  // 属性列表
    val confidence: Float? = null       // 置信度（可选）
)
```

### LlmResult (LLM 融合结果)

```kotlin
data class LlmResult(
    val source: String = "llm",
    val timestampMs: Long = System.currentTimeMillis(),
    val summary: String = "",                    // 中文融合摘要
    val objects: List<FusedObject> = emptyList(),
    val discrepancies: List<Discrepancy> = emptyList(),
    val rawResponse: String = ""                 // 原始响应
)

data class FusedObject(
    val name: String,
    val count: Int,
    val evidence: List<String>,         // 证据来源 ["yolo", "vlm"]
    val confidence: String = "high"     // high / medium / low
)

data class Discrepancy(
    val type: String,                   // 差异类型
    val description: String             // 差异描述
)
```

## 3. 统一结果数据类

### AnalysisResult (统一分析结果)

```kotlin
data class AnalysisResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val mode: DetectionMode = DetectionMode.IMAGE,
    val yoloResult: DetectionResult? = null,
    val vlmResult: VlmResult? = null,
    val llmResult: LlmResult? = null,
    val fusedSummary: String = "",              // 最终中文摘要
    val processedImage: Bitmap? = null,         // 处理后图片（带标注）
    val videoTimestampMs: Long? = null          // 视频时间戳（仅视频模式）
)

enum class DetectionMode {
    IMAGE,          // 静态图片
    VIDEO,          // 视频文件
    REALTIME        // 实时摄像头
}
```

## 4. VLM/LLM API 数据类

### VlmRequest

```kotlin
data class VlmRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048
)

data class Message(
    val role: String,           // "system", "user", "assistant"
    val content: List<Content>
)

sealed class Content {
    data class Text(val text: String) : Content()
    data class ImageUrl(val imageUrl: ImageUrlData) : Content()
}

data class ImageUrlData(
    val url: String             // "data:image/jpeg;base64,..."
)
```

### VlmResponse

```kotlin
data class VlmResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val message: Message,
    val finishReason: String
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
```

### LlmRequest

```kotlin
data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val responseFormat: ResponseFormat? = null
)

data class LlmMessage(
    val role: String,
    val content: String
)

data class ResponseFormat(
    val type: String = "json_object"
)
```

## 5. 导出数据类

### ExportResult

```kotlin
data class ExportResult(
    val type: ExportType,
    val filePath: String,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class ExportType {
    JSON,
    ANNOTATED_IMAGE,
    ANNOTATED_VIDEO
}
```

### JsonExportData

```kotlin
data class JsonExportData(
    val version: String = "1.0",
    val exportTime: String,             // ISO 8601
    val appVersion: String,
    val deviceInfo: DeviceInfo,
    val analysisResults: List<AnalysisResult>
)

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int
)
```

## 6. Prompt 模板

### VLM 识别 Prompt

```kotlin
const val VLM_SYSTEM_PROMPT = """
你是一个专业的图像分析助手。请仔细分析图片，识别图中的所有对象。

对于每个对象，请输出：
1. 对象名称（中文）
2. 数量
3. 基本属性（颜色、大小、状态等）

请用 JSON 格式输出，结构如下：
{
  "objects": [
    {
      "name": "对象名称",
      "count": 数量,
      "attributes": ["属性1", "属性2"]
    }
  ],
  "sceneSummary": "场景整体描述"
}
"""
```

### LLM 融合 Prompt

```kotlin
const val LLM_FUSION_SYSTEM_PROMPT = """
你是一个数据融合专家。请整合 YOLO 目标检测和 VLM 视觉语言模型的识别结果，生成统一的中文分析摘要。

输入数据格式：
- YOLO 结果：包含检测到的对象类别、置信度、边界框
- VLM 结果：包含对象名称、数量、属性、场景描述

请输出：
1. 整体场景摘要（中文）
2. 对象列表（名称、数量、证据来源）
3. 差异分析（如果 YOLO 和 VLM 结果不一致）

请用 JSON 格式输出：
{
  "summary": "整体摘要",
  "objects": [
    {
      "name": "对象名称",
      "count": 数量,
      "evidence": ["yolo", "vlm"],
      "confidence": "high"
    }
  ],
  "discrepancies": [
    {
      "type": "count_mismatch",
      "description": "描述"
    }
  ]
}
"""
```

## 7. 实时检测状态管理

### RealtimeState

```kotlin
data class RealtimeState(
    val isRunning: Boolean = false,
    val fps: Float = 0f,
    val lastYoloResult: DetectionResult? = null,
    val lastVlmResult: VlmResult? = null,
    val lastLlmResult: LlmResult? = null,
    val vlmPending: Boolean = false,        // VLM 请求是否进行中
    val llmPending: Boolean = false,        // LLM 请求是否进行中
    val frameCount: Int = 0,                // 总帧数
    val detectionCount: Int = 0,            // 总检测数
    val errorMessage: String? = null
)
```

## 8. 数据流转图

### 图片检测模式

```
用户选择图片
  ↓
Bitmap → YOLO NCNN 推理 → DetectionResult
  ↓
Bitmap + DetectionResult → VLM API → VlmResult
  ↓
DetectionResult + VlmResult → LLM API → LlmResult
  ↓
AnalysisResult (融合)
  ↓
[展示] ← 中文摘要
[导出] ← JSON / 标注图片
```

### 实时检测模式

```
CameraX 实时帧
  ↓
每帧 → YOLO NCNN 推理 → DetectionResult → [实时绘制框]
  ↓ (每 5 秒)
关键帧 → VLM API → VlmResult
  ↓
最近结果 → LLM API → LlmResult
  ↓
[实时面板] ← 中文摘要
```

### 视频检测模式

```
用户选择视频
  ↓
视频解码 → 抽帧策略
  ↓
抽样帧 → YOLO NCNN 推理 → List<DetectionResult>
  ↓
关键帧 → VLM API → List<VlmResult>
  ↓
汇总 → LLM API → LlmResult
  ↓
AnalysisResult (时间轴)
  ↓
[展示] ← 视频摘要 + 时间轴
[导出] ← JSON / 标注图片 / 标注视频(可选)
```

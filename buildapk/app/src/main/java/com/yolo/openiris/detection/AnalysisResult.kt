package com.yolo.openiris.detection

import com.yolo.openiris.llm.LlmResult
import com.yolo.openiris.vlm.VlmResult

/**
 * 检测模式枚举
 */
enum class DetectionMode {
    IMAGE,          // 静态图片
    VIDEO,          // 视频文件
    REALTIME        // 实时摄像头
}

/**
 * 统一分析结果
 */
data class AnalysisResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val mode: DetectionMode = DetectionMode.IMAGE,
    val yoloResult: DetectionResult? = null,
    val vlmResult: VlmResult? = null,
    val llmResult: LlmResult? = null,
    val unifiedObjects: List<UnifiedObjectResult> = emptyList(),
    val fusedSummary: String = "",              // 最终中文摘要
    val videoTimestampMs: Long? = null          // 视频时间戳（仅视频模式）
) {
    companion object {
        fun fromResults(
            mode: DetectionMode = DetectionMode.IMAGE,
            yoloResult: DetectionResult? = null,
            vlmResult: VlmResult? = null,
            llmResult: LlmResult? = null,
            videoTimestampMs: Long? = null
        ): AnalysisResult {
            val fallbackObjects = combineUnifiedObjects(yoloResult, vlmResult)
            return AnalysisResult(
                mode = mode,
                yoloResult = yoloResult,
                vlmResult = vlmResult,
                llmResult = llmResult,
                unifiedObjects = llmResult?.objects ?: fallbackObjects,
                fusedSummary = llmResult?.summary ?: vlmResult?.sceneSummary.orEmpty(),
                videoTimestampMs = videoTimestampMs
            )
        }

        private fun combineUnifiedObjects(
            yoloResult: DetectionResult?,
            vlmResult: VlmResult?
        ): List<UnifiedObjectResult> {
            val merged = linkedMapOf<String, UnifiedObjectResult>()

            yoloResult?.unifiedObjects().orEmpty().forEach { unifiedObject ->
                merged[unifiedObject.name] =
                    merged[unifiedObject.name]?.mergeWith(unifiedObject) ?: unifiedObject
            }

            vlmResult?.unifiedObjects().orEmpty().forEach { unifiedObject ->
                merged[unifiedObject.name] =
                    merged[unifiedObject.name]?.mergeWith(unifiedObject) ?: unifiedObject
            }

            return merged.values.toList()
        }
    }
}

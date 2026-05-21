package com.yolo.openiris.llm

import com.yolo.openiris.detection.UnifiedObjectResult

/**
 * 差异信息
 */
data class Discrepancy(
    val type: String,                   // 差异类型
    val description: String             // 差异描述
)

/**
 * LLM 融合结果
 */
data class LlmResult(
    val source: String = "llm",
    val timestampMs: Long = System.currentTimeMillis(),
    val summary: String = "",                    // 中文融合摘要
    val objects: List<UnifiedObjectResult> = emptyList(),
    val discrepancies: List<Discrepancy> = emptyList(),
    val rawResponse: String = ""                 // 原始响应
) {
    fun hasDiscrepancies(): Boolean {
        return discrepancies.isNotEmpty()
    }
}

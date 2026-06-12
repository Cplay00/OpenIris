package com.yolo.openiris.detection

/**
 * 统一对象结果模型。
 *
 * 这是一个面向融合与导出的派生读模型:
 * - YOLO 提供 bbox / confidence
 * - VLM 提供 count / attributes
 * - LLM 提供 evidence / discrepancies 归纳
 */
data class UnifiedObjectResult(
    val name: String,
    val count: Int? = null,
    val attributes: List<String> = emptyList(),
    val confidence: String? = null,
    val score: Float? = null,
    val bbox: BoundingBox? = null,
    val evidence: List<String> = emptyList()
) {
    fun mergeWith(other: UnifiedObjectResult): UnifiedObjectResult {
        return UnifiedObjectResult(
            name = name,
            count = when {
                count == null -> other.count
                other.count == null -> count
                else -> maxOf(count, other.count)
            },
            attributes = (attributes + other.attributes).distinct(),
            confidence = confidence ?: other.confidence,
            score = when {
                score == null -> other.score
                other.score == null -> score
                else -> maxOf(score, other.score)
            },
            bbox = bbox ?: other.bbox,
            evidence = (evidence + other.evidence).distinct()
        )
    }
}

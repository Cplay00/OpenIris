package com.yolo.openiris.vlm

import com.yolo.openiris.detection.UnifiedObjectResult

/**
 * VLM 识别的对象
 */
data class VlmObject(
    val name: String,                           // 对象名称
    val count: Int = 1,                         // 数量
    val attributes: List<String> = emptyList(),  // 属性列表
    val confidence: Float? = null               // 置信度(可选)
) {
    fun toUnifiedObjectResult(): UnifiedObjectResult {
        return UnifiedObjectResult(
            name = name,
            count = count,
            attributes = attributes,
            score = confidence,
            evidence = listOf("vlm")
        )
    }
}

/**
 * VLM 识别结果
 */
data class VlmResult(
    val source: String = "vlm",
    val timestampMs: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,            // 输入图片(可选)
    val objects: List<VlmObject> = emptyList(),
    val sceneSummary: String = "",               // 场景摘要
    val rawResponse: String = ""                 // 原始响应(用于调试)
) {
    /**
     * 按名称统计对象数量
     */
    fun countByName(): Map<String, Int> {
        return objects
            .groupBy { it.name }
            .mapValues { (_, groupedObjects) -> groupedObjects.sumOf { it.count.coerceAtLeast(0) } }
    }

    /**
     * 获取所有对象名称列表
     */
    fun objectNames(): List<String> {
        return objects.map { it.name }.distinct()
    }

    /**
     * 转换为统一对象结果列表。
     */
    fun unifiedObjects(): List<UnifiedObjectResult> {
        return objects.map { it.toUnifiedObjectResult() }
    }
}

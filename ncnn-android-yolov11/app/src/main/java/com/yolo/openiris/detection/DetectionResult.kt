package com.yolo.openiris.detection

import android.graphics.RectF

/**
 * 边界框数据类
 */
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun toRectF(): RectF = RectF(x, y, x + width, y + height)

    companion object {
        fun fromRectF(rectF: RectF): BoundingBox = BoundingBox(
            x = rectF.left,
            y = rectF.top,
            width = rectF.width(),
            height = rectF.height()
        )
    }
}

/**
 * 检测到的对象
 */
data class DetectedObject(
    val label: String,              // 类别名称
    val labelIndex: Int,            // 类别索引
    val confidence: Float,          // 置信度 0.0-1.0
    val bbox: BoundingBox           // 边界框
) {
    fun toUnifiedObjectResult(): UnifiedObjectResult {
        return UnifiedObjectResult(
            name = label,
            count = 1,
            score = confidence,
            bbox = bbox,
            evidence = listOf("yolo")
        )
    }
}

/**
 * YOLO 检测结果
 */
data class DetectionResult(
    val source: String = "yolo",
    val timestampMs: Long = System.currentTimeMillis(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val objects: List<DetectedObject> = emptyList()
) {
    /**
     * 按类别统计对象数量
     */
    fun countByLabel(): Map<String, Int> {
        return objects.groupingBy { it.label }.eachCount()
    }

    /**
     * 获取所有唯一类别
     */
    fun uniqueLabels(): List<String> {
        return objects.map { it.label }.distinct()
    }

    /**
     * 获取最高置信度的对象
     */
    fun topConfidentObjects(limit: Int = 10): List<DetectedObject> {
        return objects.sortedByDescending { it.confidence }.take(limit)
    }

    /**
     * 转换为统一对象结果列表。
     */
    fun unifiedObjects(): List<UnifiedObjectResult> {
        return objects.map { it.toUnifiedObjectResult() }
    }

    /**
     * 获取每个类别的最高置信度。
     */
    fun maxConfidenceByLabel(): Map<String, Float> {
        return objects
            .groupBy { it.label }
            .mapValues { (_, detectedObjects) -> detectedObjects.maxOf { it.confidence } }
    }
}

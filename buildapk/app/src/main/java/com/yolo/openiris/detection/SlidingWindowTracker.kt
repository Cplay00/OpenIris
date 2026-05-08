package com.yolo.openiris.detection

import java.util.concurrent.ConcurrentHashMap

/**
 * 滑动窗口统计追踪器
 * 用于实时检测结果的统计汇总
 */
class SlidingWindowTracker(private val windowDurationMs: Long = 15000) {

    /**
     * 带时间戳的检测记录
     */
    data class TimestampedDetection(
        val label: String,
        val confidence: Float,
        val timestampMs: Long = System.currentTimeMillis()
    )

    /**
     * 对象统计信息
     */
    data class ObjectStats(
        val name: String,
        var count: Int = 0,
        var totalConfidence: Float = 0f,
        val detections: MutableList<TimestampedDetection> = mutableListOf()
    ) {
        val avgConfidence: Float
            get() = if (count > 0) totalConfidence / count else 0f
    }

    // 存储所有检测记录
    private val detections = mutableListOf<TimestampedDetection>()

    // 按对象名称分组的统计
    private val objectStatsMap = ConcurrentHashMap<String, ObjectStats>()

    /**
     * 添加检测结果
     */
    @Synchronized
    fun addDetection(label: String, confidence: Float) {
        val detection = TimestampedDetection(label, confidence)
        detections.add(detection)

        // 更新统计
        val stats = objectStatsMap.getOrPut(label) { ObjectStats(label) }
        stats.count++
        stats.totalConfidence += confidence
        stats.detections.add(detection)

        // 清理过期数据
        cleanup()
    }

    /**
     * 批量添加检测结果
     */
    @Synchronized
    fun addDetections(detections: List<Pair<String, Float>>) {
        detections.forEach { (label, confidence) ->
            addDetection(label, confidence)
        }
    }

    /**
     * 清理过期数据
     */
    private fun cleanup() {
        val now = System.currentTimeMillis()
        val cutoff = now - windowDurationMs

        // 移除过期的检测记录
        val iterator = detections.iterator()
        while (iterator.hasNext()) {
            val detection = iterator.next()
            if (detection.timestampMs < cutoff) {
                iterator.remove()
                // 更新对应的统计
                val stats = objectStatsMap[detection.label]
                if (stats != null) {
                    stats.count--
                    stats.totalConfidence -= detection.confidence
                    stats.detections.remove(detection)
                    if (stats.count <= 0) {
                        objectStatsMap.remove(detection.label)
                    }
                }
            } else {
                break // 因为是按时间顺序添加的，所以遇到未过期的就可以停止
            }
        }
    }

    /**
     * 获取当前窗口内的所有统计
     */
    @Synchronized
    fun getSummary(): Map<String, ObjectStats> {
        cleanup()
        return objectStatsMap.toMap()
    }

    /**
     * 获取当前窗口内的检测数量
     */
    @Synchronized
    fun getTotalCount(): Int {
        cleanup()
        return detections.size
    }

    /**
     * 获取当前窗口内不同对象的数量
     */
    @Synchronized
    fun getUniqueCount(): Int {
        cleanup()
        return objectStatsMap.size
    }

    /**
     * 清空所有数据
     */
    @Synchronized
    fun clear() {
        detections.clear()
        objectStatsMap.clear()
    }

    /**
     * 获取窗口时长（秒）
     */
    fun getWindowDurationSeconds(): Int {
        return (windowDurationMs / 1000).toInt()
    }

    /**
     * 检查是否有数据
     */
    @Synchronized
    fun hasData(): Boolean {
        cleanup()
        return detections.isNotEmpty()
    }

    /**
     * 获取排序后的统计列表（按数量降序）
     */
    @Synchronized
    fun getSortedSummary(): List<ObjectStats> {
        cleanup()
        return objectStatsMap.values.sortedByDescending { it.count }
    }
}

package com.yolo.openiris.detection

import java.util.concurrent.ConcurrentHashMap

/**
 * 婊戝姩绐楀彛缁熻杩借釜鍣? * 鐢ㄤ簬瀹炴椂妫€娴嬬粨鏋滅殑缁熻姹囨€? */
class SlidingWindowTracker(private val windowDurationMs: Long = 15000) {

    /**
     * 甯︽椂闂存埑鐨勬娴嬭褰?     */
    data class TimestampedDetection(
        val label: String,
        val confidence: Float,
        val timestampMs: Long = System.currentTimeMillis()
    )

    /**
     * 瀵硅薄缁熻淇℃伅
     */
    data class ObjectStats(
        val name: String,
        val count: Int = 0,
        val totalConfidence: Float = 0f
    ) {
        val avgConfidence: Float
            get() = if (count > 0) totalConfidence / count else 0f
    }

    // 瀛樺偍鎵€鏈夋娴嬭褰?    private val detections = mutableListOf<TimestampedDetection>()

    // 鎸夊璞″悕绉板垎缁勭殑缁熻锛堝唴閮ㄤ娇鐢ㄥ彲鍙樼増鏈級
    private data class MutableObjectStats(
        val name: String,
        var count: Int = 0,
        var totalConfidence: Float = 0f,
        val detections: MutableList<TimestampedDetection> = mutableListOf()
    )

    private val objectStatsMap = ConcurrentHashMap<String, MutableObjectStats>()

    /**
     * 娣诲姞妫€娴嬬粨鏋?     */
    @Synchronized
    fun addDetection(label: String, confidence: Float) {
        val detection = TimestampedDetection(label, confidence)
        detections.add(detection)

        // 鏇存柊缁熻
        val stats = objectStatsMap.getOrPut(label) { MutableObjectStats(label) }
        stats.count++
        stats.totalConfidence += confidence
        stats.detections.add(detection)

        // 娓呯悊杩囨湡鏁版嵁
        cleanup()
    }

    /**
     * 鎵归噺娣诲姞妫€娴嬬粨鏋?     */
    @Synchronized
    fun addDetections(detections: List<Pair<String, Float>>) {
        detections.forEach { (label, confidence) ->
            val detection = TimestampedDetection(label, confidence)
            this.detections.add(detection)
            val stats = objectStatsMap.getOrPut(label) { MutableObjectStats(label) }
            stats.count++
            stats.totalConfidence += confidence
            stats.detections.add(detection)
        }
        cleanup()
    }

    /**
     * 鑾峰彇褰撳墠绐楀彛鍐呯殑鎵€鏈夌粺璁?     */
    @Synchronized
    fun getSummary(): Map<String, ObjectStats> {
        cleanup()
        return objectStatsMap.mapValues { (_, stats) ->
            ObjectStats(stats.name, stats.count, stats.totalConfidence)
        }
    }

    /**
     * 鑾峰彇褰撳墠绐楀彛鍐呯殑妫€娴嬫暟閲?     */
    @Synchronized
    fun getTotalCount(): Int {
        cleanup()
        return detections.size
    }

    /**
     * 鑾峰彇褰撳墠绐楀彛鍐呬笉鍚屽璞$殑鏁伴噺
     */
    @Synchronized
    fun getUniqueCount(): Int {
        cleanup()
        return objectStatsMap.size
    }

    /**
     * 娓呯┖鎵€鏈夋暟鎹?     */
    @Synchronized
    fun clear() {
        detections.clear()
        objectStatsMap.clear()
    }

    /**
     * 鑾峰彇绐楀彛鏃堕暱锛堢锛?     */
    fun getWindowDurationSeconds(): Int {
        return (windowDurationMs / 1000).toInt()
    }

    /**
     * 妫€鏌ユ槸鍚︽湁鏁版嵁
     */
    @Synchronized
    fun hasData(): Boolean {
        cleanup()
        return detections.isNotEmpty()
    }

    /**
     * 鑾峰彇鎺掑簭鍚庣殑缁熻鍒楄〃锛堟寜鏁伴噺闄嶅簭锛?     */
    @Synchronized
    fun getSortedSummary(): List<ObjectStats> {
        cleanup()
        return objectStatsMap.values
            .sortedByDescending { it.count }
            .map { stats -> ObjectStats(stats.name, stats.count, stats.totalConfidence) }
    }
}

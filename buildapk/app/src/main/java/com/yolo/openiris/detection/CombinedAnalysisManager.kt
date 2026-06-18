package com.yolo.openiris.detection

/**
 * 综合分析标签条目
 * 独立管理每个标签的生命周期
 */
data class CombinedLabelEntry(
    val name: String,
    var maxCount: Int = 0,           // 历史最高计数（15秒内）
    var isMax: Boolean = false,      // 是否标注为 "max"
    val displayStartTime: Long = System.currentTimeMillis(), // 展示开始时间
    var lastCountUpdateTime: Long = System.currentTimeMillis(), // 最后计数更新时间（用于15秒过期判断）
    var confidenceSum: Float = 0f,   // 置信度累加值
    var confidenceCount: Int = 0     // 置信度采集次数
) {
    /** 平均置信度 */
    val avgConfidence: Float
        get() = if (confidenceCount > 0) confidenceSum / confidenceCount else 0f

    /** 条目是否过期（超过15秒未更新计数） */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return (now - lastCountUpdateTime) > 15_000L
    }
}

/**
 * 综合分析管理器
 * 独立管理综合分析标签的生命周期
 *
 * 核心机制：
 * 1. 展示时长：所有标签保留15秒展示时间（从最后一次计数更新开始计算）
 * 2. 计数机制：取15秒内从YOLO/AI识别获取到的对应标签计数的最高值
 * 3. max标注：当YOLO/AI识别的对应标签计数 <= 综合分析中的对应标签计数，标记为"max"
 *             当YOLO/AI识别的对应标签计数 > 综合分析中的对应标签计数，取更高计数，撤销"max"
 * 4. 平均置信度：仅统计标签被检测到的帧的置信度（未检测帧不参与计算）
 * 5. 机制独立：标签信息、计数、max标注、平均置信度各自独立
 * 6. 展示时长与采集间隔独立：无论采集间隔为多少，展示时长始终为15秒
 */
class CombinedAnalysisManager {

    /** 当前活跃的标签条目 */
    private val entries = mutableMapOf<String, CombinedLabelEntry>()

    /**
     * 刷新综合分析结果
     * @param yoloResults 当前帧 YOLO 检测结果 (名称, 计数, 置信度)
     * @param aiResults 当前帧 AI 检测结果 (名称, 计数, 置信度)
     * @return 当前活跃的标签列表（按计数降序）
     */
    @Synchronized
    fun refresh(
        yoloResults: List<Triple<String, Int, Float>>,
        aiResults: List<Triple<String, Int, Float>>
    ): List<CombinedLabelEntry> {
        val now = System.currentTimeMillis()

        // 1. 清理过期条目
        entries.values.removeAll { it.isExpired(now) }

        // 2. 合并 YOLO 和 AI 结果，取每个标签的最高计数
        val mergedResults = mutableMapOf<String, Pair<Int, Float>>()

        for ((name, count, confidence) in yoloResults) {
            val existing = mergedResults[name]
            if (existing == null || count > existing.first) {
                mergedResults[name] = Pair(count, confidence)
            }
        }

        for ((name, count, confidence) in aiResults) {
            val existing = mergedResults[name]
            if (existing == null || count > existing.first) {
                mergedResults[name] = Pair(count, confidence)
            } else if (count == existing.first) {
                // 同计数时取平均置信度
                mergedResults[name] = Pair(count, (existing.second + confidence) / 2f)
            }
        }

        // 3. 更新条目
        for ((name, countAndConfidence) in mergedResults) {
            val (count, confidence) = countAndConfidence
            val existing = entries[name]

            if (existing == null) {
                // 新标签：直接添加
                entries[name] = CombinedLabelEntry(
                    name = name,
                    maxCount = count,
                    isMax = false,
                    confidenceSum = confidence,
                    confidenceCount = 1
                )
            } else {
                // 已有标签：判断计数变化
                if (count > existing.maxCount) {
                    // 计数增加：更新最高计数，取消 max 标注，重置展示时间
                    existing.maxCount = count
                    existing.isMax = false
                    existing.lastCountUpdateTime = now
                } else {
                    // 计数未增加：标注为 max，不重置展示时间
                    existing.isMax = true
                }
                // 累计置信度（无论计数是否增加）
                existing.confidenceSum += confidence
                existing.confidenceCount++
            }
        }

        // 4. 处理当前帧未出现但仍在15秒窗口内的标签
        // 标签未出现时只标记 isMax，不累加置信度（避免稀释平均值）
        for ((name, entry) in entries) {
            if (name !in mergedResults) {
                // 标注为 max（因为当前帧计数为0，小于等于已有计数）
                entry.isMax = true
            }
        }

        // 5. 返回按计数降序排列的结果
        return entries.values.sortedByDescending { it.maxCount }
    }

    /**
     * 获取当前活跃条目数量
     */
    @Synchronized
    fun getActiveCount(): Int {
        val now = System.currentTimeMillis()
        return entries.values.count { !it.isExpired(now) }
    }

    /**
     * 清空所有条目
     */
    @Synchronized
    fun clear() {
        entries.clear()
    }
}

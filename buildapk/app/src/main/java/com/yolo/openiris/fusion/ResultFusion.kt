package com.yolo.openiris.fusion

import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.detection.UnifiedObjectResult
import com.yolo.openiris.llm.Discrepancy
import com.yolo.openiris.llm.LlmResult
import com.yolo.openiris.vlm.VlmResult

/**
 * 结果融合器
 * 在本地快速合并 YOLO 和 VLM 的结果,作为 LLM 融合的前置步骤
 */
object ResultFusion {

    /**
     * 简单融合:直接对比 YOLO 和 VLM 的结果
     */
    fun simpleFusion(yoloResult: DetectionResult, vlmResult: VlmResult): LlmResult {
        val yoloCounts = yoloResult.countByLabel()
        val vlmCounts = vlmResult.countByName()
        val yoloScores = yoloResult.maxConfidenceByLabel()
        val vlmObjectsByName = vlmResult.objects.groupBy { it.name }

        val allNames = (yoloCounts.keys + vlmCounts.keys).distinct()
        val fusedObjects = mutableListOf<UnifiedObjectResult>()
        val discrepancies = mutableListOf<Discrepancy>()

        for (name in allNames) {
            val yoloCount = yoloCounts[name] ?: 0
            val vlmCount = vlmCounts[name] ?: 0
            val evidence = mutableListOf<String>()
            val attributes = vlmObjectsByName[name]
                .orEmpty()
                .flatMap { it.attributes }
                .distinct()

            if (yoloCount > 0) evidence.add("yolo")
            if (vlmCount > 0) evidence.add("vlm")

            val count = if (yoloCount > 0 && vlmCount > 0) {
                // 两者都有,取平均值或最大值
                maxOf(yoloCount, vlmCount)
            } else if (yoloCount > 0) {
                yoloCount
            } else {
                vlmCount
            }

            val confidence = when {
                yoloCount > 0 && vlmCount > 0 -> "high"
                yoloCount > 0 || vlmCount > 0 -> "medium"
                else -> "low"
            }

            fusedObjects.add(
                UnifiedObjectResult(
                    name = name,
                    count = count,
                    attributes = attributes,
                    confidence = confidence,
                    score = yoloScores[name],
                    evidence = evidence
                )
            )

            // 检测数量不一致
            if (yoloCount > 0 && vlmCount > 0 && yoloCount != vlmCount) {
                discrepancies.add(
                    Discrepancy(
                        type = "count_mismatch",
                        description = "$name 数量不一致: YOLO 检测 $yoloCount 个, VLM 识别 $vlmCount 个"
                    )
                )
            }

            // 检测仅出现在一个来源中的对象
            if (yoloCount > 0 && vlmCount == 0) {
                discrepancies.add(
                    Discrepancy(
                        type = "only_in_yolo",
                        description = "$name 仅在 YOLO 中检测到"
                    )
                )
            }
            if (vlmCount > 0 && yoloCount == 0) {
                discrepancies.add(
                    Discrepancy(
                        type = "only_in_vlm",
                        description = "$name 仅在 VLM 中识别到"
                    )
                )
            }
        }

        val summary = buildSummary(fusedObjects, discrepancies, vlmResult.sceneSummary)

        return LlmResult(
            summary = summary,
            objects = fusedObjects,
            discrepancies = discrepancies
        )
    }

    private fun buildSummary(
        objects: List<UnifiedObjectResult>,
        discrepancies: List<Discrepancy>,
        sceneSummary: String
    ): String {
        val sb = StringBuilder()

        if (sceneSummary.isNotBlank()) {
            sb.append("场景: $sceneSummary\n")
        }

        if (objects.isNotEmpty()) {
            sb.append("\n检测到 ${objects.size} 类对象:\n")
            objects.forEach { obj ->
                val source = when (obj.evidence.size) {
                    2 -> "(YOLO+VLM 共同确认)"
                    1 -> "(仅 ${obj.evidence[0]})"
                    else -> ""
                }
                sb.append("- ${obj.name}: ${obj.count}个 $source\n")
            }
        }

        if (discrepancies.isNotEmpty()) {
            sb.append("\n注意: 发现 ${discrepancies.size} 处差异\n")
        }

        return sb.toString().trim()
    }

    /**
     * 检查 YOLO 和 VLM 结果是否一致
     * 当两边都有检测结果且存在共同识别的对象时返回 true
     */
    fun isConsistent(yoloResult: DetectionResult, vlmResult: VlmResult): Boolean {
        val yoloLabels = yoloResult.uniqueLabels().map { it.lowercase() }.toSet()
        val vlmNames = vlmResult.objectNames().map { it.lowercase() }.toSet()

        if (yoloLabels.isEmpty() || vlmNames.isEmpty()) return false

        // 检查是否存在交集(共同识别的对象)
        return yoloLabels.intersect(vlmNames).isNotEmpty()
    }
}

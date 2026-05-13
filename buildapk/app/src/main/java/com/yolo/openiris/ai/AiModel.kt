package com.yolo.openiris.ai

import java.util.UUID

/**
 * AI 模型数据模型
 */
data class AiModel(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val hasVision: Boolean = false,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val priority: Int = 0,
    val enableReasoning: Boolean = false,
    val assignedTasks: List<String> = emptyList(),
    val customHeaders: Map<String, String> = emptyMap(),
    val customBody: Map<String, Any> = emptyMap()
) {
    companion object {
        const val TASK_VISUAL_RECOGNITION = "visual_recognition"
        const val TASK_DETECTION_SUMMARY = "detection_summary"
    }

    /**
     * 获取显示用的完整名称
     */
    fun getFullName(): String {
        return "$displayName ($modelId)"
    }

    /**
     * 是否支持视觉输入
     */
    fun supportsVision(): Boolean = hasVision

    /**
     * 是否分配了指定任务
     */
    fun hasTask(task: String): Boolean = assignedTasks.contains(task)
}

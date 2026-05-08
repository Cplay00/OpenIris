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
    val priority: Int = 0
) {
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
}

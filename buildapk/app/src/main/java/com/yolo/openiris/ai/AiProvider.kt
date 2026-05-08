package com.yolo.openiris.ai

import java.util.UUID

/**
 * AI 提供商数据模型
 */
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<AiModel> = emptyList(),
    val isEnabled: Boolean = true
) {
    /**
     * 获取有效的 API Base URL（确保以 /v1 结尾）
     */
    fun getEffectiveBaseUrl(): String {
        val url = baseUrl.trimEnd('/')
        return if (url.endsWith("/v1")) url else "$url/v1"
    }

    /**
     * 验证提供商配置是否有效
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()
    }
}

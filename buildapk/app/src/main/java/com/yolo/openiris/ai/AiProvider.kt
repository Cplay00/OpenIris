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
     * 获取有效的 API Base URL（确保以 /v1 结尾，避免重复追加）
     */
    fun getEffectiveBaseUrl(): String {
        val url = baseUrl.trimEnd('/')
        // 如果已经以 /v1 结尾，直接返回
        if (url.endsWith("/v1")) return url
        // 如果包含 /v1/ 后面还有路径段，说明已经有版本路径，直接返回
        if (url.contains("/v1/")) return url
        return "$url/v1"
    }

    /**
     * 验证提供商配置是否有效
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()
    }
}

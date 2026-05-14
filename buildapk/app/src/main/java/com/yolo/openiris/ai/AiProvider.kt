package com.yolo.openiris.ai

import java.util.UUID

/**
 * API 格式枚举
 */
enum class ApiFormat {
    OPENAI_COMPATIBLE,    // OpenAI兼容API（默认）
    ANTHROPIC             // Anthropic兼容API
}

/**
 * AI 提供商数据模型
 */
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<AiModel> = emptyList(),
    val isEnabled: Boolean = true,
    val apiFormat: ApiFormat = ApiFormat.OPENAI_COMPATIBLE,
    val apiPath: String = "/chat/completions",
    val useResponseApi: Boolean = false,
    val enableStream: Boolean = true
) {
    /**
     * 获取有效的 API Base URL
     */
    fun getEffectiveBaseUrl(): String {
        val url = baseUrl.trimEnd('/')
        // Anthropic 格式不需要追加 /v1
        if (apiFormat == ApiFormat.ANTHROPIC) return url
        // OpenAI 兼容格式：检查是否已经包含版本路径
        if (url.endsWith("/v1")) return url
        if (url.contains("/v1/")) return url
        if (url.endsWith("/v2")) return url
        if (url.contains("/v2/")) return url
        return "$url/v1"
    }

    /**
     * 获取完整的 API 路径
     */
    fun getEffectiveApiPath(): String {
        return apiPath
    }

    /**
     * 获取默认的 Base URL
     */
    fun getDefaultBaseUrl(): String {
        return when (apiFormat) {
            ApiFormat.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
            ApiFormat.ANTHROPIC -> "https://api.anthropic.com"
        }
    }

    /**
     * 获取默认的 API 路径
     */
    fun getDefaultApiPath(): String {
        return when {
            apiFormat == ApiFormat.ANTHROPIC -> "/v1/messages"
            useResponseApi -> "/responses"
            else -> "/chat/completions"
        }
    }

    /**
     * 验证提供商配置是否有效
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()
    }
}

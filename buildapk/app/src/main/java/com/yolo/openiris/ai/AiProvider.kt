package com.yolo.openiris.ai

import java.net.URI
import java.util.UUID

/**
 * API 格式枚举
 */
enum class ApiFormat {
    OPENAI_COMPATIBLE,    // OpenAI兼容API(默认)
    ANTHROPIC             // Anthropic兼容API
}

/**
 * AI 提供商数据模型
 *
 * 注意:apiKey 不应出现在 toString()/hashCode() 中,已手动排除。
 */
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<AiModel> = emptyList(),
    val isEnabled: Boolean = true,
    val apiFormat: ApiFormat = ApiFormat.OPENAI_COMPATIBLE,
    val apiPath: String = "/chat/completions",
    val useResponseApi: Boolean = false,
    val enableStream: Boolean = true
) {
    // 排除 apiKey,防止日志/序列化泄露
    override fun toString(): String {
        return "AiProvider(id=$id, name=$name, baseUrl=$baseUrl, apiKey=***, " +
            "models=${models.size} items, isEnabled=$isEnabled, apiFormat=$apiFormat, " +
            "apiPath=$apiPath, useResponseApi=$useResponseApi, enableStream=$enableStream)"
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + baseUrl.hashCode()
        result = 31 * result + models.hashCode()
        result = 31 * result + isEnabled.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiProvider) return false
        return id == other.id && name == other.name && baseUrl == other.baseUrl &&
            apiKey == other.apiKey && models == other.models && isEnabled == other.isEnabled
    }

    /**
     * 获取有效的 API Base URL
     */
    fun getEffectiveBaseUrl(): String {
        val url = baseUrl.trimEnd('/')
        // Anthropic 格式不需要追加 /v1
        if (apiFormat == ApiFormat.ANTHROPIC) return url
        // OpenAI 兼容格式:检查是否已经包含版本路径
        if (Regex("/v\\d+").containsMatchIn(url)) return url
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

    companion object {
        /**
         * 验证 baseUrl 是否安全(HTTPS,非内网地址)
         * 返回 null 表示验证通过,返回字符串表示错误信息
         */
        fun validateBaseUrl(url: String): String? {
            val normalized = url.trim()
            if (normalized.isEmpty()) return "URL 不能为空"

            val uri = runCatching { URI(normalized) }.getOrNull()
                ?: return "URL 格式无效"

            val scheme = uri.scheme?.lowercase()
            if (scheme != "https" && scheme != "http") {
                return "仅支持 http/https 协议"
            }
            // 生产环境强制 HTTPS
            if (scheme == "http") {
                val host = uri.host?.lowercase() ?: return "URL 缺少主机名"
                val isLocalhost = host == "localhost" || host == "127.0.0.1" || host == "::1"
                if (!isLocalhost) {
                    return "生产环境请使用 HTTPS(当前为 HTTP)"
                }
            }

            // SSRF 防护:阻止内网地址
            val host = uri.host?.lowercase() ?: return "URL 缺少主机名"
            val privatePatterns = listOf(
                Regex("^10\\."),
                Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\."),
                Regex("^192\\.168\\."),
                Regex("^127\\."),
                Regex("^0\\."),
                Regex("^169\\.254\\."),
                Regex("^\\[::1\\]"),
                Regex("^\\[fc"),
                Regex("^\\[fd"),
                Regex("^\\[fe80")
            )
            if (host != "localhost" && host != "::1") {
                for (pattern in privatePatterns) {
                    if (pattern.containsMatchIn(host)) {
                        return "不允许使用内网地址"
                    }
                }
            }

            return null // 验证通过
        }
    }
}

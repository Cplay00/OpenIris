package com.yolo.openiris.ai

import java.net.URI
import java.util.UUID

enum class ApiFormat {
    OPENAI_COMPATIBLE,
    ANTHROPIC
}

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

    fun getEffectiveBaseUrl(): String {
        val url = baseUrl.trimEnd('/')
        if (apiFormat == ApiFormat.ANTHROPIC) return url
        if (Regex("/v\\d+").containsMatchIn(url)) return url
        return "$url/v1"
    }

    fun getEffectiveApiPath(): String = apiPath

    fun getDefaultBaseUrl(): String {
        return when (apiFormat) {
            ApiFormat.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
            ApiFormat.ANTHROPIC -> "https://api.anthropic.com"
        }
    }

    fun getDefaultApiPath(): String {
        return when {
            apiFormat == ApiFormat.ANTHROPIC -> "/v1/messages"
            useResponseApi -> "/responses"
            else -> "/chat/completions"
        }
    }

    fun isValid(): Boolean {
        return name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()
    }

    companion object {
        fun validateBaseUrl(url: String): String? {
            val normalized = url.trim()
            if (normalized.isEmpty()) return "URL cannot be empty"
            val uri = runCatching { URI(normalized) }.getOrNull() ?: return "Invalid URL format"
            val scheme = uri.scheme?.lowercase()
            if (scheme != "https" && scheme != "http") return "Only http/https supported"
            if (scheme == "http") {
                val host = uri.host?.lowercase() ?: return "Missing host"
                val isLocalhost = host == "localhost" || host == "127.0.0.1" || host == "::1"
                if (!isLocalhost) return "Use HTTPS for non-localhost"
            }
            val host = uri.host?.lowercase() ?: return "Missing host"
            val privatePatterns = listOf(
                Regex("^10\\."), Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\."),
                Regex("^192\\.168\\."), Regex("^127\\."), Regex("^0\\."),
                Regex("^169\\.254\\.")
            )
            if (host != "localhost" && host != "::1") {
                for (pattern in privatePatterns) {
                    if (pattern.containsMatchIn(host)) return "Private IP not allowed"
                }
            }
            return null
        }
    }
}
package com.yolo.openiris.config

import java.net.URI

/**
 * 应用配置校验器
 * 仅包含纯 Kotlin/JVM 逻辑，便于单元测试
 */
object AppConfigValidator {

    fun validate(config: AppConfig): AppConfigValidationResult {
        val errors = mutableListOf<ConfigValidationError>()

        validateApiBaseUrl(config.apiBaseUrl)?.let(errors::add)
        validateApiKey(config.apiKey)?.let(errors::add)
        validateVlmModel(config.vlmModel)?.let(errors::add)
        validateLlmModel(config.llmModel)?.let(errors::add)
        validateRealtimeVlmInterval(config.vlmIntervalSeconds)?.let(errors::add)

        return AppConfigValidationResult(errors)
    }

    fun validateApiBaseUrl(apiBaseUrl: String): ConfigValidationError? {
        val normalized = apiBaseUrl.trim()
        if (normalized.isEmpty()) {
            return ConfigValidationError(
                field = ConfigField.API_BASE_URL,
                message = "Base URL 不能为空"
            )
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return ConfigValidationError(
                field = ConfigField.API_BASE_URL,
                message = "Base URL 格式无效"
            )

        val scheme = uri.scheme?.lowercase()
        if (!uri.isAbsolute || scheme.isNullOrBlank()) {
            return ConfigValidationError(
                field = ConfigField.API_BASE_URL,
                message = "Base URL 必须包含 http 或 https 协议"
            )
        }

        if (scheme != "http" && scheme != "https") {
            return ConfigValidationError(
                field = ConfigField.API_BASE_URL,
                message = "Base URL 仅支持 http 或 https 协议"
            )
        }

        if (uri.host.isNullOrBlank()) {
            return ConfigValidationError(
                field = ConfigField.API_BASE_URL,
                message = "Base URL 必须包含有效主机名"
            )
        }

        return null
    }

    fun validateApiKey(apiKey: String): ConfigValidationError? {
        return if (apiKey.trim().isEmpty()) {
            ConfigValidationError(
                field = ConfigField.API_KEY,
                message = "API Key 不能为空"
            )
        } else {
            null
        }
    }

    fun validateVlmModel(vlmModel: String): ConfigValidationError? {
        return validateRequiredText(
            value = vlmModel,
            field = ConfigField.VLM_MODEL,
            fieldLabel = "VLM model"
        )
    }

    fun validateLlmModel(llmModel: String): ConfigValidationError? {
        return validateRequiredText(
            value = llmModel,
            field = ConfigField.LLM_MODEL,
            fieldLabel = "LLM model"
        )
    }

    fun validateRealtimeVlmInterval(vlmIntervalSeconds: Int): ConfigValidationError? {
        return if (vlmIntervalSeconds !in AppConfig.MIN_VLM_INTERVAL..AppConfig.MAX_VLM_INTERVAL) {
            ConfigValidationError(
                field = ConfigField.VLM_INTERVAL_SECONDS,
                message = "实时 VLM 间隔必须在 ${AppConfig.MIN_VLM_INTERVAL} 到 ${AppConfig.MAX_VLM_INTERVAL} 秒之间"
            )
        } else {
            null
        }
    }

    fun normalizeApiBaseUrl(apiBaseUrl: String): String {
        val trimmed = apiBaseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return ""
        }

        return if (trimmed.endsWith("/v1")) {
            trimmed
        } else {
            "$trimmed/v1"
        }
    }

    private fun validateRequiredText(
        value: String,
        field: ConfigField,
        fieldLabel: String
    ): ConfigValidationError? {
        return if (value.trim().isEmpty()) {
            ConfigValidationError(
                field = field,
                message = "$fieldLabel 不能为空"
            )
        } else {
            null
        }
    }
}

data class AppConfigValidationResult(
    val errors: List<ConfigValidationError>
) {
    val isValid: Boolean
        get() = errors.isEmpty()

    fun hasError(field: ConfigField): Boolean {
        return errors.any { it.field == field }
    }
}

data class ConfigValidationError(
    val field: ConfigField,
    val message: String
)

enum class ConfigField {
    API_BASE_URL,
    API_KEY,
    VLM_MODEL,
    LLM_MODEL,
    VLM_INTERVAL_SECONDS
}

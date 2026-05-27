package com.yolo.openiris.config

import com.google.gson.annotations.SerializedName

/**
 * 应用全局配置
 */
data class AppConfig(
    @field:SerializedName(value = "apiBaseUrl", alternate = ["baseUrl"])
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    @field:SerializedName("apiKey")
    val apiKey: String = "",
    @field:SerializedName(value = "vlmModel", alternate = ["visionModel"])
    val vlmModel: String = DEFAULT_VLM_MODEL,
    @field:SerializedName(value = "llmModel", alternate = ["chatModel"])
    val llmModel: String = DEFAULT_LLM_MODEL,
    @field:SerializedName(value = "vlmIntervalSeconds", alternate = ["realtimeVlmIntervalSeconds", "vlmInterval"])
    val vlmIntervalSeconds: Int = DEFAULT_VLM_INTERVAL,
    val useGpu: Boolean = true,
    val selectedModel: String = "yolov11n",
    val enableLlmFusion: Boolean = true,
    val enableJsonExport: Boolean = true,
    val enableImageExport: Boolean = true,
    val enableVideoExport: Boolean = false
) {
    companion object {
        const val DEFAULT_API_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_VLM_MODEL = "qwen3.5-35b-a3b"
        const val DEFAULT_LLM_MODEL = "deepseek-v4-flash"
        const val DEFAULT_VLM_INTERVAL = 5
        const val MIN_VLM_INTERVAL = 2
        const val MAX_VLM_INTERVAL = 300

        val VLM_INTERVAL_OPTIONS = listOf(2, 5, 10, 30)
    }

    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return validate().isValid
    }

    /**
     * 返回详细校验结果
     */
    fun validate(): AppConfigValidationResult {
        return AppConfigValidator.validate(this)
    }

    /**
     * 返回附带 API Key 的配置副本，便于将敏感字段注入运行态配置。
     */
    fun withApiKey(apiKey: String): AppConfig {
        return copy(apiKey = apiKey.trim())
    }

    /**
     * 返回移除 API Key 的配置副本，便于非敏感配置单独流转。
     */
    fun withoutApiKey(): AppConfig {
        return copy(apiKey = "")
    }

    /**
     * 获取完整的 API Base URL（确保以 /v1 结尾）
     */
    fun getFullApiBaseUrl(): String {
        return AppConfigValidator.normalizeApiBaseUrl(apiBaseUrl)
    }
}

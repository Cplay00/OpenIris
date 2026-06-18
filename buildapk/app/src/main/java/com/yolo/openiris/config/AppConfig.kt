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
    val useGpu: Boolean = false,
    val selectedModel: String = "yolov11n",
    val enableLlmFusion: Boolean = true,
    val enableJsonExport: Boolean = true,
    val enableImageExport: Boolean = true,
    val enableVideoExport: Boolean = false,
    // 摄像头分辨率配置
    val cameraResolutionWidth: Int = DEFAULT_CAMERA_WIDTH,
    val cameraResolutionHeight: Int = DEFAULT_CAMERA_HEIGHT,
    // 自定义分辨率列表（格式：["1280x720", "1920x1080"]）
    val customResolutions: List<String> = emptyList(),
    // 实时抓取后简短展示原图
    val showCapturePreview: Boolean = false,
    // 实时检测采集间隔（秒）
    val captureIntervalSeconds: Float = DEFAULT_CAPTURE_INTERVAL
) {
    companion object {
        const val DEFAULT_API_BASE_URL = ""
        const val DEFAULT_VLM_MODEL = ""
        const val DEFAULT_LLM_MODEL = ""
        const val DEFAULT_CAPTURE_INTERVAL = 1.5f
        const val MIN_CAPTURE_INTERVAL = 0.5f
        const val MAX_CAPTURE_INTERVAL = 5.0f
        const val DEFAULT_VLM_INTERVAL = 5
        const val MIN_VLM_INTERVAL = 2
        const val MAX_VLM_INTERVAL = 300

        val VLM_INTERVAL_OPTIONS = listOf(2, 5, 10, 30)
        
        // 摄像头分辨率默认值
        const val DEFAULT_CAMERA_WIDTH = 480
        const val DEFAULT_CAMERA_HEIGHT = 640
        
        // 预设档位
        const val RESOLUTION_480P = 0
        const val RESOLUTION_720P = 1
        const val RESOLUTION_1080P = 2
        const val RESOLUTION_NATIVE = 3
        
        /**
         * 获取预设分辨率列表（根据屏幕比例动态计算）
         */
        fun getPresetResolutions(screenWidth: Int, screenHeight: Int): List<Pair<Int, Int>> {
            val aspectRatio = screenHeight.toFloat() / screenWidth.toFloat()
            return listOf(
                Pair(480, (480 * aspectRatio).toInt()),      // 480P
                Pair(720, (720 * aspectRatio).toInt()),      // 720P
                Pair(1080, (1080 * aspectRatio).toInt()),    // 1080P
                Pair(screenWidth, screenHeight)               // 原生分辨率
            )
        }
        
        /**
         * 获取预设分辨率显示名称
         */
        fun getPresetResolutionNames(): List<String> {
            return listOf("480P", "720P", "1080P", "原生分辨率")
        }
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

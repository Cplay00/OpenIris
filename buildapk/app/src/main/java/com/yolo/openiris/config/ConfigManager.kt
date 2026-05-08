package com.yolo.openiris.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 配置管理器
 * 使用 EncryptedSharedPreferences 安全存储敏感配置
 */
class ConfigManager private constructor(context: Context) {

    companion object {
        private const val PREFS_FILE = "openiris_config"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VLM_MODEL = "vlm_model"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_VLM_INTERVAL = "vlm_interval_seconds"
        private const val KEY_USE_GPU = "use_gpu"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_ENABLE_LLM_FUSION = "enable_llm_fusion"
        private const val KEY_ENABLE_JSON_EXPORT = "enable_json_export"
        private const val KEY_ENABLE_IMAGE_EXPORT = "enable_image_export"
        private const val KEY_ENABLE_VIDEO_EXPORT = "enable_video_export"
        private const val KEY_IMAGE_EXPORT_PATH = "image_export_path"
        private const val KEY_JSON_EXPORT_PATH = "json_export_path"

        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val defaults = AppConfig()

    private val apiKeyStore = EncryptedApiKeyStore(
        encryptedPrefs = encryptedPrefs,
        apiKeyPreferenceKey = KEY_API_KEY
    )

    /**
     * 保存配置
     */
    fun saveConfig(config: AppConfig) {
        saveNonSensitiveConfig(config)
        saveApiKey(config.apiKey)
    }

    /**
     * 保存除 API Key 外的普通配置。
     */
    fun saveNonSensitiveConfig(config: AppConfig) {
        encryptedPrefs.edit().apply {
            putString(KEY_API_BASE_URL, config.apiBaseUrl)
            putString(KEY_VLM_MODEL, config.vlmModel)
            putString(KEY_LLM_MODEL, config.llmModel)
            putInt(KEY_VLM_INTERVAL, config.vlmIntervalSeconds)
            putBoolean(KEY_USE_GPU, config.useGpu)
            putString(KEY_SELECTED_MODEL, config.selectedModel)
            putBoolean(KEY_ENABLE_LLM_FUSION, config.enableLlmFusion)
            putBoolean(KEY_ENABLE_JSON_EXPORT, config.enableJsonExport)
            putBoolean(KEY_ENABLE_IMAGE_EXPORT, config.enableImageExport)
            putBoolean(KEY_ENABLE_VIDEO_EXPORT, config.enableVideoExport)
            apply()
        }
    }

    /**
     * 加载配置
     */
    fun loadConfig(): AppConfig {
        return loadNonSensitiveConfig().withApiKey(getApiKey())
    }

    /**
     * 加载不包含 API Key 的普通配置。
     */
    fun loadNonSensitiveConfig(): AppConfig {
        return AppConfig(
            apiBaseUrl = encryptedPrefs.getString(KEY_API_BASE_URL, defaults.apiBaseUrl)
                ?: defaults.apiBaseUrl,
            apiKey = "",
            vlmModel = encryptedPrefs.getString(KEY_VLM_MODEL, defaults.vlmModel)
                ?: defaults.vlmModel,
            llmModel = encryptedPrefs.getString(KEY_LLM_MODEL, defaults.llmModel)
                ?: defaults.llmModel,
            vlmIntervalSeconds = encryptedPrefs.getInt(KEY_VLM_INTERVAL, AppConfig.DEFAULT_VLM_INTERVAL),
            useGpu = encryptedPrefs.getBoolean(KEY_USE_GPU, defaults.useGpu),
            selectedModel = encryptedPrefs.getString(KEY_SELECTED_MODEL, defaults.selectedModel)
                ?: defaults.selectedModel,
            enableLlmFusion = encryptedPrefs.getBoolean(KEY_ENABLE_LLM_FUSION, defaults.enableLlmFusion),
            enableJsonExport = encryptedPrefs.getBoolean(KEY_ENABLE_JSON_EXPORT, defaults.enableJsonExport),
            enableImageExport = encryptedPrefs.getBoolean(KEY_ENABLE_IMAGE_EXPORT, defaults.enableImageExport),
            enableVideoExport = encryptedPrefs.getBoolean(KEY_ENABLE_VIDEO_EXPORT, defaults.enableVideoExport)
        )
    }

    /**
     * 保存 API Key。
     */
    fun saveApiKey(apiKey: String) {
        apiKeyStore.save(apiKey)
    }

    /**
     * 更新 API Key。
     */
    fun updateApiKey(apiKey: String) {
        apiKeyStore.save(apiKey)
    }

    /**
     * 获取 API Key（用于网络请求）
     */
    fun getApiKey(): String {
        return apiKeyStore.get()
    }

    /**
     * 清除 API Key，但保留其他普通配置。
     */
    fun clearApiKey() {
        apiKeyStore.clear()
    }

    /**
     * 是否已经保存 API Key。
     */
    fun hasApiKey(): Boolean {
        return apiKeyStore.hasValue()
    }

    /**
     * 清除所有配置
     */
    fun clearConfig() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * 检查是否已配置 API
     */
    fun isConfigured(): Boolean {
        return loadConfig().isValid()
    }

    /**
     * 获取图片导出路径
     */
    fun getImageExportPath(): String {
        return encryptedPrefs.getString(KEY_IMAGE_EXPORT_PATH, "Pictures") ?: "Pictures"
    }

    /**
     * 设置图片导出路径
     */
    fun setImageExportPath(path: String) {
        encryptedPrefs.edit().putString(KEY_IMAGE_EXPORT_PATH, path).apply()
    }

    /**
     * 获取 JSON 导出路径
     */
    fun getJsonExportPath(): String {
        return encryptedPrefs.getString(KEY_JSON_EXPORT_PATH, "YOLO_Export/JSON") ?: "YOLO_Export/JSON"
    }

    /**
     * 设置 JSON 导出路径
     */
    fun setJsonExportPath(path: String) {
        encryptedPrefs.edit().putString(KEY_JSON_EXPORT_PATH, path).apply()
    }
}

package com.yolo.openiris.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 配置管理器
 * 使用 EncryptedSharedPreferences 安全存储敏感配置
 */
class ConfigManager private constructor(context: Context) {

    companion object {
        private const val TAG = "ConfigManager"
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
        
        // 摄像头分辨率配置
        private const val KEY_CAMERA_RESOLUTION_WIDTH = "camera_resolution_width"
        private const val KEY_CAMERA_RESOLUTION_HEIGHT = "camera_resolution_height"
        private const val KEY_CUSTOM_RESOLUTIONS = "custom_resolutions"
        private const val KEY_SHOW_CAPTURE_PREVIEW = "show_capture_preview"
        private const val KEY_CAPTURE_INTERVAL = "capture_interval_seconds"

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

    private val encryptedPrefs: SharedPreferences
    val isEncryptionAvailable: Boolean

    init {
        var encrypted = true
        val prefs: SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "加密存储初始化失败，当前会话已禁用敏感字段落盘", e)
            encrypted = false
            object : SharedPreferences {
                private val noopEditor = object : SharedPreferences.Editor {
                    override fun putString(key: String?, value: String?) = this
                    override fun putStringSet(key: String?, values: MutableSet<String>?) = this
                    override fun putInt(key: String?, value: Int) = this
                    override fun putLong(key: String?, value: Long) = this
                    override fun putFloat(key: String?, value: Float) = this
                    override fun putBoolean(key: String?, value: Boolean) = this
                    override fun remove(key: String?) = this
                    override fun clear() = this
                    override fun commit() = true
                    override fun apply() {}
                }

                override fun getAll(): Map<String, *> = emptyMap<String, Any>()
                override fun getString(key: String?, defValue: String?) = defValue
                override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
                override fun getInt(key: String?, defValue: Int) = defValue
                override fun getLong(key: String?, defValue: Long) = defValue
                override fun getFloat(key: String?, defValue: Float) = defValue
                override fun getBoolean(key: String?, defValue: Boolean) = defValue
                override fun contains(key: String?) = false
                override fun edit() = noopEditor
                override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            }
        }
        encryptedPrefs = prefs
        isEncryptionAvailable = encrypted
    }

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
            // 摄像头分辨率配置
            putInt(KEY_CAMERA_RESOLUTION_WIDTH, config.cameraResolutionWidth)
            putInt(KEY_CAMERA_RESOLUTION_HEIGHT, config.cameraResolutionHeight)
            putString(KEY_CUSTOM_RESOLUTIONS, config.customResolutions.joinToString(","))
            putBoolean(KEY_SHOW_CAPTURE_PREVIEW, config.showCapturePreview)
            putFloat(KEY_CAPTURE_INTERVAL, config.captureIntervalSeconds)
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
        val customResolutionsStr = encryptedPrefs.getString(KEY_CUSTOM_RESOLUTIONS, "") ?: ""
        val customResolutions = if (customResolutionsStr.isBlank()) {
            emptyList()
        } else {
            customResolutionsStr.split(",").filter { it.isNotBlank() }
        }
        
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
            enableVideoExport = encryptedPrefs.getBoolean(KEY_ENABLE_VIDEO_EXPORT, defaults.enableVideoExport),
            // 摄像头分辨率配置
            cameraResolutionWidth = encryptedPrefs.getInt(KEY_CAMERA_RESOLUTION_WIDTH, AppConfig.DEFAULT_CAMERA_WIDTH),
            cameraResolutionHeight = encryptedPrefs.getInt(KEY_CAMERA_RESOLUTION_HEIGHT, AppConfig.DEFAULT_CAMERA_HEIGHT),
            customResolutions = customResolutions,
            showCapturePreview = encryptedPrefs.getBoolean(KEY_SHOW_CAPTURE_PREVIEW, false),
            captureIntervalSeconds = encryptedPrefs.getFloat(KEY_CAPTURE_INTERVAL, AppConfig.DEFAULT_CAPTURE_INTERVAL)
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
     * 清除所有配置（保留 AI Provider 密钥）
     */
    fun clearConfig() {
        encryptedPrefs.edit().apply {
            remove(KEY_API_BASE_URL)
            remove(KEY_API_KEY)
            remove(KEY_VLM_MODEL)
            remove(KEY_LLM_MODEL)
            remove(KEY_VLM_INTERVAL)
            remove(KEY_USE_GPU)
            remove(KEY_SELECTED_MODEL)
            remove(KEY_ENABLE_LLM_FUSION)
            remove(KEY_ENABLE_JSON_EXPORT)
            remove(KEY_ENABLE_IMAGE_EXPORT)
            remove(KEY_ENABLE_VIDEO_EXPORT)
            remove(KEY_IMAGE_EXPORT_PATH)
            remove(KEY_JSON_EXPORT_PATH)
            apply()
        }
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

    /**
     * 保存 AI Provider 的 API Key（加密存储）
     * 回退模式下会记录警告日志（明文存储）
     */
    fun saveAiProviderApiKey(providerId: String, apiKey: String) {
        if (!isEncryptionAvailable && apiKey.isNotBlank()) {
            Log.w(TAG, "加密存储不可用，已阻止敏感 API Key 写入 SharedPreferences")
        }
        val key = "ai_provider_key_$providerId"
        encryptedPrefs.edit().apply {
            if (apiKey.isBlank()) {
                remove(key)
            } else {
                putString(key, apiKey.trim())
            }
            apply()
        }
    }

    /**
     * 获取 AI Provider 的 API Key
     */
    fun getAiProviderApiKey(providerId: String): String {
        val key = "ai_provider_key_$providerId"
        return encryptedPrefs.getString(key, "")?.trim().orEmpty()
    }

    /**
     * 删除 AI Provider 的 API Key
     */
    fun removeAiProviderApiKey(providerId: String) {
        val key = "ai_provider_key_$providerId"
        encryptedPrefs.edit().remove(key).apply()
    }
}

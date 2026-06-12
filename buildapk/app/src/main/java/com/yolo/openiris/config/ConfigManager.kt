package com.yolo.openiris.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 閰嶇疆绠$悊鍣?
 * 浣跨敤 EncryptedSharedPreferences 瀹夊叏瀛樺偍鏁忔劅閰嶇疆
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
        
        // 鎽勫儚澶村垎杈ㄧ巼閰嶇疆
        private const val KEY_CAMERA_RESOLUTION_WIDTH = "camera_resolution_width"
        private const val KEY_CAMERA_RESOLUTION_HEIGHT = "camera_resolution_height"
        private const val KEY_CUSTOM_RESOLUTIONS = "custom_resolutions"
        private const val KEY_SHOW_CAPTURE_PREVIEW = "show_capture_preview"

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
            Log.e(TAG, "鍔犲瘑瀛樺偍鍒濆鍖栧け璐ワ紝褰撳墠浼氳瘽宸茬鐢ㄦ晱鎰熷瓧娈佃惤鐩?, e)
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
     * 淇濆瓨閰嶇疆
     */
    fun saveConfig(config: AppConfig) {
        saveNonSensitiveConfig(config)
        saveApiKey(config.apiKey)
    }

    /**
     * 淇濆瓨闄?API Key 澶栫殑鏅€氶厤缃€?
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
            // 鎽勫儚澶村垎杈ㄧ巼閰嶇疆
            putInt(KEY_CAMERA_RESOLUTION_WIDTH, config.cameraResolutionWidth)
            putInt(KEY_CAMERA_RESOLUTION_HEIGHT, config.cameraResolutionHeight)
            putString(KEY_CUSTOM_RESOLUTIONS, config.customResolutions.joinToString(","))
            putBoolean(KEY_SHOW_CAPTURE_PREVIEW, config.showCapturePreview)
            apply()
        }
    }

    /**
     * 鍔犺浇閰嶇疆
     */
    fun loadConfig(): AppConfig {
        return loadNonSensitiveConfig().withApiKey(getApiKey())
    }

    /**
     * 鍔犺浇涓嶅寘鍚?API Key 鐨勬櫘閫氶厤缃€?
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
            // 鎽勫儚澶村垎杈ㄧ巼閰嶇疆
            cameraResolutionWidth = encryptedPrefs.getInt(KEY_CAMERA_RESOLUTION_WIDTH, AppConfig.DEFAULT_CAMERA_WIDTH),
            cameraResolutionHeight = encryptedPrefs.getInt(KEY_CAMERA_RESOLUTION_HEIGHT, AppConfig.DEFAULT_CAMERA_HEIGHT),
            customResolutions = customResolutions,
            showCapturePreview = encryptedPrefs.getBoolean(KEY_SHOW_CAPTURE_PREVIEW, false)
        )
    }

    /**
     * 淇濆瓨 API Key銆?
     */
    fun saveApiKey(apiKey: String) {
        apiKeyStore.save(apiKey)
    }

    /**
     * 鏇存柊 API Key銆?
     */
    fun updateApiKey(apiKey: String) {
        apiKeyStore.save(apiKey)
    }

    /**
     * 鑾峰彇 API Key锛堢敤浜庣綉缁滆姹傦級
     */
    fun getApiKey(): String {
        return apiKeyStore.get()
    }

    /**
     * 娓呴櫎 API Key锛屼絾淇濈暀鍏朵粬鏅€氶厤缃€?
     */
    fun clearApiKey() {
        apiKeyStore.clear()
    }

    /**
     * 鏄惁宸茬粡淇濆瓨 API Key銆?
     */
    fun hasApiKey(): Boolean {
        return apiKeyStore.hasValue()
    }

    /**
     * 娓呴櫎鎵€鏈夐厤缃紙淇濈暀 AI Provider 瀵嗛挜锛?
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
     * 妫€鏌ユ槸鍚﹀凡閰嶇疆 API
     */
    fun isConfigured(): Boolean {
        return loadConfig().isValid()
    }

    /**
     * 鑾峰彇鍥剧墖瀵煎嚭璺緞
     */
    fun getImageExportPath(): String {
        return encryptedPrefs.getString(KEY_IMAGE_EXPORT_PATH, "Pictures") ?: "Pictures"
    }

    /**
     * 璁剧疆鍥剧墖瀵煎嚭璺緞
     */
    fun setImageExportPath(path: String) {
        encryptedPrefs.edit().putString(KEY_IMAGE_EXPORT_PATH, path).apply()
    }

    /**
     * 鑾峰彇 JSON 瀵煎嚭璺緞
     */
    fun getJsonExportPath(): String {
        return encryptedPrefs.getString(KEY_JSON_EXPORT_PATH, "YOLO_Export/JSON") ?: "YOLO_Export/JSON"
    }

    /**
     * 璁剧疆 JSON 瀵煎嚭璺緞
     */
    fun setJsonExportPath(path: String) {
        encryptedPrefs.edit().putString(KEY_JSON_EXPORT_PATH, path).apply()
    }

    /**
     * 淇濆瓨 AI Provider 鐨?API Key锛堝姞瀵嗗瓨鍌級
     * 鍥為€€妯″紡涓嬩細璁板綍璀﹀憡鏃ュ織锛堟槑鏂囧瓨鍌級
     */
    fun saveAiProviderApiKey(providerId: String, apiKey: String) {
        if (!isEncryptionAvailable && apiKey.isNotBlank()) {
            Log.w(TAG, "鍔犲瘑瀛樺偍涓嶅彲鐢紝宸查樆姝㈡晱鎰?API Key 鍐欏叆 SharedPreferences")
            return
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
     * 鑾峰彇 AI Provider 鐨?API Key
     */
    fun getAiProviderApiKey(providerId: String): String {
        val key = "ai_provider_key_$providerId"
        return encryptedPrefs.getString(key, "")?.trim().orEmpty()
    }

    /**
     * 鍒犻櫎 AI Provider 鐨?API Key
     */
    fun removeAiProviderApiKey(providerId: String) {
        val key = "ai_provider_key_$providerId"
        encryptedPrefs.edit().remove(key).apply()
    }
}

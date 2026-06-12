package com.yolo.openiris.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yolo.openiris.config.ConfigManager

/**
 * AI 模型配置持久化存储
 *
 * 提供商元数据存储在普通 SharedPreferences 中,
 * apiKey 单独通过 ConfigManager 的 EncryptedSharedPreferences 加密存储。
 */
class AiModelConfigStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val configManager = ConfigManager.getInstance(context)
    private val gson = Gson()

    // 提供商缓存,避免每次都从 SharedPreferences 读取
    @Volatile
    private var cachedProviders: List<AiProvider>? = null

    companion object {
        private const val TAG = "AiModelConfigStore"
        private const val PREFS_NAME = "ai_model_config"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_DEFAULT_MODEL_ID = "default_model_id"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_CALL_INTERVAL_SECONDS = "call_interval_seconds"
        private const val KEY_VISUAL_PROMPT = "visual_recognition_prompt"
        private const val KEY_SUMMARY_PROMPT = "detection_summary_prompt"

        // 默认视觉识别提示词
        const val DEFAULT_VISUAL_PROMPT = """# 视觉识别任务

请识别图片中的物体,以JSON格式返回结果。

## 输出格式

```json
{
  "objects": [
    {
      "name": "物体英文名",
      "name_cn": "物体中文名",
      "count": 数量,
      "confidence": 置信度(0-1)
    }
  ],
  "summary": "简要描述"
}
```

## 要求
- 尽可能识别所有物体
- 置信度低于0.3的物体可以忽略
- 保持输出格式严格符合JSON"""

        // 默认检测总结提示词
        const val DEFAULT_SUMMARY_PROMPT = """# 检测总结任务

请根据以下YOLO检测结果,生成简要的检测总结。

## YOLO检测结果
{yolo_results}

## 输出格式

```json
{
  "objects": [
    {
      "name": "物体英文名",
      "name_cn": "物体中文名",
      "count": 数量,
      "confidence": 平均置信度
    }
  ],
  "summary": "一句话总结检测结果"
}
```

## 要求
- 合并YOLO检测到的物体
- 补充识别画面中可能遗漏的物体
- 保持输出格式严格符合JSON"""
    }

    /**
     * 保存所有提供商配置(apiKey 单独加密存储)
     */
    fun saveProviders(providers: List<AiProvider>) {
        // 将 apiKey 从 provider 中剥离,单独加密存储
        providers.forEach { provider ->
            configManager.saveAiProviderApiKey(provider.id, provider.apiKey)
        }
        // 存储不含 apiKey 的 provider 元数据
        val metadataList = providers.map { it.copy(apiKey = "") }
        val json = gson.toJson(metadataList)
        prefs.edit().putString(KEY_PROVIDERS, json).apply()
        invalidateCache()
    }

    /**
     * 加载所有提供商配置(从加密存储恢复 apiKey,含旧格式迁移)
     */
    fun loadProviders(): List<AiProvider> {
        // 如果有缓存,直接返回
        cachedProviders?.let {
            Log.d(TAG, "Returning cached providers: ${it.size}")
            return it
        }

        val json = prefs.getString(KEY_PROVIDERS, null)
        Log.d(TAG, "Loading providers from SharedPreferences, json length: ${json?.length ?: 0}")
        
        if (json == null) {
            Log.w(TAG, "No providers found in SharedPreferences")
            return emptyList()
        }
        
        return try {
            val type = object : TypeToken<List<AiProvider>>() {}.type
            val metadataList: List<AiProvider> = gson.fromJson(json, type) ?: emptyList()
            Log.d(TAG, "Parsed ${metadataList.size} providers from JSON")
            
            var needMigration = false

            val result = metadataList.map { provider ->
                var apiKey = configManager.getAiProviderApiKey(provider.id)
            if (com.yolo.openiris.BuildConfig.DEBUG) {
                Log.d(TAG, "Provider '${provider.name}' (ID: ${provider.id}): apiKey present=${apiKey.isNotBlank()}")
            }
                
                // 旧格式迁移:加密存储为空但 JSON 中有旧 key
                if (apiKey.isBlank() && provider.apiKey.isNotBlank()) {
                    if (com.yolo.openiris.BuildConfig.DEBUG) {
                        Log.w(TAG, "Migrating apiKey for provider: ${provider.id}")
                    }
                    configManager.saveAiProviderApiKey(provider.id, provider.apiKey)
                    apiKey = provider.apiKey
                    needMigration = true
                }
                // 确保新字段有默认值
                provider.copy(
                    apiKey = apiKey,
                    apiFormat = provider.apiFormat,
                    apiPath = provider.apiPath.ifBlank { "/chat/completions" },
                    useResponseApi = provider.useResponseApi,
                    models = provider.models.map { model ->
                        model.copy(
                            enableReasoning = model.enableReasoning,
                            assignedTasks = model.assignedTasks,
                            customHeaders = model.customHeaders,
                            customBody = model.customBody
                        )
                    }
                )
            }

            // 迁移后清理明文
            if (needMigration) {
                val cleaned = result.map { it.copy(apiKey = "") }
                saveRawProviders(cleaned)
            }

            // 缓存结果
            cachedProviders = result
            Log.d(TAG, "Successfully loaded and cached ${result.size} providers")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load providers", e)
            // 不清除数据,返回空列表但保留原始数据
            emptyList()
        }
    }

    /**
     * 清除缓存(在数据变更后调用)
     */
    private fun invalidateCache() {
        cachedProviders = null
    }

    /**
     * 添加提供商(apiKey 加密存储)
     */
    @Synchronized
    fun addProvider(provider: AiProvider) {
        val providers = loadRawProviders().toMutableList()
        // 避免重复添加
        providers.removeAll { it.id == provider.id }
        providers.add(provider.copy(apiKey = ""))
        saveRawProviders(providers)
        configManager.saveAiProviderApiKey(provider.id, provider.apiKey)
        invalidateCache()
    }

    /**
     * 更新提供商(apiKey 加密存储)
     */
    @Synchronized
    fun updateProvider(provider: AiProvider) {
        val providers = loadRawProviders().toMutableList()
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            providers[index] = provider.copy(apiKey = "")
            saveRawProviders(providers)
            configManager.saveAiProviderApiKey(provider.id, provider.apiKey)
            invalidateCache()
        }
    }

    /**
     * 删除提供商(同时删除加密的 apiKey)
     */
    @Synchronized
    fun deleteProvider(providerId: String) {
        val providers = loadRawProviders().toMutableList()
        providers.removeAll { it.id == providerId }
        saveRawProviders(providers)
        configManager.removeAiProviderApiKey(providerId)
        invalidateCache()
    }

    /**
     * 获取所有已启用的模型
     */
    fun getEnabledModels(): List<AiModel> {
        return loadProviders()
            .filter { it.isEnabled }
            .flatMap { provider ->
                provider.models.filter { it.isEnabled }.map { model ->
                    model.copy(providerId = provider.id)
                }
            }
            .sortedBy { it.priority }
    }

    /**
     * 获取默认模型
     */
    fun getDefaultModel(): AiModel? {
        val defaultModelId = prefs.getString(KEY_DEFAULT_MODEL_ID, null)
        val models = getEnabledModels()

        return if (defaultModelId != null) {
            models.find { it.id == defaultModelId } ?: models.firstOrNull()
        } else {
            models.firstOrNull()
        }
    }

    /**
     * 设置默认模型
     */
    fun setDefaultModel(modelId: String?) {
        prefs.edit().putString(KEY_DEFAULT_MODEL_ID, modelId).apply()
    }

    /**
     * AI 是否启用
     */
    fun isAiEnabled(): Boolean {
        return prefs.getBoolean(KEY_AI_ENABLED, false)
    }

    /**
     * 设置 AI 启用状态
     */
    fun setAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    /**
     * 获取 AI 调用间隔(秒)
     */
    fun getCallIntervalSeconds(): Int {
        return prefs.getInt(KEY_CALL_INTERVAL_SECONDS, 5)
    }

    /**
     * 设置 AI 调用间隔
     */
    fun setCallIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CALL_INTERVAL_SECONDS, seconds).apply()
    }

    /**
     * 获取视觉识别提示词
     */
    fun getVisualRecognitionPrompt(): String {
        return prefs.getString(KEY_VISUAL_PROMPT, DEFAULT_VISUAL_PROMPT) ?: DEFAULT_VISUAL_PROMPT
    }

    /**
     * 设置视觉识别提示词
     */
    fun setVisualRecognitionPrompt(prompt: String) {
        prefs.edit().putString(KEY_VISUAL_PROMPT, prompt).apply()
    }

    /**
     * 获取检测总结提示词
     */
    fun getDetectionSummaryPrompt(): String {
        return prefs.getString(KEY_SUMMARY_PROMPT, DEFAULT_SUMMARY_PROMPT) ?: DEFAULT_SUMMARY_PROMPT
    }

    /**
     * 设置检测总结提示词
     */
    fun setDetectionSummaryPrompt(prompt: String) {
        prefs.edit().putString(KEY_SUMMARY_PROMPT, prompt).apply()
    }

    /**
     * 根据提供商 ID 获取提供商(含加密 apiKey)
     */
    fun getProvider(providerId: String): AiProvider? {
        if (com.yolo.openiris.BuildConfig.DEBUG) {
            Log.d(TAG, "getProvider called with ID: '$providerId'")
        }
        val providers = loadProviders()
        if (com.yolo.openiris.BuildConfig.DEBUG) {
            Log.d(TAG, "Loaded ${providers.size} providers, searching for ID: '$providerId'")
            providers.forEach { p ->
                Log.d(TAG, "  Provider: '${p.name}', ID: '${p.id}'")
            }
        }
        val found = providers.find { it.id == providerId }
        if (com.yolo.openiris.BuildConfig.DEBUG) {
            Log.d(TAG, "Found provider: ${found?.name ?: "null"}")
        }
        return found
    }

    /**
     * 为提供商添加模型
     */
    @Synchronized
    fun addModelToProvider(providerId: String, model: AiModel) {
        val providers = loadRawProviders().toMutableList()
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            val provider = providers[providerIndex]
            val updatedModels = provider.models.toMutableList()
            updatedModels.add(model)
            providers[providerIndex] = provider.copy(models = updatedModels)
            saveRawProviders(providers)
            invalidateCache()
        }
    }

    /**
     * 从提供商删除模型
     */
    @Synchronized
    fun removeModelFromProvider(providerId: String, modelId: String) {
        val providers = loadRawProviders().toMutableList()
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            val provider = providers[providerIndex]
            val updatedModels = provider.models.toMutableList()
            updatedModels.removeAll { it.id == modelId }
            providers[providerIndex] = provider.copy(models = updatedModels)
            saveRawProviders(providers)
            invalidateCache()
        }
    }

    /**
     * 更新模型
     */
    @Synchronized
    fun updateModel(providerId: String, model: AiModel) {
        val providers = loadRawProviders().toMutableList()
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            val provider = providers[providerIndex]
            val updatedModels = provider.models.toMutableList()
            val modelIndex = updatedModels.indexOfFirst { it.id == model.id }
            if (modelIndex >= 0) {
                updatedModels[modelIndex] = model
                providers[providerIndex] = provider.copy(models = updatedModels)
                saveRawProviders(providers)
                invalidateCache()
            }
        }
    }

    /**
     * 从存储加载原始 provider 元数据(不含 apiKey)
     */
    private fun loadRawProviders(): List<AiProvider> {
        val json = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AiProvider>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load raw providers", e)
            emptyList()
        }
    }

    /**
     * 保存原始 provider 元数据(不含 apiKey)
     */
    private fun saveRawProviders(providers: List<AiProvider>) {
        val json = gson.toJson(providers)
        prefs.edit().putString(KEY_PROVIDERS, json).apply()
    }
}

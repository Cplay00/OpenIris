package com.yolo.openiris.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * AI 模型配置持久化存储
 */
class AiModelConfigStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "ai_model_config"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_DEFAULT_MODEL_ID = "default_model_id"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_CALL_INTERVAL_SECONDS = "call_interval_seconds"
    }

    /**
     * 保存所有提供商配置
     */
    fun saveProviders(providers: List<AiProvider>) {
        val json = gson.toJson(providers)
        prefs.edit().putString(KEY_PROVIDERS, json).apply()
    }

    /**
     * 加载所有提供商配置
     */
    fun loadProviders(): List<AiProvider> {
        val json = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AiProvider>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 添加提供商
     */
    fun addProvider(provider: AiProvider) {
        val providers = loadProviders().toMutableList()
        providers.add(provider)
        saveProviders(providers)
    }

    /**
     * 更新提供商
     */
    fun updateProvider(provider: AiProvider) {
        val providers = loadProviders().toMutableList()
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            providers[index] = provider
            saveProviders(providers)
        }
    }

    /**
     * 删除提供商
     */
    fun deleteProvider(providerId: String) {
        val providers = loadProviders().toMutableList()
        providers.removeAll { it.id == providerId }
        saveProviders(providers)
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
     * 获取 AI 调用间隔（秒）
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
     * 根据提供商 ID 获取提供商
     */
    fun getProvider(providerId: String): AiProvider? {
        return loadProviders().find { it.id == providerId }
    }

    /**
     * 为提供商添加模型
     */
    fun addModelToProvider(providerId: String, model: AiModel) {
        val providers = loadProviders().toMutableList()
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            val provider = providers[providerIndex]
            val updatedModels = provider.models.toMutableList()
            updatedModels.add(model)
            providers[providerIndex] = provider.copy(models = updatedModels)
            saveProviders(providers)
        }
    }

    /**
     * 从提供商删除模型
     */
    fun removeModelFromProvider(providerId: String, modelId: String) {
        val providers = loadProviders().toMutableList()
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            val provider = providers[providerIndex]
            val updatedModels = provider.models.toMutableList()
            updatedModels.removeAll { it.id == modelId }
            providers[providerIndex] = provider.copy(models = updatedModels)
            saveProviders(providers)
        }
    }

    /**
     * 更新模型
     */
    fun updateModel(providerId: String, model: AiModel) {
        val providers = loadProviders().toMutableList()
        val providerIndex = providers.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            val provider = providers[providerIndex]
            val updatedModels = provider.models.toMutableList()
            val modelIndex = updatedModels.indexOfFirst { it.id == model.id }
            if (modelIndex >= 0) {
                updatedModels[modelIndex] = model
                providers[providerIndex] = provider.copy(models = updatedModels)
                saveProviders(providers)
            }
        }
    }
}

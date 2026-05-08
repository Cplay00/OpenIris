package com.yolo.openiris.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * AI 模型管理器（单例）
 */
class AiModelManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AiModelManager"

        @Volatile
        private var instance: AiModelManager? = null

        fun getInstance(context: Context): AiModelManager {
            return instance ?: synchronized(this) {
                instance ?: AiModelManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    val configStore = AiModelConfigStore(context)
    private val apiClient = AiApiClient()

    /**
     * 获取所有已启用的模型
     */
    fun getEnabledModels(): List<AiModel> {
        return configStore.getEnabledModels()
    }

    /**
     * 获取默认模型
     */
    fun getDefaultModel(): AiModel? {
        return configStore.getDefaultModel()
    }

    /**
     * AI 是否启用
     */
    fun isAiEnabled(): Boolean {
        return configStore.isAiEnabled()
    }

    /**
     * 设置 AI 启用状态
     */
    fun setAiEnabled(enabled: Boolean) {
        configStore.setAiEnabled(enabled)
    }

    /**
     * 获取调用间隔
     */
    fun getCallIntervalSeconds(): Int {
        return configStore.getCallIntervalSeconds()
    }

    /**
     * 设置调用间隔
     */
    fun setCallIntervalSeconds(seconds: Int) {
        configStore.setCallIntervalSeconds(seconds)
    }

    /**
     * 调用指定模型（纯文本）
     */
    suspend fun callModel(
        model: AiModel,
        prompt: String,
        systemPrompt: String? = null
    ): AiResult = withContext(Dispatchers.IO) {
        val provider = configStore.getProvider(model.providerId)
            ?: return@withContext AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "提供商不存在"
            )

        apiClient.callModel(provider, model, prompt, systemPrompt)
    }

    /**
     * 调用指定模型（带图片）
     */
    suspend fun callModelWithImage(
        model: AiModel,
        prompt: String,
        imageBase64: String,
        systemPrompt: String? = null
    ): AiResult = withContext(Dispatchers.IO) {
        val provider = configStore.getProvider(model.providerId)
            ?: return@withContext AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "提供商不存在"
            )

        apiClient.callModelWithImage(provider, model, prompt, imageBase64, systemPrompt)
    }

    /**
     * 调用默认模型（纯文本）
     */
    suspend fun callDefaultModel(
        prompt: String,
        systemPrompt: String? = null
    ): AiResult {
        val model = getDefaultModel()
            ?: return AiResult.failure(
                modelId = "none",
                modelName = "None",
                error = "没有可用的默认模型"
            )

        return callModel(model, prompt, systemPrompt)
    }

    /**
     * 调用默认模型（带图片）
     */
    suspend fun callDefaultModelWithImage(
        prompt: String,
        imageBase64: String,
        systemPrompt: String? = null
    ): AiResult {
        val model = getDefaultModel()
            ?: return AiResult.failure(
                modelId = "none",
                modelName = "None",
                error = "没有可用的默认模型"
            )

        return callModelWithImage(model, prompt, imageBase64, systemPrompt)
    }

    /**
     * 使用故障切换机制调用模型（纯文本）
     */
    suspend fun callWithFallback(
        prompt: String,
        systemPrompt: String? = null,
        onError: ((String) -> Unit)? = null
    ): AiResult {
        val models = getEnabledModels()
        if (models.isEmpty()) {
            return AiResult.failure(
                modelId = "none",
                modelName = "None",
                error = "没有可用的模型"
            )
        }

        // 优先使用默认模型
        val defaultModel = getDefaultModel()
        val orderedModels = if (defaultModel != null) {
            listOf(defaultModel) + models.filter { it.id != defaultModel.id }
        } else {
            models
        }

        for (model in orderedModels) {
            try {
                val result = callModel(model, prompt, systemPrompt)
                if (result.success) {
                    return result
                } else {
                    onError?.invoke("模型 ${model.displayName} 调用失败: ${result.error}")
                }
            } catch (e: Exception) {
                onError?.invoke("模型 ${model.displayName} 调用异常: ${e.message}")
                Log.e(TAG, "Model call failed", e)
            }
        }

        return AiResult.failure(
            modelId = "all",
            modelName = "All Models",
            error = "所有模型均调用失败"
        )
    }

    /**
     * 使用故障切换机制调用模型（带图片）
     */
    suspend fun callWithFallbackAndImage(
        prompt: String,
        imageBase64: String,
        systemPrompt: String? = null,
        onError: ((String) -> Unit)? = null
    ): AiResult {
        val models = getEnabledModels().filter { it.hasVision }
        if (models.isEmpty()) {
            return AiResult.failure(
                modelId = "none",
                modelName = "None",
                error = "没有支持视觉的可用模型"
            )
        }

        // 优先使用默认模型（如果支持视觉）
        val defaultModel = getDefaultModel()?.takeIf { it.hasVision }
        val orderedModels = if (defaultModel != null) {
            listOf(defaultModel) + models.filter { it.id != defaultModel.id }
        } else {
            models
        }

        for (model in orderedModels) {
            try {
                val result = callModelWithImage(model, prompt, imageBase64, systemPrompt)
                if (result.success) {
                    return result
                } else {
                    onError?.invoke("模型 ${model.displayName} 调用失败: ${result.error}")
                }
            } catch (e: Exception) {
                onError?.invoke("模型 ${model.displayName} 调用异常: ${e.message}")
                Log.e(TAG, "Model call failed", e)
            }
        }

        return AiResult.failure(
            modelId = "all",
            modelName = "All Models",
            error = "所有支持视觉的模型均调用失败"
        )
    }

    /**
     * 获取提供商的模型列表
     */
    suspend fun fetchModelList(provider: AiProvider): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            apiClient.fetchModelList(provider)
        }
    }

    /**
     * Bitmap 转 Base64
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 保存提供商
     */
    fun saveProvider(provider: AiProvider) {
        configStore.addProvider(provider)
    }

    /**
     * 更新提供商
     */
    fun updateProvider(provider: AiProvider) {
        configStore.updateProvider(provider)
    }

    /**
     * 删除提供商
     */
    fun deleteProvider(providerId: String) {
        configStore.deleteProvider(providerId)
    }

    /**
     * 添加模型到提供商
     */
    fun addModelToProvider(providerId: String, model: AiModel) {
        configStore.addModelToProvider(providerId, model)
    }

    /**
     * 从提供商删除模型
     */
    fun removeModelFromProvider(providerId: String, modelId: String) {
        configStore.removeModelFromProvider(providerId, modelId)
    }

    /**
     * 更新模型
     */
    fun updateModel(providerId: String, model: AiModel) {
        configStore.updateModel(providerId, model)
    }

    /**
     * 设置默认模型
     */
    fun setDefaultModel(modelId: String?) {
        configStore.setDefaultModel(modelId)
    }

    /**
     * 获取所有提供商
     */
    fun getProviders(): List<AiProvider> {
        return configStore.loadProviders()
    }

    /**
     * 根据 ID 获取提供商
     */
    fun getProvider(providerId: String): AiProvider? {
        return configStore.getProvider(providerId)
    }
}

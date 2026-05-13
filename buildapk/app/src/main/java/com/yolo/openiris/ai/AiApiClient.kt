package com.yolo.openiris.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.util.concurrent.TimeUnit

/**
 * 统一 AI API 客户端
 */
class AiApiClient {

    companion object {
        private const val TAG = "AiApiClient"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 30L

        // 默认系统提示词
        private const val DEFAULT_SYSTEM_PROMPT = """你是一个物体识别助手。请分析提供的图像或文本描述，识别出其中的物体。

请以JSON格式返回结果：
{
  "objects": [
    {"name": "object_name", "name_cn": "中文名称", "count": 1, "confidence": 0.95}
  ],
  "summary": "场景描述摘要"
}

要求：
1. name 为英文物体名称
2. name_cn 为中文翻译（如果知道的话）
3. count 为该物体出现的次数
4. confidence 为置信度（0-1之间的小数）
5. summary 为简短的场景描述"""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 构建请求头
     */
    private fun buildHeaders(provider: AiProvider, model: AiModel): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        
        // 根据 API 格式设置认证头
        when (provider.apiFormat) {
            ApiFormat.ANTHROPIC -> {
                headers["x-api-key"] = provider.apiKey
                headers["anthropic-version"] = "2023-06-01"
            }
            else -> {
                headers["Authorization"] = "Bearer ${provider.apiKey}"
            }
        }
        
        headers["Content-Type"] = "application/json"
        
        // 禁止覆盖的安全头列表
        val blockedHeaders = setOf(
            "authorization", "x-api-key", "host", "content-length", 
            "content-type", "transfer-encoding", "connection"
        )
        
        // 合并自定义 Headers（过滤危险头）
        model.customHeaders.forEach { (key, value) ->
            if (key.lowercase() !in blockedHeaders && key.isNotBlank() && value.isNotBlank()) {
                headers[key] = value
            } else {
                Log.w(TAG, "Blocked potentially dangerous header: $key")
            }
        }
        
        return headers
    }

    /**
     * 获取提供商的模型列表
     */
    fun fetchModelList(provider: AiProvider): Result<List<String>> {
        return try {
            val url = "${provider.getEffectiveBaseUrl()}/models"
            val headers = buildHeaders(provider, AiModel(providerId = provider.id, modelId = "", displayName = ""))
            
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
            
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonObject = JsonParser.parseString(body).asJsonObject
                    val dataArray = jsonObject.getAsJsonArray("data")
                    val models = mutableListOf<String>()
                    dataArray?.forEach { item ->
                        val modelId = item.asJsonObject.get("id")?.asString
                        if (modelId != null) {
                            models.add(modelId)
                        }
                    }
                    Result.success(models)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch model list", e)
            Result.failure(e)
        }
    }

    /**
     * 调用 AI 模型（纯文本）
     */
    fun callModel(
        provider: AiProvider,
        model: AiModel,
        prompt: String,
        systemPrompt: String? = null
    ): AiResult {
        val startTime = System.currentTimeMillis()

        // 输入验证
        if (provider.baseUrl.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "提供商 Base URL 为空",
                durationMs = 0
            )
        }
        if (model.modelId.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "模型 ID 为空",
                durationMs = 0
            )
        }
        if (prompt.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "提示词为空",
                durationMs = 0
            )
        }

        return try {
            val url = "${provider.getEffectiveBaseUrl()}${provider.getEffectiveApiPath()}"
            val headers = buildHeaders(provider, model)
            
            val requestBody = when (provider.apiFormat) {
                ApiFormat.ANTHROPIC -> buildAnthropicRequestBody(model, prompt, systemPrompt, false, null)
                else -> buildOpenAIRequestBody(model, prompt, systemPrompt, false, null)
            }

            val jsonBody = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder().url(url).post(jsonBody)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val result = parseResponse(body, provider.apiFormat)
                    AiResult.success(
                        modelId = model.id,
                        modelName = model.displayName,
                        content = result.first,
                        structuredOutput = result.second,
                        durationMs = duration
                    )
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    AiResult.failure(
                        modelId = model.id,
                        modelName = model.displayName,
                        error = "HTTP ${response.code}: $errorBody",
                        durationMs = duration
                    )
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Failed to call model", e)
            AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = e.message ?: "Unknown error",
                durationMs = duration
            )
        }
    }

    /**
     * 调用 AI 模型（带图片）
     */
    fun callModelWithImage(
        provider: AiProvider,
        model: AiModel,
        prompt: String,
        imageBase64: String,
        systemPrompt: String? = null
    ): AiResult {
        val startTime = System.currentTimeMillis()

        // 输入验证
        if (provider.baseUrl.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "提供商 Base URL 为空",
                durationMs = 0
            )
        }
        if (model.modelId.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "模型 ID 为空",
                durationMs = 0
            )
        }
        if (prompt.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "提示词为空",
                durationMs = 0
            )
        }
        if (imageBase64.isBlank()) {
            return AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = "图片数据为空",
                durationMs = 0
            )
        }

        return try {
            if (!model.hasVision) {
                return AiResult.failure(
                    modelId = model.id,
                    modelName = model.displayName,
                    error = "该模型不支持视觉输入",
                    durationMs = 0
                )
            }

            val url = "${provider.getEffectiveBaseUrl()}${provider.getEffectiveApiPath()}"
            val headers = buildHeaders(provider, model)
            
            val requestBody = when (provider.apiFormat) {
                ApiFormat.ANTHROPIC -> buildAnthropicRequestBody(model, prompt, systemPrompt, true, imageBase64)
                else -> buildOpenAIRequestBody(model, prompt, systemPrompt, true, imageBase64)
            }

            val jsonBody = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder().url(url).post(jsonBody)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val result = parseResponse(body, provider.apiFormat)
                    AiResult.success(
                        modelId = model.id,
                        modelName = model.displayName,
                        content = result.first,
                        structuredOutput = result.second,
                        durationMs = duration
                    )
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    AiResult.failure(
                        modelId = model.id,
                        modelName = model.displayName,
                        error = "HTTP ${response.code}: $errorBody",
                        durationMs = duration
                    )
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Failed to call model with image", e)
            AiResult.failure(
                modelId = model.id,
                modelName = model.displayName,
                error = e.message ?: "Unknown error",
                durationMs = duration
            )
        }
    }

    /**
     * 流式调用 AI 模型（纯文本）
     * @return Pair<完整内容, 错误信息?>
     */
    fun callModelStream(
        provider: AiProvider,
        model: AiModel,
        prompt: String,
        systemPrompt: String? = null,
        onToken: ((String) -> Unit)? = null
    ): Pair<String?, String?> {
        // 输入验证
        if (provider.baseUrl.isBlank()) {
            return Pair(null, "提供商 Base URL 为空")
        }
        if (model.modelId.isBlank()) {
            return Pair(null, "模型 ID 为空")
        }
        if (prompt.isBlank()) {
            return Pair(null, "提示词为空")
        }

        return try {
            val url = "${provider.getEffectiveBaseUrl()}${provider.getEffectiveApiPath()}"
            val headers = buildHeaders(provider, model)

            val requestBody = when (provider.apiFormat) {
                ApiFormat.ANTHROPIC -> buildAnthropicRequestBody(model, prompt, systemPrompt, false, null)
                else -> buildOpenAIRequestBody(model, prompt, systemPrompt, false, null)
            }.toMutableMap()

            // 添加流式标记
            requestBody["stream"] = true

            val jsonBody = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder().url(url).post(jsonBody)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return Pair(null, "HTTP ${response.code}: $errorBody")
                }

                val fullContent = StringBuilder()
                val source = response.body?.source() ?: return Pair(null, "Empty response body")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()

                        if (data == "[DONE]") {
                            break
                        }

                        try {
                            val jsonObject = JsonParser.parseString(data).asJsonObject
                            val choices = jsonObject.getAsJsonArray("choices")

                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                val content = delta?.get("content")?.asString

                                if (content != null) {
                                    fullContent.append(content)
                                    onToken?.invoke(content)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse stream chunk: $data", e)
                        }
                    }
                }

                Pair(fullContent.toString(), null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call model stream", e)
            Pair(null, e.message ?: "Unknown error")
        }
    }

    /**
     * 构建 OpenAI 兼容格式请求体
     */
    private fun buildOpenAIRequestBody(
        model: AiModel,
        prompt: String,
        systemPrompt: String?,
        withImage: Boolean,
        imageBase64: String?
    ): Map<String, Any> {
        val messages = mutableListOf<Map<String, Any>>()

        // 添加系统提示
        messages.add(mapOf(
            "role" to "system",
            "content" to (systemPrompt ?: DEFAULT_SYSTEM_PROMPT)
        ))

        // 添加用户消息
        if (withImage && imageBase64 != null) {
            val userContent = listOf(
                mapOf("type" to "text", "text" to prompt),
                mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
            )
            messages.add(mapOf("role" to "user", "content" to userContent))
        } else {
            messages.add(mapOf("role" to "user", "content" to prompt))
        }

        val body = mutableMapOf<String, Any>(
            "model" to model.modelId,
            "messages" to messages,
            "temperature" to 0.7,
            "max_tokens" to 1024
        )

        // 推理/思考开关
        if (!model.enableReasoning) {
            body["thinking"] = mapOf("type" to "disabled")
        }

        // 合并自定义 Body
        model.customBody.forEach { (key, value) ->
            body[key] = value
        }

        return body
    }

    /**
     * 构建 Anthropic 格式请求体
     */
    private fun buildAnthropicRequestBody(
        model: AiModel,
        prompt: String,
        systemPrompt: String?,
        withImage: Boolean,
        imageBase64: String?
    ): Map<String, Any> {
        val messages = mutableListOf<Map<String, Any>>()

        // 添加用户消息
        if (withImage && imageBase64 != null) {
            val userContent = listOf(
                mapOf("type" to "image", "source" to mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to imageBase64)),
                mapOf("type" to "text", "text" to prompt)
            )
            messages.add(mapOf("role" to "user", "content" to userContent))
        } else {
            messages.add(mapOf("role" to "user", "content" to prompt))
        }

        val body = mutableMapOf<String, Any>(
            "model" to model.modelId,
            "messages" to messages,
            "max_tokens" to 1024
        )

        // Anthropic 的 system 是顶级字段
        if (systemPrompt != null) {
            body["system"] = systemPrompt
        } else {
            body["system"] = DEFAULT_SYSTEM_PROMPT
        }

        // 推理/思考开关
        if (!model.enableReasoning) {
            body["thinking"] = mapOf("type" to "disabled")
        }

        // 合并自定义 Body
        model.customBody.forEach { (key, value) ->
            body[key] = value
        }

        return body
    }

    /**
     * 解析 API 响应
     */
    private fun parseResponse(responseBody: String, apiFormat: ApiFormat): Pair<String, StructuredOutput?> {
        return try {
            when (apiFormat) {
                ApiFormat.ANTHROPIC -> parseAnthropicResponse(responseBody)
                else -> parseOpenAIResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            Pair(responseBody, null)
        }
    }

    /**
     * 解析 OpenAI 兼容格式响应
     */
    private fun parseOpenAIResponse(responseBody: String): Pair<String, StructuredOutput?> {
        val jsonObject = JsonParser.parseString(responseBody).asJsonObject
        val choices = jsonObject.getAsJsonArray("choices")
        if (choices != null && choices.size() > 0) {
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val content = message?.get("content")?.asString ?: ""
            val structuredOutput = tryParseStructuredOutput(content)
            return Pair(content, structuredOutput)
        }
        return Pair("No response", null)
    }

    /**
     * 解析 Anthropic 格式响应
     */
    private fun parseAnthropicResponse(responseBody: String): Pair<String, StructuredOutput?> {
        val jsonObject = JsonParser.parseString(responseBody).asJsonObject
        val contentArray = jsonObject.getAsJsonArray("content")
        if (contentArray != null && contentArray.size() > 0) {
            val textBlock = contentArray[0].asJsonObject
            val content = textBlock.get("text")?.asString ?: ""
            val structuredOutput = tryParseStructuredOutput(content)
            return Pair(content, structuredOutput)
        }
        return Pair("No response", null)
    }

    /**
     * 尝试解析结构化输出
     */
    private fun tryParseStructuredOutput(content: String): StructuredOutput? {
        return try {
            // 尝试提取 JSON 部分
            val jsonStart = content.indexOf("{")
            val jsonEnd = content.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = content.substring(jsonStart, jsonEnd)
                val jsonObject = JsonParser.parseString(jsonStr).asJsonObject

                val objectsArray = jsonObject.getAsJsonArray("objects")
                val objects = mutableListOf<RecognizedObject>()

                objectsArray?.forEach { item ->
                    val obj = item.asJsonObject
                    objects.add(RecognizedObject(
                        name = obj.get("name")?.asString ?: "unknown",
                        nameCn = obj.get("name_cn")?.asString,
                        count = obj.get("count")?.asInt ?: 1,
                        confidence = obj.get("confidence")?.asFloat ?: 0.5f
                    ))
                }

                val summary = jsonObject.get("summary")?.asString ?: ""

                StructuredOutput(objects = objects, summary = summary)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse structured output, treating as plain text")
            null
        }
    }
}

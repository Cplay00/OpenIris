package com.yolo.openiris.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
     * 获取提供商的模型列表
     */
    fun fetchModelList(provider: AiProvider): Result<List<String>> {
        return try {
            val url = "${provider.getEffectiveBaseUrl()}/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${provider.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
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

        return try {
            val url = "${provider.getEffectiveBaseUrl()}/chat/completions"

            val messages = mutableListOf<Map<String, String>>()

            // 添加系统提示
            messages.add(mapOf(
                "role" to "system",
                "content" to (systemPrompt ?: DEFAULT_SYSTEM_PROMPT)
            ))

            // 添加用户提示
            messages.add(mapOf(
                "role" to "user",
                "content" to prompt
            ))

            val requestBody = mapOf(
                "model" to model.modelId,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 1024
            )

            val jsonBody = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${provider.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody)
                .build()

            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val result = parseResponse(body, model)
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

        return try {
            if (!model.hasVision) {
                return AiResult.failure(
                    modelId = model.id,
                    modelName = model.displayName,
                    error = "该模型不支持视觉输入"
                )
            }

            val url = "${provider.getEffectiveBaseUrl()}/chat/completions"

            val messages = mutableListOf<Map<String, Any>>()

            // 添加系统提示
            messages.add(mapOf(
                "role" to "system",
                "content" to (systemPrompt ?: DEFAULT_SYSTEM_PROMPT)
            ))

            // 添加用户消息（带图片）
            val userContent = listOf(
                mapOf(
                    "type" to "text",
                    "text" to prompt
                ),
                mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf(
                        "url" to "data:image/jpeg;base64,$imageBase64"
                    )
                )
            )
            messages.add(mapOf(
                "role" to "user",
                "content" to userContent
            ))

            val requestBody = mapOf(
                "model" to model.modelId,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 1024
            )

            val jsonBody = gson.toJson(requestBody)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${provider.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody)
                .build()

            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val result = parseResponse(body, model)
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
     * 解析 API 响应
     */
    private fun parseResponse(responseBody: String, model: AiModel): Pair<String, StructuredOutput?> {
        return try {
            val jsonObject = JsonParser.parseString(responseBody).asJsonObject
            val choices = jsonObject.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                val content = message.get("content")?.asString ?: ""

                // 尝试解析结构化输出
                val structuredOutput = tryParseStructuredOutput(content)

                Pair(content, structuredOutput)
            } else {
                Pair("No response", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            Pair(responseBody, null)
        }
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

package com.yolo.openiris.llm

import android.util.Log
import com.google.gson.Gson
import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.detection.UnifiedObjectResult
import com.yolo.openiris.vlm.VlmResult
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * LLM 客户端
 * 调用 OpenAI-compatible API 进行结果融合
 */
class LlmClient private constructor(
    private val config: AppConfig
) {
    companion object {
        private const val TAG = "OpenIris-LLM"

        @Volatile
        private var instance: LlmClient? = null

        fun getInstance(config: AppConfig): LlmClient {
            return instance ?: synchronized(this) {
                instance ?: LlmClient(config).also {
                    instance = it
                }
            }
        }

        fun recreate(config: AppConfig): LlmClient {
            instance = LlmClient(config)
            return instance!!
        }
 
         // OkHttpClient 单例
         val client: OkHttpClient = OkHttpClient.Builder()
             .connectTimeout(30, TimeUnit.SECONDS)
             .readTimeout(60, TimeUnit.SECONDS)
             .writeTimeout(30, TimeUnit.SECONDS)
             .build()
    }

    private val gson = Gson()

    /**
     * 同步融合结果
     */
    fun fuse(yoloResult: DetectionResult, vlmResult: VlmResult): LlmResult {
        if (config.llmModel.isBlank()) {
            return LlmResult(
                summary = "请在设置中配置 LLM 模型",
                rawResponse = "LLM model not configured"
            )
        }
        return try {
            val prompt = LlmRequestBuilder.buildPrompt(yoloResult, vlmResult)
            val request = buildRequest(prompt)
            executeRequest(request)
        } catch (e: Exception) {
            Log.e(TAG, "LLM fusion failed", e)
            LlmResult(
                summary = "融合失败: ${e.message}",
                rawResponse = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 异步融合结果
     */
    fun fuseAsync(yoloResult: DetectionResult, vlmResult: VlmResult, callback: LlmCallback) {
        try {
            val prompt = LlmRequestBuilder.buildPrompt(yoloResult, vlmResult)
            val request = buildRequest(prompt)

            client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "LLM async request failed", e)
                try { callback.onError(e) } catch (ex: Exception) { Log.e(TAG, "Callback error", ex) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = parseResponse(response)
                    callback.onSuccess(result)
                } catch (e: Exception) {
                    Log.e(TAG, "LLM async response parsing failed", e)
                    try { callback.onError(e) } catch (ex: Exception) { Log.e(TAG, "Callback error", ex) }
                }
            }
            })
        } catch (e: Exception) {
            Log.e(TAG, "LLM async build request failed", e)
            try { callback.onError(e) } catch (ex: Exception) { Log.e(TAG, "Callback error", ex) }
        }
    }

    private fun buildRequest(prompt: String): Request {
        val llmRequest = LlmRequestBuilder.buildRequest(config, prompt)

        val jsonBody = gson.toJson(llmRequest)

        return Request.Builder()
            .url("${config.getFullApiBaseUrl()}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun executeRequest(request: Request): LlmResult {
        client.newCall(request).execute().use { response ->
            return parseResponse(response)
        }
    }

    private fun parseResponse(response: Response): LlmResult {
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw IOException("HTTP ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")

        val apiResponse = gson.fromJson(responseBody, LlmApiResponse::class.java)
        val content = apiResponse.choices.firstOrNull()?.message?.content
            ?: throw IOException("No choices in response")

        return parseLlmContent(content, responseBody)
    }

    private fun parseLlmContent(content: String, rawResponse: String): LlmResult {
        return try {
            val jsonObject = org.json.JSONObject(content)
            val summary = jsonObject.optString("summary", "")

            val objectsArray = jsonObject.optJSONArray("objects") ?: org.json.JSONArray()
            val objects = mutableListOf<UnifiedObjectResult>()
            for (i in 0 until objectsArray.length()) {
                val obj = objectsArray.getJSONObject(i)
                val name = obj.optString("name", "")
                val count = obj.optInt("count", 0)
                val evidenceArray = obj.optJSONArray("evidence") ?: org.json.JSONArray()
                val evidence = mutableListOf<String>()
                for (j in 0 until evidenceArray.length()) {
                    evidence.add(evidenceArray.getString(j))
                }
                val confidence = if (obj.has("confidence") && !obj.isNull("confidence")) {
                    obj.opt("confidence")?.toString()?.ifBlank { null }
                } else {
                    null
                }
                val score = if (obj.has("score") && !obj.isNull("score")) {
                    obj.optDouble("score").toFloat()
                } else {
                    null
                }
                val attributesArray = obj.optJSONArray("attributes") ?: org.json.JSONArray()
                val attributes = mutableListOf<String>()
                for (j in 0 until attributesArray.length()) {
                    attributes.add(attributesArray.getString(j))
                }

                if (name.isNotBlank()) {
                    objects.add(
                        UnifiedObjectResult(
                            name = name,
                            count = count,
                            attributes = attributes,
                            confidence = confidence,
                            score = score,
                            evidence = evidence
                        )
                    )
                }
            }

            val discrepanciesArray = jsonObject.optJSONArray("discrepancies") ?: org.json.JSONArray()
            val discrepancies = mutableListOf<Discrepancy>()
            for (i in 0 until discrepanciesArray.length()) {
                val disc = discrepanciesArray.getJSONObject(i)
                val type = disc.optString("type", "")
                val description = disc.optString("description", "")
                if (type.isNotBlank()) {
                    discrepancies.add(Discrepancy(type, description))
                }
            }

            LlmResult(
                summary = summary,
                objects = objects,
                discrepancies = discrepancies,
                rawResponse = rawResponse
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM JSON response, using raw text", e)
            LlmResult(
                summary = content,
                rawResponse = rawResponse
            )
        }
    }

    /**
     * LLM 回调接口
     */
    interface LlmCallback {
        fun onSuccess(result: LlmResult)
        fun onError(error: Throwable)
    }
}

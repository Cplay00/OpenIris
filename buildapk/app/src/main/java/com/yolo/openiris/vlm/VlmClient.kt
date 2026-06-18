package com.yolo.openiris.vlm

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.yolo.openiris.config.AppConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * VLM 客户�? * 调用 OpenAI-compatible Vision API 进行图像识别
 */
class VlmClient private constructor(
    private val config: AppConfig
) {
    companion object {
        private const val TAG = "OpenIris-VLM"

        @Volatile
        private var instance: VlmClient? = null

        fun getInstance(config: AppConfig): VlmClient {
            return instance ?: synchronized(this) {
                instance ?: VlmClient(config).also {
                    instance = it
                }
            }
        }

        fun recreate(config: AppConfig): VlmClient {
            instance = VlmClient(config)
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
     * 同步识别图片
     */
    fun recognize(bitmap: Bitmap): VlmResult {
        val base64Image = bitmapToBase64(bitmap)
        if (config.vlmModel.isBlank()) {
            return VlmResult(
                imageBase64 = base64Image,
                rawResponse = "VLM model not configured",
                sceneSummary = "请在设置中配置 VLM 模型"
            )
        }
        return try {
            val request = buildRequest(base64Image)
            executeRequest(request).copy(imageBase64 = base64Image)
        } catch (e: Exception) {
            Log.e(TAG, "VLM recognition failed", e)
            VlmResult(
                imageBase64 = base64Image,
                rawResponse = e.message ?: "Unknown error",
                sceneSummary = "识别失败: ${e.message}"
            )
        }
    }

    /**
     * 异步识别图片
     */
    fun recognizeAsync(bitmap: Bitmap, callback: VlmCallback) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            val request = buildRequest(base64Image)

            client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "VLM async request failed", e)
                try { callback.onError(e) } catch (ex: Exception) { Log.e(TAG, "Callback error", ex) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = parseResponse(response)
                    callback.onSuccess(result)
                } catch (e: Exception) {
                    Log.e(TAG, "VLM async response parsing failed", e)
                    try { callback.onError(e) } catch (ex: Exception) { Log.e(TAG, "Callback error", ex) }
                }
            }
            })
        } catch (e: Exception) {
            Log.e(TAG, "VLM async build request failed", e)
            try { callback.onError(e) } catch (ex: Exception) { Log.e(TAG, "Callback error", ex) }
        }
    }

    private fun buildRequest(base64Image: String): Request {
        val vlmRequest = VlmRequestBuilder.buildRecognitionRequest(
            model = config.vlmModel,
            imageInput = VlmImageInput.Base64(
                data = base64Image,
                mimeType = "image/jpeg"
            )
        )

        val jsonBody = gson.toJson(vlmRequest)

        return Request.Builder()
            .url("${config.getFullApiBaseUrl()}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun executeRequest(request: Request): VlmResult {
        client.newCall(request).execute().use { response ->
            return parseResponse(response)
        }
    }

    private fun parseResponse(response: Response): VlmResult {
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw IOException("HTTP ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")

        val apiResponse = gson.fromJson(responseBody, VlmApiResponse::class.java)
        val content = apiResponse.choices.firstOrNull()?.message?.content
            ?: throw IOException("No choices in response")

        return parseVlmContent(content, responseBody)
    }

    private fun parseVlmContent(content: String, rawResponse: String): VlmResult {
        return try {
            // 尝试解析 JSON 响应
            val jsonObject = org.json.JSONObject(content)
            val objectsArray = jsonObject.optJSONArray("objects") ?: org.json.JSONArray()
            val sceneSummary = jsonObject.optString("sceneSummary").ifBlank {
                jsonObject.optString("summary", "")
            }

            val objects = mutableListOf<VlmObject>()
            for (i in 0 until objectsArray.length()) {
                val obj = objectsArray.getJSONObject(i)
                val name = obj.optString("name", "")
                val count = obj.optInt("count", 1)
                val attributesArray = obj.optJSONArray("attributes") ?: org.json.JSONArray()
                val attributes = mutableListOf<String>()
                for (j in 0 until attributesArray.length()) {
                    attributes.add(attributesArray.getString(j))
                }
                val confidence = if (obj.has("confidence") && !obj.isNull("confidence")) {
                    obj.optDouble("confidence").toFloat()
                } else {
                    null
                }

                if (name.isNotBlank()) {
                    objects.add(
                        VlmObject(
                            name = name,
                            count = count,
                            attributes = attributes,
                            confidence = confidence
                        )
                    )
                }
            }

            VlmResult(
                objects = objects,
                sceneSummary = sceneSummary,
                rawResponse = rawResponse
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse VLM JSON response, using raw text", e)
            // 如果 JSON 解析失败，使用原始文本作为摘要
            VlmResult(
                sceneSummary = content,
                rawResponse = rawResponse
            )
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * VLM 回调接口
     */
    interface VlmCallback {
        fun onSuccess(result: VlmResult)
        fun onError(error: Throwable)
    }
}


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
 * VLM 瀹㈡埛锟? * 璋冪敤 OpenAI-compatible Vision API 杩涜鍥惧儚璇嗗埆
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
            return instance ?: throw IllegalStateException("VlmClient not initialized. Call getInstance first.")
        }
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 鍚屾璇嗗埆鍥剧墖
     */
    fun recognize(bitmap: Bitmap): VlmResult {
        val base64Image = bitmapToBase64(bitmap)
        return try {
            val request = buildRequest(base64Image)
            executeRequest(request).copy(imageBase64 = base64Image)
        } catch (e: Exception) {
            Log.e(TAG, "VLM recognition failed", e)
            VlmResult(
                imageBase64 = base64Image,
                rawResponse = e.message ?: "Unknown error",
                sceneSummary = "璇嗗埆澶辫触: ${e.message}"
            )
        }
    }

    /**
     * 寮傛璇嗗埆鍥剧墖
     */
    fun recognizeAsync(bitmap: Bitmap, callback: VlmCallback) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            val request = buildRequest(base64Image)

            client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "VLM async request failed", e)
                callback.onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = parseResponse(response)
                    callback.onSuccess(result)
                } catch (e: Exception) {
                    Log.e(TAG, "VLM async response parsing failed", e)
                    callback.onError(e)
                }
            }
            })
        } catch (e: Exception) {
            Log.e(TAG, "VLM async build request failed", e)
            callback.onError(e)
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
            // 灏濊瘯瑙f瀽 JSON 鍝嶅簲
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
            // 濡傛灉 JSON 瑙f瀽澶辫触锛屼娇鐢ㄥ師濮嬫枃鏈綔涓烘憳瑕?            VlmResult(
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
     * VLM 鍥炶皟鎺ュ彛
     */
    interface VlmCallback {
        fun onSuccess(result: VlmResult)
        fun onError(error: Throwable)
    }
}


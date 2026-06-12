package com.yolo.openiris.vlm

import com.google.gson.Gson
import com.yolo.openiris.config.AppConfig

/**
 * Wave 2 的固定图�?VLM 兼容性验证辅助逻辑�? *
 * 该类不直接持有真实密钥,主要验证�? * 1. image_url / base64 两种输入的请求体形状�? * 2. 返回 JSON 文本的关键字段是否可被稳定解析�? */
object VlmCompatibilityValidator {
    private val gson = Gson()

    const val FIXED_TEST_IMAGE_URL = "https://example.com/openiris-fixed-test.jpg"
    const val FIXED_TEST_IMAGE_BASE64 = "RklYRUQtVEVTVC1JTUFHRS1EQVRB"

    fun buildImageUrlRequest(config: AppConfig): VlmRequest {
        return VlmRequestBuilder.buildRecognitionRequest(
            model = config.vlmModel,
            imageInput = VlmImageInput.ImageUrl(FIXED_TEST_IMAGE_URL)
        )
    }

    fun buildBase64Request(config: AppConfig): VlmRequest {
        return VlmRequestBuilder.buildRecognitionRequest(
            model = config.vlmModel,
            imageInput = VlmImageInput.Base64(FIXED_TEST_IMAGE_BASE64)
        )
    }

    fun isResponseContentStable(content: String): Boolean {
        return try {
            val jsonObject = org.json.JSONObject(content)
            jsonObject.has("objects") && jsonObject.has("sceneSummary")
        } catch (_: Exception) {
            false
        }
    }

    fun toJson(request: VlmRequest): String = gson.toJson(request)
}


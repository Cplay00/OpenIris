package com.yolo.openiris.vlm

import com.google.gson.annotations.SerializedName

/**
 * VLM 图片输入类型。
 */
sealed class VlmImageInput {
    data class ImageUrl(
        val url: String,
        val detail: String? = null
    ) : VlmImageInput()

    data class Base64(
        val data: String,
        val mimeType: String = "image/jpeg",
        val detail: String? = null
    ) : VlmImageInput()
}

/**
 * VLM API 请求内容片段。
 */
data class VlmRequestContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: VlmImageUrlPayload? = null
)

/**
 * VLM API 图片内容。
 */
data class VlmImageUrlPayload(
    val url: String,
    val detail: String? = null
)

/**
 * VLM API 请求消息。
 */
data class VlmRequestMessage(
    val role: String,
    val content: List<VlmRequestContentPart>
)

/**
 * VLM API 请求。
 */
data class VlmRequest(
    val model: String,
    val messages: List<VlmRequestMessage>,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048
)

/**
 * VLM API 响应消息。
 */
data class VlmResponseMessage(
    val role: String,
    val content: String
)

/**
 * VLM API 响应选择。
 */
data class VlmChoice(
    val message: VlmResponseMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * VLM API 使用量。
 */
data class VlmUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * VLM API 响应。
 */
data class VlmApiResponse(
    val id: String,
    val choices: List<VlmChoice>,
    val usage: VlmUsage
)

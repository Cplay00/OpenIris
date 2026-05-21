package com.yolo.openiris.llm

import com.google.gson.annotations.SerializedName

/**
 * LLM API 消息
 */
data class LlmMessage(
    val role: String,
    val content: String
)

/**
 * LLM API 响应格式
 */
data class ResponseFormat(
    val type: String = "json_object"
)

/**
 * LLM API 请求
 */
data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048,
    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null
)

/**
 * LLM API 响应选择
 */
data class LlmChoice(
    val message: LlmMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * LLM API 使用量
 */
data class LlmUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * LLM API 响应
 */
data class LlmApiResponse(
    val id: String,
    val choices: List<LlmChoice>,
    val usage: LlmUsage
)

data class FusionPromptPayload(
    @SerializedName("yolo_counts")
    val yoloCounts: Map<String, Int>,
    @SerializedName("yolo_objects")
    val yoloObjects: List<String>,
    @SerializedName("vlm_scene_summary")
    val vlmSceneSummary: String,
    @SerializedName("vlm_objects")
    val vlmObjects: List<String>
)

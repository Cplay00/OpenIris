package com.yolo.openiris.vlm

/**
 * VLM 请求构造器。
 * 仅负责将业务输入映射为 OpenAI-compatible chat completions 请求体。
 */
object VlmRequestBuilder {
    const val DEFAULT_USER_PROMPT = "请识别图中对象"

    const val DEFAULT_SYSTEM_PROMPT = """
你是一个专业的图像分析助手。请仔细分析图片，识别图中的所有对象。

对于每个对象，请输出：
1. 对象名称（中文）
2. 数量
3. 基本属性（颜色、大小、状态等）

请用 JSON 格式输出，结构如下：
{
  "objects": [
    {
      "name": "对象名称",
      "count": 数量,
      "attributes": ["属性1", "属性2"]
    }
  ],
  "sceneSummary": "场景整体描述"
}
"""

    fun buildRecognitionRequest(
        model: String,
        imageInput: VlmImageInput,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        userPrompt: String = DEFAULT_USER_PROMPT,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048
    ): VlmRequest {
        return VlmRequest(
            model = model,
            messages = listOf(
                VlmRequestMessage(
                    role = "system",
                    content = listOf(textPart(systemPrompt))
                ),
                VlmRequestMessage(
                    role = "user",
                    content = listOf(
                        textPart(userPrompt),
                        imagePart(imageInput)
                    )
                )
            ),
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    private fun textPart(text: String): VlmRequestContentPart {
        return VlmRequestContentPart(
            type = "text",
            text = text
        )
    }

    private fun imagePart(imageInput: VlmImageInput): VlmRequestContentPart {
        val payload = when (imageInput) {
            is VlmImageInput.ImageUrl -> VlmImageUrlPayload(
                url = imageInput.url,
                detail = imageInput.detail
            )

            is VlmImageInput.Base64 -> VlmImageUrlPayload(
                url = imageInput.toDataUrl(),
                detail = imageInput.detail
            )
        }

        return VlmRequestContentPart(
            type = "image_url",
            imageUrl = payload
        )
    }

    private fun VlmImageInput.Base64.toDataUrl(): String {
        return if (data.startsWith("data:")) {
            data
        } else {
            "data:$mimeType;base64,$data"
        }
    }
}

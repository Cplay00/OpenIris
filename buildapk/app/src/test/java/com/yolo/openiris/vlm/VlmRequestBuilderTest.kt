package com.yolo.openiris.vlm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlmRequestBuilderTest {

    @Test
    fun `buildRecognitionRequest 应返回正确的 VlmRequest`() {
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "qwen3.5-35b-a3b",
            imageInput = VlmImageInput.ImageUrl("https://example.com/test.jpg")
        )

        assertEquals("qwen3.5-35b-a3b", request.model)
        assertEquals(2, request.messages.size)
        assertEquals("system", request.messages[0].role)
        assertEquals("user", request.messages[1].role)
    }

    @Test
    fun `buildRecognitionRequest 应包含 system prompt`() {
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "test-model",
            imageInput = VlmImageInput.Base64("dGVzdA==", "image/jpeg")
        )

        val systemMessage = request.messages[0]
        assertEquals("system", systemMessage.role)
        assertEquals(1, systemMessage.content.size)
        assertEquals("text", systemMessage.content[0].type)
        assertTrue(systemMessage.content[0].text!!.contains("图像分析"))
    }

    @Test
    fun `buildRecognitionRequest 应包含 user prompt 和 image`() {
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "test-model",
            imageInput = VlmImageInput.ImageUrl("https://example.com/test.jpg")
        )

        val userMessage = request.messages[1]
        assertEquals("user", userMessage.role)
        assertEquals(2, userMessage.content.size)
        assertEquals("text", userMessage.content[0].type)
        assertEquals("image_url", userMessage.content[1].type)
    }

    @Test
    fun `buildRecognitionRequest Base64 输入应生成 data url`() {
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "test-model",
            imageInput = VlmImageInput.Base64("dGVzdA==", "image/jpeg")
        )

        val imagePart = request.messages[1].content[1]
        assertNotNull(imagePart.imageUrl)
        assertTrue(imagePart.imageUrl!!.url.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `buildRecognitionRequest ImageUrl 输入应直接使用 url`() {
        val testUrl = "https://example.com/test.jpg"
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "test-model",
            imageInput = VlmImageInput.ImageUrl(testUrl)
        )

        val imagePart = request.messages[1].content[1]
        assertNotNull(imagePart.imageUrl)
        assertEquals(testUrl, imagePart.imageUrl!!.url)
    }

    @Test
    fun `buildRecognitionRequest 应使用默认参数`() {
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "test-model",
            imageInput = VlmImageInput.ImageUrl("https://example.com/test.jpg")
        )

        assertEquals(0.7f, request.temperature)
        assertEquals(2048, request.maxTokens)
    }

    @Test
    fun `buildRecognitionRequest 应支持自定义参数`() {
        val request = VlmRequestBuilder.buildRecognitionRequest(
            model = "test-model",
            imageInput = VlmImageInput.ImageUrl("https://example.com/test.jpg"),
            temperature = 0.5f,
            maxTokens = 1024
        )

        assertEquals(0.5f, request.temperature)
        assertEquals(1024, request.maxTokens)
    }

    @Test
    fun `DEFAULT_SYSTEM_PROMPT 应包含 JSON 格式说明`() {
        val prompt = VlmRequestBuilder.DEFAULT_SYSTEM_PROMPT
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("objects"))
        assertTrue(prompt.contains("sceneSummary"))
    }

    @Test
    fun `DEFAULT_USER_PROMPT 应为识别图中对象`() {
        assertEquals("请识别图中对象", VlmRequestBuilder.DEFAULT_USER_PROMPT)
    }
}

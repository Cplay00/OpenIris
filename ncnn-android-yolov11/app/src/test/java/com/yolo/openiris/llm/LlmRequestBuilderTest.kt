package com.yolo.openiris.llm

import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.vlm.VlmObject
import com.yolo.openiris.vlm.VlmResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRequestBuilderTest {

    @Test
    fun `buildPrompt 应包含 YOLO 检测结果`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("person", 0, 0.95f, BoundingBox(10f, 20f, 100f, 200f)),
                DetectedObject("car", 1, 0.85f, BoundingBox(50f, 50f, 150f, 100f))
            )
        )
        val vlmResult = VlmResult(sceneSummary = "城市街道")

        val prompt = LlmRequestBuilder.buildPrompt(yoloResult, vlmResult)

        assertTrue(prompt.contains("person"))
        assertTrue(prompt.contains("car"))
        assertTrue(prompt.contains("0.95"))
    }

    @Test
    fun `buildPrompt 应包含 VLM 识别结果`() {
        val yoloResult = DetectionResult()
        val vlmResult = VlmResult(
            sceneSummary = "公园场景",
            objects = listOf(
                VlmObject("树", 3, listOf("绿色", "高大")),
                VlmObject("人", 2, listOf("散步"))
            )
        )

        val prompt = LlmRequestBuilder.buildPrompt(yoloResult, vlmResult)

        assertTrue(prompt.contains("公园场景"))
        assertTrue(prompt.contains("树"))
        assertTrue(prompt.contains("人"))
    }

    @Test
    fun `buildRequest 应使用配置中的模型名`() {
        val config = AppConfig(
            llmModel = "deepseek-v4-flash",
            apiKey = "test-key"
        )

        val request = LlmRequestBuilder.buildRequest(config, "test prompt")

        assertEquals("deepseek-v4-flash", request.model)
    }

    @Test
    fun `buildRequest 应包含 system 和 user 消息`() {
        val config = AppConfig(llmModel = "test-model")

        val request = LlmRequestBuilder.buildRequest(config, "test prompt")

        assertEquals(2, request.messages.size)
        assertEquals("system", request.messages[0].role)
        assertEquals("user", request.messages[1].role)
    }

    @Test
    fun `buildRequest 应设置 response_format 为 json_object`() {
        val config = AppConfig(llmModel = "test-model")

        val request = LlmRequestBuilder.buildRequest(config, "test prompt")

        assertNotNull(request.responseFormat)
        assertEquals("json_object", request.responseFormat!!.type)
    }

    @Test
    fun `buildRequest system prompt 应包含融合专家说明`() {
        val config = AppConfig(llmModel = "test-model")

        val request = LlmRequestBuilder.buildRequest(config, "test prompt")

        val systemPrompt = request.messages[0].content
        assertTrue(systemPrompt.contains("数据融合专家"))
        assertTrue(systemPrompt.contains("YOLO"))
        assertTrue(systemPrompt.contains("VLM"))
    }

    @Test
    fun `buildPrompt 空结果应生成有效 prompt`() {
        val yoloResult = DetectionResult()
        val vlmResult = VlmResult()

        val prompt = LlmRequestBuilder.buildPrompt(yoloResult, vlmResult)

        assertNotNull(prompt)
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `FusionPromptPayload 应正确序列化`() {
        val payload = FusionPromptPayload(
            yoloCounts = mapOf("person" to 2, "car" to 1),
            yoloObjects = listOf("person | confidence=0.95 | bbox=10,20,100,200"),
            vlmSceneSummary = "城市街道",
            vlmObjects = listOf("人 | count=2 | attributes=行走")
        )

        assertEquals(2, payload.yoloCounts["person"])
        assertEquals(1, payload.yoloCounts["car"])
        assertEquals("城市街道", payload.vlmSceneSummary)
    }
}

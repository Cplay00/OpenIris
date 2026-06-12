package com.yolo.openiris.llm

import com.google.gson.Gson
import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.vlm.VlmResult

object LlmRequestBuilder {
    private val gson = Gson()

    private const val SYSTEM_PROMPT = """
你是一个数据融合专家。请整合 YOLO 目标检测和 VLM 视觉语言模型的识别结果，生成统一的中文分析摘要。

输入数据格式：
- YOLO 结果：包含检测到的对象类别、置信度、边界框
- VLM 结果：包含对象名称、数量、属性、场景描述

请输出：
1. 整体场景摘要（中文）
2. 对象列表（名称、数量、证据来源）
3. 差异分析（如果 YOLO 和 VLM 结果不一致）

请用 JSON 格式输出：
{
  "summary": "整体摘要",
  "objects": [
    {
      "name": "对象名称",
      "count": 数量,
      "evidence": ["yolo", "vlm"],
      "confidence": "high"
    }
  ],
  "discrepancies": [
    {
      "type": "count_mismatch",
      "description": "描述"
    }
  ]
}
"""

    fun buildPrompt(yoloResult: DetectionResult, vlmResult: VlmResult): String {
        val payload = FusionPromptPayload(
            yoloCounts = yoloResult.countByLabel(),
            yoloObjects = yoloResult.objects.map {
                "${it.label} | confidence=${String.format("%.2f", it.confidence)} | bbox=${it.bbox.x},${it.bbox.y},${it.bbox.width},${it.bbox.height}"
            },
            vlmSceneSummary = vlmResult.sceneSummary,
            vlmObjects = vlmResult.objects.map {
                "${it.name} | count=${it.count} | attributes=${it.attributes.joinToString(",")}".trimEnd(',')
            }
        )

        return buildString {
            appendLine("请基于以下结构化输入完成融合分析，输出必须是 JSON 对象。")
            append(gson.toJson(payload))
        }
    }

    fun buildRequest(config: AppConfig, prompt: String): LlmRequest {
        return LlmRequest(
            model = config.llmModel,
            messages = listOf(
                LlmMessage(role = "system", content = SYSTEM_PROMPT.trimIndent()),
                LlmMessage(role = "user", content = prompt)
            ),
            responseFormat = ResponseFormat("json_object")
        )
    }
}

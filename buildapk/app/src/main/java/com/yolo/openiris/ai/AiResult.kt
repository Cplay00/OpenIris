package com.yolo.openiris.ai

/**
 * AI 调用结果
 */
data class AiResult(
    val modelId: String,
    val modelName: String,
    val success: Boolean,
    val content: String? = null,
    val structuredOutput: StructuredOutput? = null,
    val error: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0
) {
    companion object {
        fun success(
            modelId: String,
            modelName: String,
            content: String,
            structuredOutput: StructuredOutput? = null,
            durationMs: Long = 0
        ): AiResult {
            return AiResult(
                modelId = modelId,
                modelName = modelName,
                success = true,
                content = content,
                structuredOutput = structuredOutput,
                durationMs = durationMs
            )
        }

        fun failure(
            modelId: String,
            modelName: String,
            error: String,
            durationMs: Long = 0
        ): AiResult {
            return AiResult(
                modelId = modelId,
                modelName = modelName,
                success = false,
                error = error,
                durationMs = durationMs
            )
        }
    }
}

/**
 * 结构化输出
 */
data class StructuredOutput(
    val objects: List<RecognizedObject>,
    val summary: String
)

/**
 * 识别出的对象
 */
data class RecognizedObject(
    val name: String,
    val nameCn: String? = null,
    val count: Int,
    val confidence: Float
) {
    /**
     * 获取显示名称(中文+英文)
     */
    fun getDisplayName(): String {
        return if (nameCn != null) {
            "$name($nameCn)"
        } else {
            name
        }
    }
}

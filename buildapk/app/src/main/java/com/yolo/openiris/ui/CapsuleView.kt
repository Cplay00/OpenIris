package com.yolo.openiris.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.yolo.openiris.R
import com.yolo.openiris.detection.SlidingWindowTracker

/**
 * 胶囊视图组件
 * 用于显示单个识别对象的统计信息
 */
class CapsuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val textName: TextView
    private val textCount: TextView
    private val textConfidence: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.capsule_recognized_item, this, true)
        textName = findViewById(R.id.textName)
        textCount = findViewById(R.id.textCount)
        textConfidence = findViewById(R.id.textConfidence)

        // 内部胶囊描边样式
        radius = dpToPx(12f).toFloat()
        cardElevation = 0f
        strokeWidth = dpToPx(1f)
        setContentPadding(dpToPx(8f), dpToPx(3f), dpToPx(8f), dpToPx(3f))
    }

    /**
     * 绑定统计数据（带 max 标注支持，用于综合分析）
     */
    fun bind(stats: SlidingWindowTracker.ObjectStats, source: CapsuleSource, maxCount: Int = 0, isMax: Boolean = false) {
        textName.text = stats.name
        textName.typeface = Typeface.DEFAULT_BOLD
        textCount.text = when {
            isMax -> "\u00d7${stats.count} (max)"
            maxCount > 0 && stats.count < maxCount -> "\u00d7${stats.count} (max:$maxCount)"
            else -> "\u00d7${stats.count}"
        }
        textCount.typeface = Typeface.DEFAULT_BOLD
        textConfidence.text = "${(stats.avgConfidence * 100).toInt()}%"

        applyColors(source)
    }

    /**
     * 绑定识别对象（YOLO/AI 专用，无 max 标注）
     */
    fun bindRecognizedObject(
        name: String,
        count: Int,
        confidence: Float,
        source: CapsuleSource
    ) {
        textName.text = name
        textName.typeface = Typeface.DEFAULT_BOLD
        textCount.text = "\u00d7$count"
        textCount.typeface = Typeface.DEFAULT_BOLD
        textConfidence.text = "${(confidence * 100).toInt()}%"

        applyColors(source)
    }

    private fun applyColors(source: CapsuleSource) {
        val colors = when (source) {
            CapsuleSource.YOLO -> CapsuleColors(
                bgColor = context.getColor(R.color.capsule_yolo_bg),
                borderColor = context.getColor(R.color.capsule_yolo_border),
                textColor = context.getColor(R.color.capsule_yolo_text)
            )
            CapsuleSource.AI -> CapsuleColors(
                bgColor = context.getColor(R.color.capsule_ai_bg),
                borderColor = context.getColor(R.color.capsule_ai_border),
                textColor = context.getColor(R.color.capsule_ai_text)
            )
            CapsuleSource.COMBINED -> CapsuleColors(
                bgColor = context.getColor(R.color.capsule_combined_bg),
                borderColor = context.getColor(R.color.capsule_combined_border),
                textColor = context.getColor(R.color.capsule_combined_text)
            )
            CapsuleSource.UNIQUE -> CapsuleColors(
                bgColor = context.getColor(R.color.capsule_unique_bg),
                borderColor = context.getColor(R.color.capsule_unique_border),
                textColor = context.getColor(R.color.capsule_unique_text)
            )
        }

        // 使用背景色填充 + 内部描边
        strokeWidth = dpToPx(1f)
        strokeColor = colors.borderColor
        setCardBackgroundColor(colors.bgColor)
        textName.setTextColor(colors.textColor)
        textCount.setTextColor(colors.textColor)
        textConfidence.setTextColor(colors.textColor)

        // 确保粗细：标签名和计数加粗，置信度不加粗
        textName.typeface = Typeface.DEFAULT_BOLD
        textCount.typeface = Typeface.DEFAULT_BOLD
        textConfidence.typeface = Typeface.DEFAULT
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * 胶囊来源类型
     */
    enum class CapsuleSource {
        YOLO,
        AI,
        COMBINED,
        UNIQUE
    }

    /**
     * 胶囊颜色配置
     */
    data class CapsuleColors(
        val bgColor: Int = Color.TRANSPARENT,
        val borderColor: Int = Color.TRANSPARENT,
        val textColor: Int = Color.BLACK
    )
}

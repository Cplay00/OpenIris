package com.yolo.openiris.ui

import android.content.Context
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

        // 设置卡片样式
        radius = dpToPx(20f).toFloat()
        cardElevation = 0f
        strokeWidth = dpToPx(1f)
        setContentPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
    }

    /**
     * 绑定统计数据
     */
    fun bind(stats: SlidingWindowTracker.ObjectStats, source: CapsuleSource) {
        textName.text = stats.name
        textCount.text = "×${stats.count}"
        textConfidence.text = "${(stats.avgConfidence * 100).toInt()}%"

        // 根据来源设置颜色
        val colors = when (source) {
            CapsuleSource.YOLO -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_yolo_bg),
                borderColor = context.getColor(R.color.capsule_yolo_border),
                textColor = context.getColor(R.color.capsule_yolo_text)
            )
            CapsuleSource.AI -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_ai_bg),
                borderColor = context.getColor(R.color.capsule_ai_border),
                textColor = context.getColor(R.color.capsule_ai_text)
            )
            CapsuleSource.COMBINED -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_combined_bg),
                borderColor = context.getColor(R.color.capsule_combined_border),
                textColor = context.getColor(R.color.capsule_combined_text)
            )
            CapsuleSource.UNIQUE -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_unique_bg),
                borderColor = context.getColor(R.color.capsule_unique_border),
                textColor = context.getColor(R.color.capsule_unique_text)
            )
        }

        setCardBackgroundColor(colors.backgroundColor)
        strokeColor = colors.borderColor
        textName.setTextColor(colors.textColor)
        textCount.setTextColor(colors.textColor)
        textConfidence.setTextColor(colors.textColor)
    }

    /**
     * 绑定识别对象数据(用于 AI 结果)
     */
    fun bindRecognizedObject(
        name: String,
        count: Int,
        confidence: Float,
        source: CapsuleSource
    ) {
        textName.text = name
        textCount.text = "×$count"
        textConfidence.text = "${(confidence * 100).toInt()}%"

        val colors = when (source) {
            CapsuleSource.YOLO -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_yolo_bg),
                borderColor = context.getColor(R.color.capsule_yolo_border),
                textColor = context.getColor(R.color.capsule_yolo_text)
            )
            CapsuleSource.AI -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_ai_bg),
                borderColor = context.getColor(R.color.capsule_ai_border),
                textColor = context.getColor(R.color.capsule_ai_text)
            )
            CapsuleSource.COMBINED -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_combined_bg),
                borderColor = context.getColor(R.color.capsule_combined_border),
                textColor = context.getColor(R.color.capsule_combined_text)
            )
            CapsuleSource.UNIQUE -> CapsuleColors(
                backgroundColor = context.getColor(R.color.capsule_unique_bg),
                borderColor = context.getColor(R.color.capsule_unique_border),
                textColor = context.getColor(R.color.capsule_unique_text)
            )
        }

        setCardBackgroundColor(colors.backgroundColor)
        strokeColor = colors.borderColor
        textName.setTextColor(colors.textColor)
        textCount.setTextColor(colors.textColor)
        textConfidence.setTextColor(colors.textColor)
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
        val backgroundColor: Int,
        val borderColor: Int,
        val textColor: Int
    )
}

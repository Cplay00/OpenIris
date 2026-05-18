package com.yolo.openiris.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.yolo.openiris.detection.DetectedObject

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 0, 0, 0)
    }

    private var detectedObjects: List<DetectedObject> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val colors = intArrayOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
        Color.CYAN, Color.MAGENTA, Color.WHITE, Color.parseColor("#FF6B35")
    )

    fun setResults(objects: List<DetectedObject>, imgWidth: Int, imgHeight: Int) {
        detectedObjects = objects
        imageWidth = imgWidth
        imageHeight = imgHeight
        postInvalidate()
    }

    fun clear() {
        detectedObjects = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detectedObjects.isEmpty()) return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        for (obj in detectedObjects) {
            val color = colors[Math.floorMod(obj.labelIndex, colors.size)]
            boxPaint.color = color

            val rect = RectF(
                obj.bbox.x * scaleX,
                obj.bbox.y * scaleY,
                (obj.bbox.x + obj.bbox.width) * scaleX,
                (obj.bbox.y + obj.bbox.height) * scaleY
            )

            canvas.drawRect(rect, boxPaint)

            val label = "${obj.label} ${(obj.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)

            val labelTop = maxOf(0f, rect.top - textBounds.height() - 12)
            val labelRect = RectF(
                rect.left,
                labelTop,
                rect.left + textWidth + 12,
                labelTop + textBounds.height() + 12
            )
            canvas.drawRect(labelRect, bgPaint)
            canvas.drawText(label, rect.left + 6, labelTop + textBounds.height() + 4, textPaint)
        }
    }
}

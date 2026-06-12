package com.yolo.openiris.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yolo.openiris.detection.DetectedObject

/**
 * 图像工具类
 */
object ImageUtils {

    /**
     * 在 Bitmap 上绘制检测框
     */
    fun drawDetections(bitmap: Bitmap, objects: List<DetectedObject>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.RED
        }
        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
        }
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(180, 0, 0, 0)
        }

        objects.forEach { obj ->
            val rect = obj.bbox.toRectF()

            // 绘制框
            paint.color = getColorForLabel(obj.labelIndex)
            canvas.drawRect(rect, paint)

            // 绘制标签背景
            val label = "${obj.label} ${String.format("%.2f", obj.confidence)}"
            val textBounds = RectF()
            textPaint.getTextBounds(label, 0, label.length, android.graphics.Rect())
            textBounds.set(
                rect.left,
                rect.top - 40,
                rect.left + textPaint.measureText(label) + 10,
                rect.top
            )
            canvas.drawRect(textBounds, bgPaint)

            // 绘制标签文字
            canvas.drawText(label, rect.left + 5, rect.top - 10, textPaint)
        }

        return result
    }

    /**
     * 为不同类别生成不同颜色
     */
    private fun getColorForLabel(labelIndex: Int): Int {
        val colors = intArrayOf(
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
            Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#FF6B35"), Color.parseColor("#8B5CF6")
        )
        return colors[Math.floorMod(labelIndex, colors.size)]
    }

    /**
     * 缩放 Bitmap 到指定尺寸
     */
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * 将 Bitmap 转换为 RGB 字节数组（用于 NCNN 推理）
     */
    fun bitmapToRgbBytes(bitmap: Bitmap): ByteArray {
        // 确保 Bitmap 是 ARGB_8888 格式
        val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        val width = argbBitmap.width
        val height = argbBitmap.height
        val pixels = IntArray(width * height)
        argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 如果创建了新 bitmap，回收它
        if (argbBitmap !== bitmap) {
            argbBitmap.recycle()
        }

        val rgbBytes = ByteArray(width * height * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            rgbBytes[i * 3] = (pixel shr 16 and 0xFF).toByte()     // R
            rgbBytes[i * 3 + 1] = (pixel shr 8 and 0xFF).toByte()  // G
            rgbBytes[i * 3 + 2] = (pixel and 0xFF).toByte()        // B
        }
        return rgbBytes
    }
}

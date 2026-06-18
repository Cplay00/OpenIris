package com.yolo.openiris.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.yolo.openiris.R

/**
 * 自定义Toast组件
 * 支持长文本显示和复制功能
 */
class CustomToast(context: Context) {

    private val appContext: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 显示自定义Toast
     * @param message 消息内容
     * @param duration 显示时长（毫秒），默认3000ms
     * @param copyable 是否显示复制按钮，默认true
     */
    fun show(message: String, duration: Long = 3000, copyable: Boolean = true) {
        val inflater = LayoutInflater.from(appContext)
        val layout = inflater.inflate(R.layout.layout_custom_toast, null)

        val textMessage = layout.findViewById<TextView>(R.id.textMessage)
        val buttonCopy = layout.findViewById<ImageButton>(R.id.buttonCopy)

        textMessage.text = message

        if (copyable) {
            buttonCopy.visibility = View.VISIBLE
            buttonCopy.setOnClickListener {
                copyToClipboard(message)
                Toast.makeText(appContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        } else {
            buttonCopy.visibility = View.GONE
        }

        val toast = Toast(appContext)
        toast.view = layout
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, 0, 0)

        toast.show()

        // 自动消失
        handler.postDelayed({
            try {
                toast.cancel()
            } catch (e: Exception) {
                // 忽略异常
            }
        }, duration)
    }

    /**
     * 显示成功消息
     */
    fun showSuccess(message: String, duration: Long = 3000) {
        show(message, duration, copyable = false)
    }

    /**
     * 显示错误消息（可复制）
     */
    fun showError(message: String, duration: Long = 5000) {
        show("错误: $message", duration, copyable = true)
    }

    /**
     * 显示长文本消息（可复制）
     */
    fun showLongText(message: String, duration: Long = 5000) {
        show(message, duration, copyable = true)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OpenIris", text)
        clipboard.setPrimaryClip(clip)
    }
}

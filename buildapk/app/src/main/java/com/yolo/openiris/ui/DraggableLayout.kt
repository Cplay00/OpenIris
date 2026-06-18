package com.yolo.openiris.ui

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.LinearLayout

/**
 * 可拖动的 LinearLayout
 * 通过重写 onInterceptTouchEvent 实现长按拖动，同时保留子视图的点击事件。
 *
 * 工作原理：
 * - ACTION_DOWN 时不拦截（返回 false），让子视图正常处理点击
 * - 长按 500ms 后标记 isLongPressed
 * - 当 isLongPressed 且移动距离超过阈值时，开始拦截事件并执行拖动
 * - ACTION_UP/CANCEL 时重置状态
 */
class DraggableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 拖动回调：(deltaX, deltaY) -> Unit */
    var onDragListener: ((Float, Float) -> Unit)? = null

    /** 是否正在拖动（供外部查询） */
    var isDragging = false
        private set

    /** 是否已长按 */
    var isLongPressed = false
        private set

    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f
    private val dragThreshold = 10f * context.resources.displayMetrics.density
    private val longPressTimeout = 500L
    private var longPressRunnable: Runnable? = null

    /**
     * 拦截事件判断：当检测到长按+拖动时，拦截后续事件由自己处理
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX
                lastY = ev.rawY
                startX = ev.rawX
                startY = ev.rawY
                isDragging = false
                isLongPressed = false

                // 启动长按检测
                longPressRunnable?.let { removeCallbacks(it) }
                longPressRunnable = Runnable {
                    isLongPressed = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                postDelayed(longPressRunnable, longPressTimeout)

                return false // 不拦截，让子视图处理点击
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLongPressed) {
                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (!isDragging && distance > dragThreshold) {
                        isDragging = true
                        longPressRunnable?.let { removeCallbacks(it) }
                        // 更新 lastX/lastY 为当前位置，避免首次拖动跳跃
                        lastX = ev.rawX
                        lastY = ev.rawY
                    }
                }
                if (isDragging) {
                    return true // 拦截：后续事件由 onTouchEvent 处理
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { removeCallbacks(it) }
                isDragging = false
                isLongPressed = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * 处理拖动事件（仅在 onInterceptTouchEvent 返回 true 后调用）
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isDragging) {
            when (ev.action) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lastX
                    val dy = ev.rawY - lastY
                    lastX = ev.rawX
                    lastY = ev.rawY
                    onDragListener?.invoke(dx, dy)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { removeCallbacks(it) }
                    isDragging = false
                    isLongPressed = false
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
    }
}

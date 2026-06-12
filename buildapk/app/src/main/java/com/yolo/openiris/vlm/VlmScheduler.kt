package com.yolo.openiris.vlm

import android.graphics.Bitmap
import android.util.Log
import com.yolo.openiris.config.AppConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * VLM 调度器(实时模式)
 *
 * 负责控制 VLM 调用频率,防止请求堆积:
 * 1. 按可配置间隔触发 VLM 请求(默认 5 秒)
 * 2. 如果上一个请求还在进行中,跳过本次触发
 * 3. 提供状态查询接口
 */
class VlmScheduler(
    private val config: AppConfig,
    private val callback: VlmSchedulerCallback
) {
    companion object {
        private const val TAG = "OpenIris-VlmScheduler"
    }

    enum class State {
        IDLE,       // 空闲,可以接受新请求
        PENDING,    // 请求进行中
        DISABLED    // 调度器已禁用
    }

    private val isRunning = AtomicBoolean(false)
    private val isPending = AtomicBoolean(false)
    private val lastTriggerTime = AtomicLong(0)
    private val triggerCount = AtomicLong(0)
    private val skipCount = AtomicLong(0)

    @Volatile
    private var intervalMs: Long = config.vlmIntervalSeconds * 1000L

    /**
     * 启动调度器
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "VLM Scheduler started with interval ${intervalMs}ms")
        }
    }

    /**
     * 停止调度器
     */
    fun stop() {
        isRunning.set(false)
        Log.d(TAG, "VLM Scheduler stopped")
    }

    /**
     * 更新间隔
     */
    fun updateInterval(intervalSeconds: Int) {
        intervalMs = intervalSeconds * 1000L
        Log.d(TAG, "VLM Scheduler interval updated to ${intervalMs}ms")
    }

    /**
     * 触发 VLM 请求
     *
     * 如果满足以下条件则触发请求:
     * 1. 调度器正在运行
     * 2. 距离上次触发已超过间隔时间
     * 3. 没有正在进行的请求
     *
     * @return true 如果请求已触发,false 如果被跳过
     */
    fun trigger(bitmap: Bitmap): Boolean {
        if (!isRunning.get()) {
            Log.d(TAG, "Scheduler not running, skip trigger")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = lastTriggerTime.get()

        // 检查间隔
        if (currentTime - lastTime < intervalMs) {
            skipCount.incrementAndGet()
            return false
        }

        // 检查是否有请求正在进行
        if (!isPending.compareAndSet(false, true)) {
            skipCount.incrementAndGet()
            Log.d(TAG, "Previous request still pending, skip trigger")
            return false
        }

        // 触发请求
        lastTriggerTime.set(currentTime)
        triggerCount.incrementAndGet()

        Log.d(TAG, "VLM request triggered #${triggerCount.get()}")

        // 异步执行 VLM 请求
        executeVlmRequest(bitmap)

        return true
    }

    private fun executeVlmRequest(bitmap: Bitmap) {
        try {
            val vlmClient = VlmClient.getInstance(config)
            vlmClient.recognizeAsync(bitmap, object : VlmClient.VlmCallback {
                override fun onSuccess(result: VlmResult) {
                    isPending.set(false)
                    Log.d(TAG, "VLM request completed successfully")
                    callback.onVlmResult(result)
                }

                override fun onError(error: Throwable) {
                    isPending.set(false)
                    Log.e(TAG, "VLM request failed", error)
                    callback.onVlmError(error)
                }
            })
        } catch (e: Exception) {
            isPending.set(false)
            Log.e(TAG, "Failed to execute VLM request", e)
            callback.onVlmError(e)
        }
    }

    /**
     * 获取当前状态
     */
    fun getState(): State {
        return when {
            !isRunning.get() -> State.DISABLED
            isPending.get() -> State.PENDING
            else -> State.IDLE
        }
    }

    /**
     * 是否正在等待响应
     */
    fun isPending(): Boolean = isPending.get()

    /**
     * 获取触发次数
     */
    fun getTriggerCount(): Long = triggerCount.get()

    /**
     * 获取跳过次数
     */
    fun getSkipCount(): Long = skipCount.get()

    /**
     * 重置统计
     */
    fun resetStats() {
        triggerCount.set(0)
        skipCount.set(0)
    }

    /**
     * VLM 调度器回调接口
     */
    interface VlmSchedulerCallback {
        fun onVlmResult(result: VlmResult)
        fun onVlmError(error: Throwable)
    }
}

package com.yolo.openiris

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.detection.SlidingWindowTracker
import com.yolo.openiris.ui.CapsuleView
import com.yolo.openiris.ui.OverlayView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RealtimeDetectActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "OpenIris-Realtime"
    }

    // Core components
    private lateinit var yolov11Ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager
    private lateinit var aiModelManager: AiModelManager

    // UI components
    private lateinit var cameraView: SurfaceView
    private lateinit var overlayView: OverlayView
    private lateinit var textFps: MaterialTextView
    private lateinit var textAiStatus: MaterialTextView
    private lateinit var buttonBack: ImageButton
    private lateinit var buttonFullscreen: ImageButton
    private lateinit var buttonSwitchCamera: MaterialButton
    private lateinit var buttonToggleGpu: MaterialButton
    private lateinit var buttonToggleAi: MaterialButton

    // Result summary components
    private lateinit var flexboxYolo: FlexboxLayout
    private lateinit var flexboxAi: FlexboxLayout
    private lateinit var flexboxCombined: FlexboxLayout
    private lateinit var textYoloCount: MaterialTextView
    private lateinit var textAiCount: MaterialTextView
    private lateinit var textCombinedCount: MaterialTextView
    private lateinit var textWindowDuration: MaterialTextView

    // State
    private var facing = 0
    private var useGpu = true
    private var currentModel = 0
    private var isAiEnabled = false
    private var isFullscreen = false
    private var cachedLabels: List<String> = emptyList()
    private var cachedModelName: String = ""

    // Detection trackers
    private val yoloTracker = SlidingWindowTracker(15000) // 15 seconds window
    private val aiTracker = SlidingWindowTracker(15000)

    // AI call job
    private var aiCallJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime_detect)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configManager = ConfigManager.getInstance(this)
        aiModelManager = AiModelManager.getInstance(this)
        yolov11Ncnn = Yolov11Ncnn()

        yolov11Ncnn.setDetectionCallback(object : Yolov11Ncnn.DetectionCallback {
            override fun onDetectionResult(results: IntArray) {
                processDetectionResult(results)
            }
        })

        initViews()
        if (!loadModel()) {
            finish()
            return
        }
    }

    private fun initViews() {
        // Camera and overlay
        cameraView = findViewById(R.id.cameraView)
        overlayView = findViewById(R.id.overlayView)

        // Top status bar
        textFps = findViewById(R.id.textFps)
        textAiStatus = findViewById(R.id.textAiStatus)
        buttonBack = findViewById(R.id.buttonBack)
        buttonFullscreen = findViewById(R.id.buttonFullscreen)

        // Right control panel
        buttonSwitchCamera = findViewById(R.id.buttonSwitchCamera)
        buttonToggleGpu = findViewById(R.id.buttonToggleGpu)
        buttonToggleAi = findViewById(R.id.buttonToggleAi)

        // Result summary
        flexboxYolo = findViewById(R.id.flexboxYolo)
        flexboxAi = findViewById(R.id.flexboxAi)
        flexboxCombined = findViewById(R.id.flexboxCombined)
        textYoloCount = findViewById(R.id.textYoloCount)
        textAiCount = findViewById(R.id.textAiCount)
        textCombinedCount = findViewById(R.id.textCombinedCount)
        textWindowDuration = findViewById(R.id.textWindowDuration)

        // Setup camera
        cameraView.holder.setFormat(PixelFormat.RGBA_8888)
        cameraView.holder.addCallback(this)

        // Setup click listeners
        buttonBack.setOnClickListener { finish() }

        buttonFullscreen.setOnClickListener { toggleFullscreen() }

        buttonSwitchCamera.setOnClickListener {
            facing = 1 - facing
            yolov11Ncnn.closeCamera()
            yolov11Ncnn.openCamera(facing)
        }

        buttonToggleGpu.setOnClickListener {
            useGpu = !useGpu
            if (loadModel()) {
                buttonToggleGpu.text = if (useGpu) "GPU" else "CPU"
            } else {
                useGpu = !useGpu
            }
        }

        buttonToggleAi.setOnClickListener {
            isAiEnabled = !isAiEnabled
            updateAiStatus()
            if (isAiEnabled) {
                startAiCallLoop()
            } else {
                stopAiCallLoop()
            }
        }

        // Update window duration text
        textWindowDuration.text = "${yoloTracker.getWindowDurationSeconds()}秒窗口"

        // Setup fullscreen toggle on camera view
        cameraView.setOnClickListener { toggleFullscreen() }
    }

    private fun loadModel(): Boolean {
        val config = configManager.loadConfig()
        currentModel = 0
        val cpuGpu = if (useGpu) 1 else 0

        val ret = yolov11Ncnn.loadModel(assets, currentModel, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
            Toast.makeText(this, "模型加载失败，请检查 GPU 设置", Toast.LENGTH_LONG).show()
            return false
        }

        cachedModelName = config.selectedModel
        cachedLabels = loadLabels(config.selectedModel)
        return true
    }

    private fun updateAiStatus() {
        if (isAiEnabled) {
            textAiStatus.text = "AI: 开启"
            textAiStatus.setTextColor(getColor(R.color.capsule_ai_text))
        } else {
            textAiStatus.text = "AI: 关闭"
            textAiStatus.setTextColor(getColor(android.R.color.darker_gray))
        }
    }

    private fun startAiCallLoop() {
        aiCallJob?.cancel()
        aiCallJob = lifecycleScope.launch {
            val intervalMs = (aiModelManager.getCallIntervalSeconds() * 1000).toLong()
            while (true) {
                delay(intervalMs)
                if (isAiEnabled) {
                    callAiModel()
                }
            }
        }
    }

    private fun stopAiCallLoop() {
        aiCallJob?.cancel()
        aiCallJob = null
    }

    private suspend fun callAiModel() {
        try {
            // 将当前 YOLO 检测结果作为上下文传给 AI
            val yoloSummary = yoloTracker.getSortedSummary()
            val contextPrompt = if (yoloSummary.isNotEmpty()) {
                val yoloText = yoloSummary.joinToString("、") { "${it.name}(${it.count}个)" }
                "当前画面 YOLO 检测到：$yoloText。请基于以上检测结果，补充识别画面中的其他物体，以JSON格式返回结果。"
            } else {
                "请识别图片中的物体，以JSON格式返回结果。"
            }

            val result = aiModelManager.callWithFallback(
                prompt = contextPrompt,
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            )

            if (result.success && result.structuredOutput != null) {
                val output = result.structuredOutput
                output.objects.forEach { obj ->
                    aiTracker.addDetection(obj.name, obj.confidence)
                }
                runOnUiThread {
                    updateResultSummary()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI call failed", e)
        }
    }

    private fun processDetectionResult(rawResults: IntArray) {
        val objects = mutableListOf<DetectedObject>()
        val labels = cachedLabels

        var i = 0
        while (i + 5 < rawResults.size) {
            val x = rawResults[i].toFloat()
            val y = rawResults[i + 1].toFloat()
            val w = rawResults[i + 2].toFloat()
            val h = rawResults[i + 3].toFloat()
            val labelIndex = rawResults[i + 4]
            val confidence = rawResults[i + 5] / 1000f

            val label = if (labelIndex in labels.indices) labels[labelIndex] else "unknown"

            objects.add(
                DetectedObject(
                    label = label,
                    labelIndex = labelIndex,
                    confidence = confidence,
                    bbox = BoundingBox(x, y, w, h)
                )
            )
            i += 6
        }

        // Update YOLO tracker
        objects.forEach { obj ->
            yoloTracker.addDetection(obj.label, obj.confidence)
        }

        runOnUiThread {
            overlayView.setResults(objects, overlayView.width, overlayView.height)
            updateResultSummary()
        }
    }

    private fun updateResultSummary() {
        // Update YOLO capsules
        val yoloSummary = yoloTracker.getSortedSummary()
        flexboxYolo.removeAllViews()
        yoloSummary.forEach { stats ->
            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.YOLO)
            flexboxYolo.addView(capsule)
        }
        textYoloCount.text = "${yoloTracker.getUniqueCount()} 类"

        // Update AI capsules
        val aiSummary = aiTracker.getSortedSummary()
        flexboxAi.removeAllViews()
        aiSummary.forEach { stats ->
            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.AI)
            flexboxAi.addView(capsule)
        }
        textAiCount.text = "${aiTracker.getUniqueCount()} 类"

        // Update combined capsules
        val combinedMap = mutableMapOf<String, SlidingWindowTracker.ObjectStats>()
        yoloSummary.forEach { stats ->
            combinedMap[stats.name] = stats.copy()
        }
        aiSummary.forEach { stats ->
            val existing = combinedMap[stats.name]
            if (existing != null) {
                combinedMap[stats.name] = existing.copy(
                    count = maxOf(existing.count, stats.count),
                    totalConfidence = (existing.avgConfidence + stats.avgConfidence) / 2 * maxOf(existing.count, stats.count)
                )
            } else {
                combinedMap[stats.name] = stats.copy()
            }
        }

        flexboxCombined.removeAllViews()
        combinedMap.values.sortedByDescending { it.count }.forEach { stats ->
            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.COMBINED)
            flexboxCombined.addView(capsule)
        }
        textCombinedCount.text = "${combinedMap.size} 类"
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (isFullscreen) {
            // Hide system bars
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            // Animate UI elements out
            animateViewOut(findViewById(R.id.topStatusBar))
            animateViewOut(findViewById(R.id.rightControlPanel))
            animateViewOut(findViewById(R.id.resultSummaryCard))
        } else {
            // Show system bars
            controller.show(WindowInsetsCompat.Type.systemBars())

            // Animate UI elements in
            animateViewIn(findViewById(R.id.topStatusBar))
            animateViewIn(findViewById(R.id.rightControlPanel))
            animateViewIn(findViewById(R.id.resultSummaryCard))
        }
    }

    private fun animateViewOut(view: View) {
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        val translationY = ObjectAnimator.ofFloat(view, "translationY", 0f, -view.height.toFloat())
        val set = AnimatorSet()
        set.playTogether(alpha, translationY)
        set.duration = 300
        set.interpolator = DecelerateInterpolator()
        set.start()
    }

    private fun animateViewIn(view: View) {
        view.alpha = 0f
        view.translationY = -view.height.toFloat()
        view.visibility = View.VISIBLE

        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val translationY = ObjectAnimator.ofFloat(view, "translationY", -view.height.toFloat(), 0f)
        val set = AnimatorSet()
        set.playTogether(alpha, translationY)
        set.duration = 300
        set.interpolator = DecelerateInterpolator()
        set.start()
    }

    private fun loadLabels(modelName: String): List<String> {
        return try {
            assets.open("models/$modelName/labels.txt").use { it.bufferedReader().readLines().filter { line -> line.isNotBlank() } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
            emptyList()
        }
    }

    // SurfaceHolder.Callback
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        yolov11Ncnn.setOutputWindow(holder.surface)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onResume() {
        super.onResume()
        yolov11Ncnn.openCamera(facing)
        if (isAiEnabled) {
            startAiCallLoop()
        }
    }

    override fun onPause() {
        super.onPause()
        yolov11Ncnn.closeCamera()
        stopAiCallLoop()
    }
}

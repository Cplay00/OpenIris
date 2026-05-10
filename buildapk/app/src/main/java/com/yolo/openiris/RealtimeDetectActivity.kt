package com.yolo.openiris

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Environment
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModelManager
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var buttonCapture: ImageButton
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
    private val yoloTracker = SlidingWindowTracker(15000)
    private val aiTracker = SlidingWindowTracker(15000)

    // AI call job
    private var aiCallJob: Job? = null

    // Screenshot analysis job
    private var screenshotJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime_detect)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configManager = ConfigManager.getInstance(this)
        aiModelManager = AiModelManager.getInstance(this)
        yolov11Ncnn = Yolov11Ncnn()

        initViews()
        if (!loadModel()) {
            finish()
            return
        }
    }

    private fun initViews() {
        cameraView = findViewById(R.id.cameraView)
        overlayView = findViewById(R.id.overlayView)

        textFps = findViewById(R.id.textFps)
        textAiStatus = findViewById(R.id.textAiStatus)
        buttonBack = findViewById(R.id.buttonBack)
        buttonCapture = findViewById(R.id.buttonCapture)

        buttonSwitchCamera = findViewById(R.id.buttonSwitchCamera)
        buttonToggleGpu = findViewById(R.id.buttonToggleGpu)
        buttonToggleAi = findViewById(R.id.buttonToggleAi)

        flexboxYolo = findViewById(R.id.flexboxYolo)
        flexboxAi = findViewById(R.id.flexboxAi)
        flexboxCombined = findViewById(R.id.flexboxCombined)
        textYoloCount = findViewById(R.id.textYoloCount)
        textAiCount = findViewById(R.id.textAiCount)
        textCombinedCount = findViewById(R.id.textCombinedCount)
        textWindowDuration = findViewById(R.id.textWindowDuration)

        cameraView.holder.setFormat(PixelFormat.RGBA_8888)
        cameraView.holder.addCallback(this)

        // 初始化按钮状态
        buttonToggleGpu.text = if (useGpu) "GPU" else "CPU"
        updateAiButtonStyle()

        buttonBack.setOnClickListener { finish() }

        // 截图按钮 - 截取当前画面并保存
        buttonCapture.setOnClickListener { captureAndSave() }

        buttonSwitchCamera.setOnClickListener {
            facing = 1 - facing
            yolov11Ncnn.closeCamera()
            yolov11Ncnn.openCamera(facing)
        }

        buttonToggleGpu.setOnClickListener {
            useGpu = !useGpu
            buttonToggleGpu.text = if (useGpu) "GPU" else "CPU"
            loadModel()
        }

        buttonToggleAi.setOnClickListener {
            isAiEnabled = !isAiEnabled
            updateAiButtonStyle()
            updateAiStatus()
            if (isAiEnabled) {
                startAiCallLoop()
                startScreenshotAnalysis()
            } else {
                stopAiCallLoop()
                stopScreenshotAnalysis()
            }
        }

        textWindowDuration.text = "${yoloTracker.getWindowDurationSeconds()}秒窗口"
    }

    private fun loadModel(): Boolean {
        val config = configManager.loadConfig()
        currentModel = 0
        val cpuGpu = if (useGpu) 1 else 0

        val ret = yolov11Ncnn.loadModel(assets, currentModel, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
            Toast.makeText(this, "模型加载失败", Toast.LENGTH_LONG).show()
            return false
        }

        cachedModelName = config.selectedModel
        cachedLabels = loadLabels(config.selectedModel)
        return true
    }

    private fun updateAiButtonStyle() {
        if (isAiEnabled) {
            buttonToggleAi.text = "AI"
            buttonToggleAi.backgroundTintList = ContextCompat.getColorStateList(this, R.color.capsule_ai_text)
            buttonToggleAi.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            buttonToggleAi.text = "AI"
            buttonToggleAi.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            buttonToggleAi.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
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

    // 截图并保存
    private fun captureAndSave() {
        try {
            val bitmap = Bitmap.createBitmap(cameraView.width, cameraView.height, Bitmap.Config.ARGB_8888)
            saveBitmap(bitmap)
            Toast.makeText(this, "截图已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "OpenIris_${timeStamp}.jpg"
        
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(storageDir, fileName)
        
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        
        Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
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

    // 启动截图分析 - 定期截图并使用 YOLO 检测
    private fun startScreenshotAnalysis() {
        screenshotJob?.cancel()
        screenshotJob = lifecycleScope.launch {
            while (isAiEnabled) {
                delay(1000) // 每秒分析一次
                if (isAiEnabled) {
                    analyzeCurrentFrame()
                }
            }
        }
    }

    private fun stopScreenshotAnalysis() {
        screenshotJob?.cancel()
        screenshotJob = null
    }

    private fun analyzeCurrentFrame() {
        try {
            // 创建空位图用于 YOLO 检测
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            
            // 使用 YOLO 检测
            val rawResults = yolov11Ncnn.detectBitmap(bitmap, currentModel, if (useGpu) 1 else 0)
            
            // 处理检测结果
            val objects = mutableListOf<DetectedObject>()
            var i = 0
            while (i + 5 < rawResults.size) {
                val x = rawResults[i].toFloat()
                val y = rawResults[i + 1].toFloat()
                val w = rawResults[i + 2].toFloat()
                val h = rawResults[i + 3].toFloat()
                val labelIndex = rawResults[i + 4]
                val confidence = rawResults[i + 5] / 1000f

                val label = if (labelIndex in cachedLabels.indices) cachedLabels[labelIndex] else "unknown"

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

            // 更新 YOLO 追踪器
            objects.forEach { obj ->
                yoloTracker.addDetection(obj.label, obj.confidence)
            }

            runOnUiThread {
                updateResultSummary()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        }
    }

    private suspend fun callAiModel() {
        try {
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
            startScreenshotAnalysis()
        }
    }

    override fun onPause() {
        super.onPause()
        yolov11Ncnn.closeCamera()
        stopAiCallLoop()
        stopScreenshotAnalysis()
    }
}

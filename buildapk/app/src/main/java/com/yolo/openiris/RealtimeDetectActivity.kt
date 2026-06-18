package com.yolo.openiris

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.detection.CombinedAnalysisManager
import com.yolo.openiris.detection.SlidingWindowTracker
import com.yolo.openiris.utils.ImageUtils
import com.yolo.openiris.ui.CapsuleView
import com.yolo.openiris.ui.DraggableLayout
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
        private const val REQUEST_STORAGE_PERMISSION = 1001
    }

    // Core components
    private lateinit var yolov11Ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager
    private lateinit var aiModelManager: AiModelManager

    // UI components
    private lateinit var cameraView: SurfaceView
    private lateinit var overlayView: OverlayView
    private lateinit var buttonBack: ImageButton
    private lateinit var buttonCapture: ImageButton
    private lateinit var buttonFullscreen: ImageButton
    private lateinit var buttonShowCapture: ImageButton

    // Card buttons
    private lateinit var cardSwitchCamera: MaterialCardView
    private lateinit var cardToggleGpu: MaterialCardView
    private lateinit var cardToggleAi: MaterialCardView
    private lateinit var textGpuStatus: TextView
    private lateinit var textAiStatusBtn: TextView
    private lateinit var textAiState: TextView

    // Result summary components
    private lateinit var flexboxYolo: FlexboxLayout
    private lateinit var flexboxAi: FlexboxLayout
    private lateinit var flexboxCombined: FlexboxLayout
    private lateinit var textYoloCount: MaterialTextView
    private lateinit var textAiCount: MaterialTextView
    private lateinit var textCombinedCount: MaterialTextView
    private lateinit var textWindowDuration: MaterialTextView

    // 记录开关卡片
    private lateinit var cardToggleRecord: MaterialCardView
    private lateinit var textRecordIcon: TextView
    private lateinit var textRecordState: TextView

    // State
    private var facing = 1
    private var useGpu = true
    private var currentModel = 0
    private var isAiEnabled = false
    private var isFullscreen = false
    private var isRecording = true  // 记录开关，默认开启
    private var cachedLabels: List<String> = emptyList()
    private var cachedModelName: String = ""

    // Detection trackers（改为每帧清空模式，不再使用滑动窗口）
    private val yoloTracker = SlidingWindowTracker(1000)
    private val aiTracker = SlidingWindowTracker(1000)

    // 综合分析管理器（独立管理15秒展示/max标注/平均置信度）
    private val combinedAnalysisManager = CombinedAnalysisManager()

    // Jobs
    private var aiCallJob: Job? = null
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
        // Camera and overlay
        cameraView = findViewById(R.id.cameraView)
        overlayView = findViewById(R.id.overlayView)

        // Top status bar
        buttonBack = findViewById(R.id.buttonBack)
        buttonCapture = findViewById(R.id.buttonCapture)
        buttonFullscreen = findViewById(R.id.buttonFullscreen)
        buttonShowCapture = findViewById(R.id.buttonShowCapture)

        // Card buttons
        cardSwitchCamera = findViewById(R.id.cardSwitchCamera)
        cardToggleGpu = findViewById(R.id.cardToggleGpu)
        cardToggleAi = findViewById(R.id.cardToggleAi)
        cardToggleRecord = findViewById(R.id.cardToggleRecord)
        textGpuStatus = findViewById(R.id.textGpuStatus)
        textAiStatusBtn = findViewById(R.id.textAiStatusBtn)
        textAiState = findViewById(R.id.textAiState)
        textRecordIcon = findViewById(R.id.textRecordIcon)
        textRecordState = findViewById(R.id.textRecordState)

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

        // Initialize button states
        updateGpuButton()
        updateAiButton()
        updateRecordButton()

        // Set click listeners
        buttonBack.setOnClickListener { finish() }
        buttonCapture.setOnClickListener { captureAndSave() }
        buttonFullscreen.setOnClickListener { toggleFullscreen() }
        cameraView.setOnClickListener { toggleFullscreen() }

        cardSwitchCamera.setOnClickListener {
            facing = 1 - facing
            yolov11Ncnn.closeCamera()
            yolov11Ncnn.openCamera(facing)
        }

        cardToggleGpu.setOnClickListener {
            useGpu = !useGpu
            updateGpuButton()
            loadModel()
        }

        cardToggleAi.setOnClickListener {
            isAiEnabled = !isAiEnabled
            updateAiButton()
            if (isAiEnabled) {
                startAiCallLoop()
            } else {
                stopAiCallLoop()
            }
        }

        // 记录开关按钮
        cardToggleRecord.setOnClickListener {
            isRecording = !isRecording
            updateRecordButton()
        }

        // 右侧控制面板拖动（使用 DraggableLayout 处理长按+拖动，子视图点击正常工作）
        val rightControlPanel = findViewById<DraggableLayout>(R.id.rightControlPanel)
        rightControlPanel.onDragListener = { dx, dy ->
            val newX = rightControlPanel.translationX + dx
            val newY = rightControlPanel.translationY + dy

            // 边界约束：限制在标题栏与检测结果汇总窗口之间
            val topStatusBar = findViewById<View>(R.id.topStatusBar)
            val resultSummaryCard = findViewById<MaterialCardView>(R.id.resultSummaryCard)
            val minY = (topStatusBar.bottom - rightControlPanel.top).toFloat()
            val maxY = (resultSummaryCard.top - rightControlPanel.bottom).toFloat()

            // 水平边界约束：不超出屏幕左右范围
            val screenWidth = resources.displayMetrics.widthPixels
            val minX = -rightControlPanel.left.toFloat()
            val maxX = (screenWidth - rightControlPanel.right).toFloat()

            rightControlPanel.translationX = newX.coerceIn(minX, maxX)
            rightControlPanel.translationY = newY.coerceIn(minY, maxY)
        }

        // 监听结果汇总窗口大小变化，自适应移动按钮栏
        val resultSummaryCard = findViewById<MaterialCardView>(R.id.resultSummaryCard)
        resultSummaryCard.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) {
                // 结果汇总窗口大小变化时，检查是否遮挡按钮栏
                val panelBottom = rightControlPanel.bottom + rightControlPanel.translationY
                val summaryTop = resultSummaryCard.top.toFloat()
                if (panelBottom > summaryTop) {
                    // 按钮栏被遮挡，向上移动
                    val targetY = rightControlPanel.translationY - (panelBottom - summaryTop) - 16f
                    val topStatusBar = findViewById<View>(R.id.topStatusBar)
                    val minY = (topStatusBar.bottom - rightControlPanel.top).toFloat()
                    rightControlPanel.translationY = targetY.coerceAtLeast(minY)
                }
            }
        }

        // Update window duration text
        textWindowDuration.text = "实时刷新"

        // 限制结果卡片高度为屏幕40%
        val displayMetrics = resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.4).toInt()
        resultSummaryCard.post {
            if (resultSummaryCard.height > maxHeight) {
                val layoutParams = resultSummaryCard.layoutParams
                layoutParams.height = maxHeight
                resultSummaryCard.layoutParams = layoutParams
            }
        }
    }

    private fun loadModel(): Boolean {
        val config = configManager.loadConfig()
        currentModel = 0
        val cpuGpu = if (useGpu) 1 else 0

        Log.d(TAG, "Loading model: selectedModel=${config.selectedModel}, gpu=$useGpu")
        val ret = yolov11Ncnn.loadModel(assets, currentModel, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
            Toast.makeText(this, "模型加载失败", Toast.LENGTH_LONG).show()
            return false
        }

        cachedModelName = config.selectedModel
        cachedLabels = loadLabels(config.selectedModel)
        Log.d(TAG, "Model loaded: labels=${cachedLabels.size}, names=${cachedLabels.take(5)}")
        return true
    }

    // 颜色对比度计算
    private fun getContrastColor(backgroundColor: Int): Int {
        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)
        // 计算相对亮度 (W3C 标准)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    // 更新 GPU 按钮状态
    private fun updateGpuButton() {
        val color = if (useGpu) {
            ContextCompat.getColor(this, R.color.btn_gpu_enabled)
        } else {
            ContextCompat.getColor(this, R.color.btn_gpu_disabled)
        }
        cardToggleGpu.setCardBackgroundColor(color)
        textGpuStatus.text = if (useGpu) "GPU" else "CPU"
        textGpuStatus.setTextColor(getContrastColor(color))
    }

    // 更新 AI 按钮状态
    private fun updateAiButton() {
        val color = if (isAiEnabled) {
            ContextCompat.getColor(this, R.color.btn_ai_enabled)
        } else {
            ContextCompat.getColor(this, R.color.btn_ai_disabled)
        }
        cardToggleAi.setCardBackgroundColor(color)
        textAiState.text = if (isAiEnabled) "开启" else "关闭"
        textAiState.setTextColor(getContrastColor(color))
        textAiStatusBtn.setTextColor(getContrastColor(color))
    }

    // 更新记录按钮状态
    private fun updateRecordButton() {
        val color = if (isRecording) {
            ContextCompat.getColor(this, R.color.btn_ai_enabled)
        } else {
            ContextCompat.getColor(this, R.color.btn_ai_disabled)
        }
        cardToggleRecord.setCardBackgroundColor(color)
        textRecordIcon.text = if (isRecording) "⏸" else "▶"
        textRecordState.text = if (isRecording) "暂停" else "开始"
        textRecordState.setTextColor(getContrastColor(color))
        textRecordIcon.setTextColor(getContrastColor(color))
    }

    // 截图并保存
    private fun captureAndSave() {
        var bitmap: Bitmap? = null
        try {
            val config = configManager.loadConfig()
            bitmap = Bitmap.createBitmap(
                config.cameraResolutionWidth,
                config.cameraResolutionHeight,
                Bitmap.Config.ARGB_8888
            )
            
            if (yolov11Ncnn.captureFrame(bitmap)) {
                // 运行YOLO检测获取检测框
                val detections = runYoloDetection(bitmap)
                
                // 在帧上绘制检测框
                val annotatedBitmap = ImageUtils.drawDetections(bitmap, detections)
                
                saveBitmap(annotatedBitmap)
                
                // 如果开启了截图预览，显示浮窗
                if (config.showCapturePreview) {
                    showCapturePreview(annotatedBitmap)
                    bitmap = null // dialog会持有bitmap引用，不在这里回收
                }
                
                Toast.makeText(this, "截图已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "截图失败：无法获取当前帧", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // 如果bitmap没有被dialog持有，则回收
            bitmap?.recycle()
        }
    }
    
    private fun runYoloDetection(bitmap: Bitmap): List<DetectedObject> {
        val rawResults = yolov11Ncnn.detectBitmap(bitmap, currentModel, if (useGpu) 1 else 0)
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
        
        return objects
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "OpenIris_${timeStamp}.jpg"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore 保存到公共相册
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenIris")
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Log.d(TAG, "Screenshot saved to MediaStore: $uri")
            } else {
                Log.e(TAG, "Failed to create MediaStore entry")
            }
        } else {
            // Android 9 及以下需要 WRITE_EXTERNAL_STORAGE 权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
                Toast.makeText(this, "需要存储权限才能保存截图", Toast.LENGTH_SHORT).show()
                return
            }
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val storageDir = File(baseDir, "OpenIris").apply { mkdirs() }
            val file = File(storageDir, fileName)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
        }
    }

    private var capturePreviewDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private val capturePreviewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val capturePreviewRunnable = Runnable {
        capturePreviewDialog?.let { dialog ->
            if (dialog.isShowing && !isFinishing && !isDestroyed) {
                dialog.dismiss()
            }
        }
        capturePreviewDialog = null
    }

    private fun showCapturePreview(bitmap: Bitmap) {
        // 先关闭之前的对话框
        capturePreviewDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        capturePreviewHandler.removeCallbacks(capturePreviewRunnable)

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(R.layout.layout_capture_preview)
        
        val imageView = dialog.findViewById<android.widget.ImageView>(R.id.imageCapturePreview)
        val textDetections = dialog.findViewById<MaterialTextView>(R.id.textCaptureDetections)
        
        imageView?.setImageBitmap(bitmap)
        
        // 显示当前检测结果摘要
        val yoloSummary = yoloTracker.getSortedSummary()
        if (yoloSummary.isNotEmpty()) {
            val summaryText = yoloSummary.take(3).joinToString("、") { "${it.name} x${it.count}" }
            textDetections?.text = summaryText
        } else {
            textDetections?.text = "无检测结果"
        }
        
        capturePreviewDialog = dialog
        dialog.show()
        
        // 2秒后自动关闭
        capturePreviewHandler.postDelayed(capturePreviewRunnable, 2000)
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

    private fun startScreenshotAnalysis() {
        screenshotJob?.cancel()
        screenshotJob = lifecycleScope.launch {
            // 等待摄像头准备好（首次延迟 2 秒）
            delay(2000)
            while (true) {
                if (isRecording) {
                    analyzeCurrentFrame()
                }
                delay(1000)
            }
        }
    }

    private fun stopScreenshotAnalysis() {
        screenshotJob?.cancel()
        screenshotJob = null
    }

    private fun analyzeCurrentFrame() {
        try {
            // 直接从摄像头帧运行检测（避免两次像素复制）
            val rawResults = yolov11Ncnn.detectCurrentFrame(currentModel, if (useGpu) 1 else 0)
            Log.d(TAG, "detectCurrentFrame returned ${rawResults.size} values, model=$currentModel, gpu=${if (useGpu) 1 else 0}, labels=${cachedLabels.size}")

            if (rawResults.isEmpty()) {
                Log.w(TAG, "No detection results - camera may not be ready or no objects detected")
                return
            }

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

            // 使用 clearAndReplace 替代 addDetection：每帧清空旧数据，只保留当前帧结果
            yoloTracker.clearAndReplace(objects.map { it.label to it.confidence })
            Log.d(TAG, "YOLO detected ${objects.size} objects: ${objects.map { it.label }.distinct()}")

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
                // 使用 clearAndReplace：每次 AI 推理清空旧数据
                aiTracker.clearAndReplace(output.objects.map { it.name to it.confidence })

                runOnUiThread {
                    updateResultSummary()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI call failed", e)
        }
    }

    private fun updateResultSummary() {
        // 暂停时冻结显示，不更新
        if (!isRecording) return

        // 获取当前帧 YOLO 和 AI 结果
        val yoloSummary = yoloTracker.getSortedSummary()
        val aiSummary = aiTracker.getSortedSummary()

        // Update YOLO capsules（常规方案，无 max 标注）
        Log.d(TAG, "updateResultSummary: yoloSummary=${yoloSummary.size} items, hasData=${yoloTracker.hasData()}")
        flexboxYolo.removeAllViews()
        yoloSummary.forEach { stats ->
            val capsule = CapsuleView(this)
            capsule.bindRecognizedObject(stats.name, stats.count, stats.avgConfidence, CapsuleView.CapsuleSource.YOLO)
            flexboxYolo.addView(capsule)
        }
        textYoloCount.text = "${yoloTracker.getUniqueCount()} 类"

        // Update AI capsules（常规方案，无 max 标注）
        flexboxAi.removeAllViews()
        aiSummary.forEach { stats ->
            val capsule = CapsuleView(this)
            capsule.bindRecognizedObject(stats.name, stats.count, stats.avgConfidence, CapsuleView.CapsuleSource.AI)
            flexboxAi.addView(capsule)
        }
        textAiCount.text = "${aiTracker.getUniqueCount()} 类"

        // Update combined capsules（使用 CombinedAnalysisManager 管理15秒展示/max标注/平均置信度）
        val yoloTriples = yoloSummary.map { Triple(it.name, it.count, it.avgConfidence) }
        val aiTriples = aiSummary.map { Triple(it.name, it.count, it.avgConfidence) }
        val combinedEntries = combinedAnalysisManager.refresh(yoloTriples, aiTriples)

        flexboxCombined.removeAllViews()
        combinedEntries.forEach { entry ->
            val stats = SlidingWindowTracker.ObjectStats.fromAvgConfidence(entry.name, entry.maxCount, entry.avgConfidence)
            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.COMBINED, maxCount = entry.maxCount, isMax = entry.isMax)
            flexboxCombined.addView(capsule)
        }
        textCombinedCount.text = "${combinedEntries.size} 类"

        // 自适应移动：检查按钮栏是否被结果卡片遮挡
        val rightControlPanel = findViewById<DraggableLayout>(R.id.rightControlPanel)
        val resultSummaryCard = findViewById<MaterialCardView>(R.id.resultSummaryCard)
        if (rightControlPanel != null && resultSummaryCard != null) {
            resultSummaryCard.post {
                val panelBottom = rightControlPanel.bottom + rightControlPanel.translationY
                val summaryTop = resultSummaryCard.top.toFloat()
                if (panelBottom > summaryTop) {
                    val topStatusBar = findViewById<View>(R.id.topStatusBar)
                    val minY = (topStatusBar.bottom - rightControlPanel.top).toFloat()
                    val targetY = rightControlPanel.translationY - (panelBottom - summaryTop) - 16f
                    rightControlPanel.translationY = targetY.coerceAtLeast(minY)
                }
            }
        }
    }

    private fun loadLabels(modelName: String): List<String> {
        return try {
            assets.open("models/$modelName/labels.txt").use { it.bufferedReader().readLines().filter { line -> line.isNotBlank() } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
            emptyList()
        }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val resultSummaryCard = findViewById<View>(R.id.resultSummaryCard)
        val topStatusBar = findViewById<View>(R.id.topStatusBar)
        val rightControlPanel = findViewById<View>(R.id.rightControlPanel)

        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            animateBottomPanelOut(resultSummaryCard)
            animateToolbarOut(topStatusBar)
            animateToolbarOut(rightControlPanel)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            animateBottomPanelIn(resultSummaryCard)
            animateToolbarIn(topStatusBar)
            animateToolbarIn(rightControlPanel)
        }
    }

    private fun animateBottomPanelOut(view: View) {
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        val translationY = ObjectAnimator.ofFloat(view, "translationY", 0f, view.height.toFloat())
        val set = AnimatorSet()
        set.playTogether(alpha, translationY)
        set.duration = 350
        set.interpolator = DecelerateInterpolator(1.5f)
        set.start()
    }

    private fun animateBottomPanelIn(view: View) {
        view.alpha = 0f
        view.translationY = view.height.toFloat()
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val translationY = ObjectAnimator.ofFloat(view, "translationY", view.height.toFloat(), 0f)
        val set = AnimatorSet()
        set.playTogether(alpha, translationY)
        set.duration = 350
        set.interpolator = DecelerateInterpolator(1.5f)
        set.start()
    }

    private fun animateToolbarOut(view: View) {
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        alpha.duration = 300
        alpha.start()
    }

    private fun animateToolbarIn(view: View) {
        view.alpha = 0f
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        alpha.duration = 300
        alpha.start()
    }

    // SurfaceHolder.Callback
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        yolov11Ncnn.setOutputWindow(holder.surface)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已授予，请重新截图", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法保存截图", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 应用分辨率配置
        val config = configManager.loadConfig()
        yolov11Ncnn.setCameraResolution(config.cameraResolutionWidth, config.cameraResolutionHeight)

        yolov11Ncnn.openCamera(facing)
        // YOLO 始终运行
        startScreenshotAnalysis()
        if (isAiEnabled) {
            startAiCallLoop()
        }
    }

    override fun onPause() {
        super.onPause()
        yolov11Ncnn.closeCamera()
        stopAiCallLoop()
        stopScreenshotAnalysis()
        
        // 清理浮窗
        capturePreviewHandler.removeCallbacks(capturePreviewRunnable)
        capturePreviewDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        capturePreviewDialog = null
    }
}

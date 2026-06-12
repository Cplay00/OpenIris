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
import com.yolo.openiris.detection.SlidingWindowTracker
import com.yolo.openiris.utils.ImageUtils
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
        textGpuStatus = findViewById(R.id.textGpuStatus)
        textAiStatusBtn = findViewById(R.id.textAiStatusBtn)
        textAiState = findViewById(R.id.textAiState)

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
                startScreenshotAnalysis()
            } else {
                stopAiCallLoop()
                stopScreenshotAnalysis()
            }
        }

        // Update window duration text
        textWindowDuration.text = "${yoloTracker.getWindowDurationSeconds()}绉掔獥鍙?
    }

    private fun loadModel(): Boolean {
        val config = configManager.loadConfig()
        currentModel = 0
        val cpuGpu = if (useGpu) 1 else 0

        val ret = yolov11Ncnn.loadModel(assets, currentModel, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
            Toast.makeText(this, "妯″瀷鍔犺浇澶辫触", Toast.LENGTH_LONG).show()
            return false
        }

        cachedModelName = config.selectedModel
        cachedLabels = loadLabels(config.selectedModel)
        return true
    }

    // 棰滆壊瀵规瘮搴﹁绠?
    private fun getContrastColor(backgroundColor: Int): Int {
        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)
        // 璁$畻鐩稿浜害 (W3C 鏍囧噯)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    // 鏇存柊 GPU 鎸夐挳鐘舵€?
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

    // 鏇存柊 AI 鎸夐挳鐘舵€?
    private fun updateAiButton() {
        val color = if (isAiEnabled) {
            ContextCompat.getColor(this, R.color.btn_ai_enabled)
        } else {
            ContextCompat.getColor(this, R.color.btn_ai_disabled)
        }
        cardToggleAi.setCardBackgroundColor(color)
        textAiState.text = if (isAiEnabled) "寮€鍚? else "鍏抽棴"
        textAiState.setTextColor(getContrastColor(color))
        textAiStatusBtn.setTextColor(getContrastColor(color))
    }

    // 鎴浘骞朵繚瀛?
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
                // 杩愯YOLO妫€娴嬭幏鍙栨娴嬫
                val detections = runYoloDetection(bitmap)
                
                // 鍦ㄥ抚涓婄粯鍒舵娴嬫
                val annotatedBitmap = ImageUtils.drawDetections(bitmap, detections)
                
                saveBitmap(annotatedBitmap)
                
                // 濡傛灉寮€鍚簡鎴浘棰勮锛屾樉绀烘诞绐?
                if (config.showCapturePreview) {
                    showCapturePreview(annotatedBitmap)
                    bitmap = null // dialog浼氭寔鏈塨itmap寮曠敤锛屼笉鍦ㄨ繖閲屽洖鏀?
                } else {
                    annotatedBitmap.recycle()
                }
                
                Toast.makeText(this, "鎴浘宸蹭繚瀛?, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "鎴浘澶辫触锛氭棤娉曡幏鍙栧綋鍓嶅抚", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            Toast.makeText(this, "鎴浘澶辫触: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            bitmap?.recycle()
            // 濡傛灉bitmap娌℃湁琚玠ialog鎸佹湁锛屽垯鍥炴敹
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "缂哄皯鍐欏叆瀛樺偍鏉冮檺锛屾棤娉曚繚瀛樻埅鍥?, Toast.LENGTH_SHORT).show()
        }

        val configPath = configManager.getImageExportPath()
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        val storageDir = if (configPath.isNotBlank() && configPath != "Pictures") {
            File(baseDir, configPath).apply { mkdirs() }
        } else {
            File(baseDir, "OpenIris").apply { mkdirs() }
        }
        val file = File(storageDir, fileName)

        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
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
        // 鍏堝叧闂箣鍓嶇殑瀵硅瘽妗?
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
        
        // 鏄剧ず褰撳墠妫€娴嬬粨鏋滄憳瑕?
        val yoloSummary = yoloTracker.getSortedSummary()
        if (yoloSummary.isNotEmpty()) {
            val summaryText = yoloSummary.take(3).joinToString("銆?) { "${it.name} x${it.count}" }
            textDetections?.text = summaryText
        } else {
            textDetections?.text = "鏃犳娴嬬粨鏋?
        }
        
        capturePreviewDialog = dialog
        dialog.show()
        
        // 2绉掑悗鑷姩鍏抽棴
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
            while (isAiEnabled) {
                delay(1000)
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
            // 浣跨敤 captureFrame 鑾峰彇褰撳墠鎽勫儚澶村抚锛岃€岄潪鍒涘缓绌虹櫧 bitmap
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            if (!yolov11Ncnn.captureFrame(bitmap)) {
                bitmap.recycle()
                return
            }

            val rawResults = yolov11Ncnn.detectBitmap(bitmap, currentModel, if (useGpu) 1 else 0)
            bitmap.recycle()

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
                val yoloText = yoloSummary.joinToString("銆?) { "${it.name}(${it.count}涓?" }
                "褰撳墠鐢婚潰 YOLO 妫€娴嬪埌锛?yoloText銆傝鍩轰簬浠ヤ笂妫€娴嬬粨鏋滐紝琛ュ厖璇嗗埆鐢婚潰涓殑鍏朵粬鐗╀綋锛屼互JSON鏍煎紡杩斿洖缁撴灉銆?
            } else {
                "璇疯瘑鍒浘鐗囦腑鐨勭墿浣擄紝浠SON鏍煎紡杩斿洖缁撴灉銆?
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
        textYoloCount.text = "${yoloTracker.getUniqueCount()} 绫?

        // Update AI capsules
        val aiSummary = aiTracker.getSortedSummary()
        flexboxAi.removeAllViews()
        aiSummary.forEach { stats ->
            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.AI)
            flexboxAi.addView(capsule)
        }
        textAiCount.text = "${aiTracker.getUniqueCount()} 绫?

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
        textCombinedCount.text = "${combinedMap.size} 绫?
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

    override fun onResume() {
        super.onResume()
        
        // 搴旂敤鍒嗚鲸鐜囬厤缃?
        val config = configManager.loadConfig()
        yolov11Ncnn.setCameraResolution(config.cameraResolutionWidth, config.cameraResolutionHeight)
        
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
        
        // 娓呯悊娴獥
        capturePreviewHandler.removeCallbacks(capturePreviewRunnable)
        capturePreviewDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        capturePreviewDialog = null
    }
}

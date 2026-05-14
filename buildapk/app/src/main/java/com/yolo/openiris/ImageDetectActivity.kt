package com.yolo.openiris

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.*
import com.yolo.openiris.export.ImageExporter
import com.yolo.openiris.export.JsonExporter
import com.github.chrisbanes.photoview.PhotoView
import com.yolo.openiris.ui.CapsuleView
import com.yolo.openiris.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageDetectActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OpenIris-ImageDetect"
    }

    // Core components
    private lateinit var yolov11Ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager
    private lateinit var aiModelManager: AiModelManager

    // UI components
    private lateinit var imageView: PhotoView
    private lateinit var textStatus: MaterialTextView
    private lateinit var buttonBack: ImageButton
    private lateinit var buttonFullscreen: ImageButton
    private lateinit var switchAi: MaterialSwitch
    private lateinit var buttonSelectImage: MaterialButton
    private lateinit var buttonTakePhoto: MaterialButton
    private lateinit var buttonExportJson: MaterialButton
    private lateinit var buttonExportImage: MaterialButton

    // Result summary components
    private lateinit var flexboxYolo: FlexboxLayout
    private lateinit var flexboxAi: FlexboxLayout
    private lateinit var flexboxCombined: FlexboxLayout

    // State
    private var isFullscreen = false
    private var isAiEnabled = false

    // Data
    private var originalBitmap: Bitmap? = null
    private var annotatedBitmap: Bitmap? = null
    private var analysisResult: AnalysisResult? = null
    private var lastAiOutput: com.yolo.openiris.ai.StructuredOutput? = null
    private var photoUri: Uri? = null

    // Activity result launchers
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { loadImageFromUri(it) }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { loadImageFromUri(it) }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePhoto()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detect)

        configManager = ConfigManager.getInstance(this)
        aiModelManager = AiModelManager.getInstance(this)
        yolov11Ncnn = Yolov11Ncnn()

        initViews()
        if (!loadModel()) {
            Toast.makeText(this, "模型加载失败", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        imageView = findViewById(R.id.imageView)
        textStatus = findViewById(R.id.textStatus)
        buttonBack = findViewById(R.id.buttonBack)
        buttonFullscreen = findViewById(R.id.buttonFullscreen)
        switchAi = findViewById(R.id.switchAi)
        buttonSelectImage = findViewById(R.id.buttonSelectImage)
        buttonTakePhoto = findViewById(R.id.buttonTakePhoto)
        buttonExportJson = findViewById(R.id.buttonExportJson)
        buttonExportImage = findViewById(R.id.buttonExportImage)
        flexboxYolo = findViewById(R.id.flexboxYolo)
        flexboxAi = findViewById(R.id.flexboxAi)
        flexboxCombined = findViewById(R.id.flexboxCombined)

        buttonBack.setOnClickListener { finish() }
        buttonFullscreen.setOnClickListener { toggleFullscreen() }
        imageView.setOnClickListener { toggleFullscreen() }

        switchAi.setOnCheckedChangeListener { _, isChecked ->
            isAiEnabled = isChecked
        }

        buttonSelectImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        buttonTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        buttonExportJson.setOnClickListener { exportJson() }
        buttonExportImage.setOnClickListener { exportImage() }
    }

    private fun loadModel(): Boolean {
        val cpuGpu = if (configManager.loadConfig().useGpu) 1 else 0
        val ret = yolov11Ncnn.loadModel(assets, 0, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
            textStatus.text = "模型加载失败"
            return false
        }
        return true
    }

    private fun takePhoto() {
        val photoFile = createImageFile()
        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // 拍照临时文件使用应用私有目录（FileProvider 需要）
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 支持 HEIF 格式
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                bmp
            }

            if (bitmap != null) {
                processImage(bitmap)
            } else {
                textStatus.text = "无法加载图片"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image", e)
            textStatus.text = "加载图片失败: ${e.message}"
        }
    }

    private fun processImage(bitmap: Bitmap) {
        originalBitmap?.recycle()
        annotatedBitmap?.recycle()
        originalBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        startDetection()
    }

    private fun startDetection() {
        val bitmap = originalBitmap ?: return

        textStatus.text = "正在检测..."

        lifecycleScope.launch {
            try {
                val yoloResult = withContext(Dispatchers.Default) {
                    runYoloDetection(bitmap)
                }

                displayYoloResults(yoloResult)
                annotatedBitmap = ImageUtils.drawDetections(bitmap, yoloResult.objects)
                imageView.setImageBitmap(annotatedBitmap)

                // 先赋值 analysisResult，确保 updateCombinedResults() 能读到 YOLO 结果
                analysisResult = AnalysisResult.fromResults(
                    mode = DetectionMode.IMAGE,
                    yoloResult = yoloResult
                )

                if (isAiEnabled) {
                    textStatus.text = "正在进行 AI 识别..."
                    Log.d(TAG, "Starting AI detection...")
                    try {
                        val imageBase64 = aiModelManager.bitmapToBase64(bitmap)
                        Log.d(TAG, "Image encoded to base64, length: ${imageBase64.length}")
                        
                        val aiResult = aiModelManager.callWithFallbackAndImage(
                            prompt = "请识别图片中的物体，以JSON格式返回结果。",
                            imageBase64 = imageBase64
                        )

                        Log.d(TAG, "AI result: success=${aiResult.success}, content length=${aiResult.content?.length}, structuredOutput=${aiResult.structuredOutput != null}, error=${aiResult.error}")

                        if (aiResult.success) {
                            if (aiResult.structuredOutput != null) {
                                lastAiOutput = aiResult.structuredOutput
                                displayAiResults(aiResult.structuredOutput)
                                Log.d(TAG, "AI detection succeeded with structured output")
                            } else {
                                // AI调用成功但无法解析为结构化输出
                                Log.w(TAG, "AI returned non-JSON response: ${aiResult.content?.take(200)}")
                                textStatus.text = "AI 识别完成（非结构化结果）"
                            }
                        } else {
                            val errorMessage = aiResult.error ?: "未知错误，请检查模型配置"
                            Log.e(TAG, "AI detection failed: $errorMessage")
                            Toast.makeText(this@ImageDetectActivity, "AI 识别失败: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AI detection exception", e)
                        Toast.makeText(this@ImageDetectActivity, "AI 识别异常: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                    }
                }

                textStatus.text = "检测完成"

            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
                textStatus.text = "检测失败: ${e.message}"
            }
        }
    }

    private fun runYoloDetection(bitmap: Bitmap): DetectionResult {
        val rawResults = yolov11Ncnn.detectBitmap(bitmap, 0, 0)
        val objects = mutableListOf<DetectedObject>()

        val config = configManager.loadConfig()
        val labels = loadLabels(config.selectedModel)

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

        return DetectionResult(
            source = "yolo",
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            objects = objects
        )
    }

    private fun loadLabels(modelName: String): List<String> {
        return try {
            assets.open("models/$modelName/labels.txt").use { it.bufferedReader().readLines().filter { line -> line.isNotBlank() } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
            emptyList()
        }
    }

    private fun displayYoloResults(result: DetectionResult) {
        flexboxYolo.removeAllViews()

        val countByLabel = result.countByLabel()
        countByLabel.forEach { (label, count) ->
            val avgConfidence = result.objects
                .filter { it.label == label }
                .map { it.confidence }
                .average()
                .toFloat()

            val stats = SlidingWindowTracker.ObjectStats(
                name = label,
                count = count,
                totalConfidence = avgConfidence * count
            )

            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.YOLO)
            flexboxYolo.addView(capsule)
        }
    }

    private fun displayAiResults(output: com.yolo.openiris.ai.StructuredOutput) {
        flexboxAi.removeAllViews()

        output.objects.forEach { obj ->
            val capsule = CapsuleView(this)
            capsule.bindRecognizedObject(
                name = obj.getDisplayName(),
                count = obj.count,
                confidence = obj.confidence,
                source = CapsuleView.CapsuleSource.AI
            )
            flexboxAi.addView(capsule)
        }

        updateCombinedResults()
    }

    private fun updateCombinedResults() {
        flexboxCombined.removeAllViews()

        val combinedMap = mutableMapOf<String, Pair<Int, Float>>()

        // 合入 YOLO 结果
        val yoloResult = analysisResult?.yoloResult
        if (yoloResult != null) {
            yoloResult.countByLabel().forEach { (label, count) ->
                val avgConfidence = yoloResult.objects
                    .filter { it.label == label }
                    .map { it.confidence }
                    .average()
                    .toFloat()
                combinedMap[label] = Pair(count, avgConfidence)
            }
        }

        // 合入 AI 结果（支持同名标识合并）
        val aiOutput = lastAiOutput
        if (aiOutput != null) {
            aiOutput.objects.forEach { obj ->
                val aiName = obj.getDisplayName()
                // 检查是否与YOLO结果中的某个标识匹配（模糊匹配：去除括号内容后比较）
                val matchingKey = combinedMap.keys.find { existingKey ->
                    val normalizedExisting = existingKey.replace(Regex("（[^）]*）"), "").trim()
                    val normalizedAi = aiName.replace(Regex("（[^）]*）"), "").trim()
                    normalizedExisting.equals(normalizedAi, ignoreCase = true) ||
                    normalizedExisting.contains(normalizedAi) ||
                    normalizedAi.contains(normalizedExisting)
                }

                if (matchingKey != null) {
                    // 找到匹配的YOLO结果，使用YOLO的置信度
                    val existing = combinedMap[matchingKey]!!
                    combinedMap[matchingKey] = Pair(
                        existing.first + obj.count,  // 累加数量
                        existing.second  // 保留YOLO的置信度
                    )
                } else {
                    // 没有匹配的YOLO结果，直接添加AI结果
                    combinedMap[aiName] = Pair(obj.count, obj.confidence)
                }
            }
        }

        combinedMap.forEach { (label, pair) ->
            val stats = SlidingWindowTracker.ObjectStats(
                name = label,
                count = pair.first,
                totalConfidence = pair.second * pair.first
            )

            val capsule = CapsuleView(this)
            capsule.bind(stats, CapsuleView.CapsuleSource.COMBINED)
            flexboxCombined.addView(capsule)
        }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val bottomPanel = findViewById<View>(R.id.bottomPanel)
        val topToolbar = findViewById<View>(R.id.topToolbar)

        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            animateBottomPanelOut(bottomPanel)
            animateToolbarOut(topToolbar)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            animateBottomPanelIn(bottomPanel)
            animateToolbarIn(topToolbar)
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

    private fun exportJson() {
        val result = analysisResult
        if (result == null) {
            Toast.makeText(this, "没有检测结果可导出", Toast.LENGTH_SHORT).show()
            return
        }

        val exportResult = JsonExporter.export(this, result)
        if (exportResult.success) {
            Toast.makeText(this, "JSON 已导出: ${exportResult.filePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败: ${exportResult.errorMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportImage() {
        val bitmap = annotatedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "没有标注图片可导出", Toast.LENGTH_SHORT).show()
            return
        }

        val exportResult = ImageExporter.export(this, bitmap)
        if (exportResult.success) {
            Toast.makeText(this, "图片已导出: ${exportResult.filePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败: ${exportResult.errorMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

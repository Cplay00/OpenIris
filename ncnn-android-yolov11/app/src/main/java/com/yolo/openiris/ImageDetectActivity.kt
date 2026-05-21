package com.yolo.openiris

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.AnalysisResult
import com.yolo.openiris.detection.DetectionMode
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.export.ImageExporter
import com.yolo.openiris.export.JsonExporter
import com.yolo.openiris.fusion.ResultFusion
import com.yolo.openiris.llm.LlmClient
import com.yolo.openiris.utils.ImageUtils
import com.yolo.openiris.vlm.VlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageDetectActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OpenIris-ImageDetect"
    }

    private lateinit var imageView: ImageView
    private lateinit var textStatus: TextView
    private lateinit var textYoloResult: TextView
    private lateinit var textVlmResult: TextView
    private lateinit var textSummary: TextView
    private lateinit var buttonExportJson: Button
    private lateinit var buttonExportImage: Button

    private lateinit var yolov11ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager

    private var originalBitmap: Bitmap? = null
    private var annotatedBitmap: Bitmap? = null
    private var analysisResult: AnalysisResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detect)

        yolov11ncnn = Yolov11Ncnn()
        configManager = ConfigManager.getInstance(this)

        initViews()
        loadModel()
        loadImage()
    }

    private fun initViews() {
        imageView = findViewById(R.id.imageView)
        textStatus = findViewById(R.id.textStatus)
        textYoloResult = findViewById(R.id.textYoloResult)
        textVlmResult = findViewById(R.id.textVlmResult)
        textSummary = findViewById(R.id.textSummary)
        buttonExportJson = findViewById(R.id.buttonExportJson)
        buttonExportImage = findViewById(R.id.buttonExportImage)

        buttonExportJson.setOnClickListener { exportJson() }
        buttonExportImage.setOnClickListener { exportImage() }
    }

    private fun loadModel() {
        val config = configManager.loadConfig()
        val modelId = if (config.selectedModel == "yolov11s") 1 else 0
        val cpuGpu = if (config.useGpu) 1 else 0
        val ret = yolov11ncnn.loadModel(assets, modelId, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
            textStatus.text = "模型加载失败"
        }
    }

    private fun loadImage() {
        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString == null) {
            textStatus.text = "未选择图片"
            return
        }

        val imageUri = Uri.parse(imageUriString)
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                imageView.setImageBitmap(originalBitmap)
                startDetection()
            } else {
                textStatus.text = "无法加载图片"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image", e)
            textStatus.text = "加载图片失败: ${e.message}"
        }
    }

    private fun startDetection() {
        val bitmap = originalBitmap ?: return

        textStatus.text = "正在进行 YOLO 检测..."

        lifecycleScope.launch {
            try {
                val yoloResult = withContext(Dispatchers.Default) {
                    runYoloDetection(bitmap)
                }

                displayYoloResult(yoloResult)
                annotatedBitmap = ImageUtils.drawDetections(bitmap, yoloResult.objects)
                imageView.setImageBitmap(annotatedBitmap)

                textStatus.text = "正在进行 VLM 识别..."
                val config = configManager.loadConfig()

                if (config.apiKey.isNotBlank()) {
                    try {
                        val vlmClient = VlmClient.getInstance(config)
                        val vlmResult = withContext(Dispatchers.IO) {
                            vlmClient.recognize(bitmap)
                        }
                        displayVlmResult(vlmResult)

                        textStatus.text = "正在进行 LLM 融合..."
                        val llmClient = LlmClient.getInstance(config)
                        val llmResult = withContext(Dispatchers.IO) {
                            llmClient.fuse(yoloResult, vlmResult)
                        }

                        analysisResult = AnalysisResult.fromResults(
                            mode = DetectionMode.IMAGE,
                            yoloResult = yoloResult,
                            vlmResult = vlmResult,
                            llmResult = llmResult
                        )

                        displaySummary(llmResult.summary)
                        textStatus.text = "检测完成"

                    } catch (e: Exception) {
                        Log.e(TAG, "VLM/LLM failed", e)
                        textStatus.text = "VLM/LLM 调用失败: ${e.message}"

                        analysisResult = AnalysisResult.fromResults(
                            mode = DetectionMode.IMAGE,
                            yoloResult = yoloResult
                        )
                        displaySummary("VLM/LLM 不可用，仅显示 YOLO 结果")
                    }
                } else {
                    textStatus.text = "未配置 API Key，仅显示 YOLO 结果"
                    analysisResult = AnalysisResult.fromResults(
                        mode = DetectionMode.IMAGE,
                        yoloResult = yoloResult
                    )
                    displaySummary("请在设置中配置 API Key 以启用 VLM/LLM 功能")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
                textStatus.text = "检测失败: ${e.message}"
            }
        }
    }

    private fun runYoloDetection(bitmap: Bitmap): DetectionResult {
        val rawResults = yolov11ncnn.detectBitmap(bitmap, 0, 0)
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
            val inputStream = assets.open("models/$modelName/labels.txt")
            val labels = inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
            inputStream.close()
            labels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
            emptyList()
        }
    }

    private fun displayYoloResult(result: DetectionResult) {
        val sb = StringBuilder()
        sb.appendLine("YOLO 检测结果:")
        sb.appendLine("检测到 ${result.objects.size} 个对象")
        val countByLabel = result.countByLabel()
        countByLabel.forEach { (label, count) ->
            sb.appendLine("  - $label: $count 个")
        }
        textYoloResult.text = sb.toString()
    }

    private fun displayVlmResult(result: com.yolo.openiris.vlm.VlmResult) {
        val sb = StringBuilder()
        sb.appendLine("VLM 识别结果:")
        sb.appendLine("场景: ${result.sceneSummary}")
        result.objects.forEach { obj ->
            sb.appendLine("  - ${obj.name}: ${obj.count} 个")
        }
        textVlmResult.text = sb.toString()
    }

    private fun displaySummary(summary: String) {
        textSummary.text = "融合摘要:
$summary"
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

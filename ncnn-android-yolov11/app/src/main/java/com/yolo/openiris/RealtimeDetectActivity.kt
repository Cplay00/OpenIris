package com.yolo.openiris

import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.ui.OverlayView
import com.yolo.openiris.vlm.VlmClient
import com.yolo.openiris.vlm.VlmResult
import com.yolo.openiris.vlm.VlmScheduler

class RealtimeDetectActivity : AppCompatActivity(), SurfaceHolder.Callback,
    VlmScheduler.VlmSchedulerCallback {

    companion object {
        private const val TAG = "OpenIris-Realtime"
    }

    private lateinit var yolov11ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager
    private lateinit var vlmScheduler: VlmScheduler

    private lateinit var cameraView: SurfaceView
    private lateinit var overlayView: OverlayView
    private lateinit var textFps: TextView
    private lateinit var textVlmStatus: TextView
    private lateinit var textDetectionCount: TextView
    private lateinit var textResultPanel: TextView

    private var facing = 0
    private var useGpu = true
    private var currentModel = 0

    private var lastYoloResult: DetectionResult? = null
    private var lastVlmResult: VlmResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime_detect)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configManager = ConfigManager.getInstance(this)
        yolov11ncnn = Yolov11Ncnn()

        initViews()
        initVlmScheduler()
        loadModel()
    }

    private fun initViews() {
        cameraView = findViewById(R.id.cameraView)
        overlayView = findViewById(R.id.overlayView)
        textFps = findViewById(R.id.textFps)
        textVlmStatus = findViewById(R.id.textVlmStatus)
        textDetectionCount = findViewById(R.id.textDetectionCount)
        textResultPanel = findViewById(R.id.textResultPanel)

        cameraView.holder.setFormat(PixelFormat.RGBA_8888)
        cameraView.holder.addCallback(this)

        val buttonSwitchCamera: Button = findViewById(R.id.buttonSwitchCamera)
        buttonSwitchCamera.setOnClickListener {
            facing = 1 - facing
            yolov11ncnn.closeCamera()
            yolov11ncnn.openCamera(facing)
        }

        val buttonToggleGpu: Button = findViewById(R.id.buttonToggleGpu)
        buttonToggleGpu.setOnClickListener {
            useGpu = !useGpu
            loadModel()
            buttonToggleGpu.text = if (useGpu) "GPU" else "CPU"
        }

        val buttonBack: Button = findViewById(R.id.buttonBack)
        buttonBack.setOnClickListener { finish() }
    }

    private fun initVlmScheduler() {
        val config = configManager.loadConfig()
        vlmScheduler = VlmScheduler(config, this)
    }

    private fun loadModel() {
        val config = configManager.loadConfig()
        currentModel = if (config.selectedModel == "yolov11s") 1 else 0
        val cpuGpu = if (useGpu) 1 else 0

        val ret = yolov11ncnn.loadModel(assets, currentModel, cpuGpu)
        if (!ret) {
            Log.e(TAG, "Failed to load model")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        yolov11ncnn.setOutputWindow(holder.surface)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onResume() {
        super.onResume()
        yolov11ncnn.openCamera(facing)
        vlmScheduler.start()
    }

    override fun onPause() {
        super.onPause()
        yolov11ncnn.closeCamera()
        vlmScheduler.stop()
    }

    override fun onVlmResult(result: VlmResult) {
        runOnUiThread {
            lastVlmResult = result
            textVlmStatus.text = "VLM: 完成"
            updateResultPanel()
        }
    }

    override fun onVlmError(error: Throwable) {
        runOnUiThread {
            textVlmStatus.text = "VLM: 失败"
            Log.e(TAG, "VLM error", error)
        }
    }

    private fun updateResultPanel() {
        val sb = StringBuilder()

        lastYoloResult?.let { yolo ->
            sb.appendLine("=== YOLO 检测 ===")
            val countByLabel = yolo.countByLabel()
            countByLabel.forEach { (label, count) ->
                sb.appendLine("  $label: $count")
            }
            sb.appendLine()
        }

        lastVlmResult?.let { vlm ->
            sb.appendLine("=== VLM 识别 ===")
            sb.appendLine("场景: ${vlm.sceneSummary}")
            vlm.objects.forEach { obj ->
                sb.appendLine("  ${obj.name}: ${obj.count}")
            }
            sb.appendLine()
        }

        textResultPanel.text = sb.toString().ifEmpty { "等待检测..." }
    }

    private fun processDetectionResult(rawResults: IntArray) {
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

        lastYoloResult = DetectionResult(
            source = "yolo",
            objects = objects
        )

        runOnUiThread {
            overlayView.setResults(objects, 640, 640)
            textDetectionCount.text = "检测: ${objects.size} 个对象"
            updateResultPanel()
        }
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
}

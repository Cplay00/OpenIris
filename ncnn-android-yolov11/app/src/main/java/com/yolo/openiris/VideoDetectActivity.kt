package com.yolo.openiris

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.AnalysisResult
import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.detection.DetectionMode
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.export.JsonExporter
import com.yolo.openiris.vlm.VlmClient
import com.yolo.openiris.vlm.VlmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

class VideoDetectActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OpenIris-Video"
        private const val FRAME_INTERVAL_MS = 1000L
    }

    private lateinit var videoView: VideoView
    private lateinit var buttonPlayPause: Button
    private lateinit var buttonAnalyze: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textTimeline: TextView
    private lateinit var textSummary: TextView
    private lateinit var buttonExportJson: Button
    private lateinit var buttonBack: Button

    private lateinit var yolov11ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager

    private var videoUri: Uri? = null
    private var isPlaying = false
    private var analysisResults: MutableList<AnalysisResult> = mutableListOf()
    private var isAnalyzing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detect)

        configManager = ConfigManager.getInstance(this)
        yolov11ncnn = Yolov11Ncnn()

        initViews()
        loadModel()
        loadVideo()
    }

    private fun initViews() {
        videoView = findViewById(R.id.videoView)
        buttonPlayPause = findViewById(R.id.buttonPlayPause)
        buttonAnalyze = findViewById(R.id.buttonAnalyze)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)
        textTimeline = findViewById(R.id.textTimeline)
        textSummary = findViewById(R.id.textSummary)
        buttonExportJson = findViewById(R.id.buttonExportJson)
        buttonBack = findViewById(R.id.buttonBack)

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        buttonPlayPause.setOnClickListener { togglePlayPause() }
        buttonAnalyze.setOnClickListener { startAnalysis() }
        buttonExportJson.setOnClickListener { exportJson() }
        buttonBack.setOnClickListener { finish() }
    }

    private fun loadModel() {
        val config = configManager.loadConfig()
        val modelId = if (config.selectedModel == "yolov11s") 1 else 0
        val cpuGpu = if (config.useGpu) 1 else 0
        yolov11ncnn.loadModel(assets, modelId, cpuGpu)
    }

    private fun loadVideo() {
        val videoUriString = intent.getStringExtra("video_uri")
        if (videoUriString == null) {
            textStatus.text = "未选择视频"
            buttonAnalyze.isEnabled = false
            return
        }

        videoUri = Uri.parse(videoUriString)
        videoView.setVideoURI(videoUri)

        videoView.setOnPreparedListener { mp ->
            textStatus.text = "视频已加载: ${mp.duration / 1000}秒"
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            videoView.pause()
            buttonPlayPause.text = "播放"
        } else {
            videoView.start()
            buttonPlayPause.text = "暂停"
        }
        isPlaying = !isPlaying
    }

    private fun startAnalysis() {
        if (isAnalyzing) return

        val uri = videoUri ?: return

        isAnalyzing = true
        buttonAnalyze.isEnabled = false
        progressBar.visibility = View.VISIBLE
        textStatus.text = "正在分析视频..."

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.Default) {
                    analyzeVideo(uri)
                }

                analysisResults.clear()
                analysisResults.addAll(results)

                displayResults()
                textStatus.text = "分析完成"
                buttonExportJson.isEnabled = true

            } catch (e: Exception) {
                Log.e(TAG, "Video analysis failed", e)
                textStatus.text = "分析失败: ${e.message}"
            } finally {
                isAnalyzing = false
                buttonAnalyze.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun analyzeVideo(uri: Uri): List<AnalysisResult> {
        val results = mutableListOf<AnalysisResult>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(this, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            var currentTimeMs = 0L

            while (currentTimeMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    currentTimeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                if (frame != null) {
                    val yoloResult = runYoloDetection(frame)
                    results.add(
                        AnalysisResult.fromResults(
                            mode = DetectionMode.VIDEO,
                            yoloResult = yoloResult,
                            videoTimestampMs = currentTimeMs
                        )
                    )
                }

                currentTimeMs += FRAME_INTERVAL_MS

                withContext(Dispatchers.Main) {
                    progressBar.progress = ((currentTimeMs.toFloat() / durationMs) * 100).toInt()
                }
            }
        } finally {
            retriever.release()
        }

        return results
    }

    private fun runYoloDetection(bitmap: android.graphics.Bitmap): DetectionResult {
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
            assets.open("models/$modelName/labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun displayResults() {
        val timeline = StringBuilder()
        timeline.appendLine("时间轴:")
        analysisResults.forEach { result ->
            val timeSec = (result.videoTimestampMs ?: 0) / 1000
            val count = result.yoloResult?.objects?.size ?: 0
            timeline.appendLine("  ${timeSec}秒: $count 个对象")
        }
        textTimeline.text = timeline.toString()

        val allLabels = analysisResults.flatMap { it.yoloResult?.objects?.map { obj -> obj.label } ?: emptyList() }
        val summary = StringBuilder()
        summary.appendLine("视频摘要:")
        summary.appendLine("  总帧数: ${analysisResults.size}")
        summary.appendLine("  检测对象类别:")
        allLabels.groupBy { it }.forEach { (label, list) ->
            summary.appendLine("    $label: ${list.size} 次")
        }
        textSummary.text = summary.toString()
    }

    private fun exportJson() {
        if (analysisResults.isEmpty()) {
            Toast.makeText(this, "没有分析结果可导出", Toast.LENGTH_SHORT).show()
            return
        }

        val result = analysisResults.first()
        val exportResult = JsonExporter.export(this, result)
        if (exportResult.success) {
            Toast.makeText(this, "JSON 已导出: ${exportResult.filePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败: ${exportResult.errorMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

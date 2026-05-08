package com.yolo.openiris

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.*
import com.yolo.openiris.export.JsonExporter
import com.yolo.openiris.ui.CapsuleView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoDetectActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OpenIris-Video"
        private const val FRAME_INTERVAL_MS = 1000L
    }

    // Core components
    private lateinit var yolov11Ncnn: Yolov11Ncnn
    private lateinit var configManager: ConfigManager
    private lateinit var aiModelManager: AiModelManager

    // UI components
    private lateinit var videoView: VideoView
    private lateinit var textStatus: MaterialTextView
    private lateinit var buttonBack: ImageButton
    private lateinit var buttonFullscreen: ImageButton
    private lateinit var switchAi: MaterialSwitch
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var buttonSelectVideo: MaterialButton
    private lateinit var buttonPlayPause: MaterialButton
    private lateinit var buttonAnalyze: MaterialButton
    private lateinit var buttonExportJson: MaterialButton

    // Result summary components
    private lateinit var flexboxYolo: FlexboxLayout
    private lateinit var flexboxAi: FlexboxLayout
    private lateinit var flexboxCombined: FlexboxLayout

    // State
    private var isFullscreen = false
    private var isAiEnabled = false
    private var isPlaying = false
    private var isAnalyzing = false

    // Data
    private var videoUri: Uri? = null
    private var analysisResults: MutableList<AnalysisResult> = mutableListOf()
    private var aiCallJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_detect)

        configManager = ConfigManager.getInstance(this)
        aiModelManager = AiModelManager.getInstance(this)
        yolov11Ncnn = Yolov11Ncnn()

        initViews()
        loadModel()
        loadVideo()
    }

    private fun initViews() {
        // Video view
        videoView = findViewById(R.id.videoView)

        // Top toolbar
        textStatus = findViewById(R.id.textStatus)
        buttonBack = findViewById(R.id.buttonBack)
        buttonFullscreen = findViewById(R.id.buttonFullscreen)

        // AI switch
        switchAi = findViewById(R.id.switchAi)

        // Progress
        progressBar = findViewById(R.id.progressBar)

        // Action buttons
        buttonSelectVideo = findViewById(R.id.buttonSelectVideo)
        buttonPlayPause = findViewById(R.id.buttonPlayPause)
        buttonAnalyze = findViewById(R.id.buttonAnalyze)
        buttonExportJson = findViewById(R.id.buttonExportJson)

        // Result summary
        flexboxYolo = findViewById(R.id.flexboxYolo)
        flexboxAi = findViewById(R.id.flexboxAi)
        flexboxCombined = findViewById(R.id.flexboxCombined)

        // Setup click listeners
        buttonBack.setOnClickListener { finish() }
        buttonFullscreen.setOnClickListener { toggleFullscreen() }
        videoView.setOnClickListener { toggleFullscreen() }

        switchAi.setOnCheckedChangeListener { _, isChecked ->
            isAiEnabled = isChecked
            if (isChecked) {
                startAiCallLoop()
            } else {
                stopAiCallLoop()
            }
        }

        buttonSelectVideo.setOnClickListener {
            // TODO: Implement video selection
        }

        buttonPlayPause.setOnClickListener { togglePlayPause() }
        buttonAnalyze.setOnClickListener { startAnalysis() }
        buttonExportJson.setOnClickListener { exportJson() }

        // Setup video view
        videoView.setOnPreparedListener { mp ->
            textStatus.text = "视频已加载: ${mp.duration / 1000}秒"
        }
    }

    private fun loadModel() {
        val config = configManager.loadConfig()
        val modelId = if (config.selectedModel == "yolov11s") 1 else 0
        val cpuGpu = if (config.useGpu) 1 else 0
        yolov11Ncnn.loadModel(assets, modelId, cpuGpu)
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
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            videoView.pause()
            buttonPlayPause.text = "播放"
            buttonPlayPause.setIconResource(android.R.drawable.ic_media_play)
        } else {
            videoView.start()
            buttonPlayPause.text = "暂停"
            buttonPlayPause.setIconResource(android.R.drawable.ic_media_pause)
        }
        isPlaying = !isPlaying
    }

    private fun startAnalysis() {
        if (isAnalyzing) return

        val uri = videoUri ?: return

        isAnalyzing = true
        buttonAnalyze.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
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
            assets.open("models/$modelName/labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun displayResults() {
        // Aggregate YOLO results
        val allObjects = analysisResults.flatMap { it.yoloResult?.objects ?: emptyList() }
        val countByLabel = allObjects.groupBy { it.label }.mapValues { it.value.size }

        // Display YOLO capsules
        flexboxYolo.removeAllViews()
        countByLabel.forEach { (label, count) ->
            val avgConfidence = allObjects
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

        // Update combined results
        updateCombinedResults()
    }

    private fun updateCombinedResults() {
        flexboxCombined.removeAllViews()

        // For now, just mirror YOLO results in combined
        val allObjects = analysisResults.flatMap { it.yoloResult?.objects ?: emptyList() }
        val countByLabel = allObjects.groupBy { it.label }.mapValues { it.value.size }

        countByLabel.forEach { (label, count) ->
            val avgConfidence = allObjects
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
            capsule.bind(stats, CapsuleView.CapsuleSource.COMBINED)
            flexboxCombined.addView(capsule)
        }
    }

    private fun startAiCallLoop() {
        aiCallJob?.cancel()
        aiCallJob = lifecycleScope.launch {
            val intervalMs = (aiModelManager.getCallIntervalSeconds() * 1000).toLong()
            while (true) {
                delay(intervalMs)
                if (isAiEnabled && isPlaying) {
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
            val result = aiModelManager.callWithFallback(
                prompt = "请识别视频中的物体，以JSON格式返回结果。",
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            )

            if (result.success && result.structuredOutput != null) {
                runOnUiThread {
                    displayAiResults(result.structuredOutput)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI call failed", e)
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
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val bottomPanel = findViewById<View>(R.id.bottomPanel)
        val topToolbar = findViewById<View>(R.id.topToolbar)

        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            animateViewOut(bottomPanel)
            animateViewOut(topToolbar)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())

            animateViewIn(bottomPanel)
            animateViewIn(topToolbar)
        }
    }

    private fun animateViewOut(view: View) {
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        val translationY = ObjectAnimator.ofFloat(view, "translationY", 0f, view.height.toFloat())
        val set = AnimatorSet()
        set.playTogether(alpha, translationY)
        set.duration = 300
        set.interpolator = DecelerateInterpolator()
        set.start()
    }

    private fun animateViewIn(view: View) {
        view.alpha = 0f
        view.translationY = view.height.toFloat()

        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val translationY = ObjectAnimator.ofFloat(view, "translationY", view.height.toFloat(), 0f)
        val set = AnimatorSet()
        set.playTogether(alpha, translationY)
        set.duration = 300
        set.interpolator = DecelerateInterpolator()
        set.start()
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

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            videoView.pause()
        }
        stopAiCallLoop()
    }

    override fun onResume() {
        super.onResume()
        if (isAiEnabled) {
            startAiCallLoop()
        }
    }
}

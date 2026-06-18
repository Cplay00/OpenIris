package com.yolo.openiris

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.config.ConfigManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import android.util.Log

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        private const val PREFS_CUSTOM_MODELS = "custom_models"
        private const val KEY_MODEL_LIST = "model_list"
        private const val KEY_IMAGE_EXPORT_URI = "image_export_uri"
        private const val KEY_JSON_EXPORT_URI = "json_export_uri"

        fun sanitizeModelName(name: String): String {
            return name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .replace(Regex("_{2,}"), "_")
                .trim('_')
                .take(64)
                .ifEmpty { "model" }
        }
    }

    private lateinit var configManager: ConfigManager

    private lateinit var spinnerModel: AutoCompleteTextView
    private lateinit var spinnerCPUGPU: AutoCompleteTextView
    private lateinit var spinnerResolution: AutoCompleteTextView
    private lateinit var layoutCustomResolution: View
    private lateinit var editCustomWidth: TextInputEditText
    private lateinit var editCustomHeight: TextInputEditText
    private lateinit var buttonAddResolution: MaterialButton
    private lateinit var switchShowCapturePreview: MaterialSwitch
    private lateinit var buttonAiModelSettings: MaterialButton
    private lateinit var editImageExportPath: TextInputEditText
    private lateinit var editJsonExportPath: TextInputEditText

    private var currentModel = 0
    private var currentCpuGpu = 0
    private var currentResolutionIndex = 0 // 默认480P
    private val customModels = mutableListOf<String>()
    private val customResolutions = mutableListOf<String>()
    private var presetResolutions = listOf<Pair<Int, Int>>()

    private var imageExportUri: Uri? = null
    private var jsonExportUri: Uri? = null

    private val pickModelFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { addModelFromUri(it) }
    }

    private val pickImageDir = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            imageExportUri = it
            val path = uriToFilePath(it) ?: it.path?.substringAfterLast(":") ?: it.toString()
            editImageExportPath.setText(path)
            configManager.setImageExportPath(path)
        }
    }

    private val pickJsonDir = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            jsonExportUri = it
            val path = uriToFilePath(it) ?: it.path?.substringAfterLast(":") ?: it.toString()
            editJsonExportPath.setText(path)
            configManager.setJsonExportPath(path)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        configManager = ConfigManager.getInstance(this)

        loadCustomModels()
        initViews()
        loadConfig()
    }

    private fun initViews() {
        spinnerModel = findViewById(R.id.spinnerModel)
        spinnerCPUGPU = findViewById(R.id.spinnerCPUGPU)
        spinnerResolution = findViewById(R.id.spinnerResolution)
        layoutCustomResolution = findViewById(R.id.layoutCustomResolution)
        editCustomWidth = findViewById(R.id.editCustomWidth)
        editCustomHeight = findViewById(R.id.editCustomHeight)
        buttonAddResolution = findViewById(R.id.buttonAddResolution)
        switchShowCapturePreview = findViewById(R.id.switchShowCapturePreview)

        val cpuGpuOptions = listOf("CPU (兼容性好)", "GPU (速度快)")
        val cpuGpuAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cpuGpuOptions)
        spinnerCPUGPU.setAdapter(cpuGpuAdapter)
        spinnerCPUGPU.setOnItemClickListener { _, _, position, _ ->
            currentCpuGpu = position
            saveProcessorSetting()
        }

        // 分辨率选择
        setupResolutionSpinner()

        // 截图预览开关 - 即时保存
        switchShowCapturePreview.setOnCheckedChangeListener { _, isChecked ->
            saveCapturePreviewSetting(isChecked)
        }

        // 检测采集间隔
        val sliderCaptureInterval = findViewById<com.google.android.material.slider.Slider>(R.id.sliderCaptureInterval)
        val textCaptureInterval = findViewById<com.google.android.material.textview.MaterialTextView>(R.id.textCaptureInterval)
        val config_capture = configManager.loadConfig()
        sliderCaptureInterval.value = config_capture.captureIntervalSeconds
        textCaptureInterval.text = "${config_capture.captureIntervalSeconds}秒"

        sliderCaptureInterval.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                textCaptureInterval.text = "${value}秒"
                val cfg = configManager.loadConfig().copy(captureIntervalSeconds = value)
                configManager.saveConfig(cfg)
            }
        }

        buttonAiModelSettings = findViewById(R.id.buttonAiModelSettings)
        buttonAiModelSettings.setOnClickListener {
            startActivity(Intent(this, AiModelSettingsActivity::class.java))
        }

        editImageExportPath = findViewById(R.id.editImageExportPath)
        editJsonExportPath = findViewById(R.id.editJsonExportPath)

        editImageExportPath.setOnClickListener { pickImageDir.launch(null) }
        editJsonExportPath.setOnClickListener { pickJsonDir.launch(null) }
        // 允许手动输入路径
        editImageExportPath.isFocusable = true
        editImageExportPath.isFocusableInTouchMode = true
        editJsonExportPath.isFocusable = true
        editJsonExportPath.isFocusableInTouchMode = true

        // 导出路径 - 失去焦点时保存
        editImageExportPath.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val path = editImageExportPath.text?.toString()?.trim() ?: ""
                configManager.setImageExportPath(path)
            }
        }
        editJsonExportPath.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val path = editJsonExportPath.text?.toString()?.trim() ?: ""
                configManager.setJsonExportPath(path)
            }
        }

        updateModelSpinner()
    }

    private fun addModelFromUri(uri: Uri) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast(".").lowercase()

        if (ext == "zip") {
            addModelFromZip(uri, fileName)
            return
        }

        if (ext != "pt" && ext != "param" && ext != "bin") {
            Toast.makeText(this, "不支持的文件格式: .$ext（仅支持 .zip / .pt / .param / .bin）", Toast.LENGTH_LONG).show()
            return
        }

        val modelName = sanitizeModelName(fileName.substringBeforeLast(".").lowercase())
        if (customModels.contains(modelName)) {
            Toast.makeText(this, "模型已存在: $modelName", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val modelDir = getDir("models", MODE_PRIVATE)
            val targetDir = java.io.File(modelDir, modelName)
            targetDir.mkdirs()

            val inputStream = contentResolver.openInputStream(uri) ?: run {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
                return
            }

            val targetFile = java.io.File(targetDir, fileName)
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            customModels.add(modelName)
            saveCustomModels()
            updateModelSpinner()
            Toast.makeText(this, "已添加模型: $modelName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "添加模型失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addModelFromZip(uri: Uri, zipFileName: String) {
        lifecycleScope.launch {
            var tempDir: java.io.File? = null
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: run {
                    Toast.makeText(this@SettingsActivity, "无法读取文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                tempDir = withContext(Dispatchers.IO) {
                    val dir = java.io.File(cacheDir, "model_import_${System.currentTimeMillis()}")
                    dir.mkdirs()
                    dir
                }

                val extractedFiles = mutableMapOf<String, java.io.File>()
                var topDirName: String? = null
                var totalExtractedSize = 0L
                val maxExtractSize = 500 * 1024 * 1024L // 500MB 限制

                withContext(Dispatchers.IO) {
                    ZipInputStream(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val entryName = entry.name
                                val entryExt = entryName.substringAfterLast(".").lowercase()
                                val baseName = entryName.substringAfterLast("/")

                                if (entryName.contains("/") && topDirName == null) {
                                    topDirName = entryName.substringBefore("/")
                                }

                                if (entryExt == "param" || entryExt == "bin" || baseName == "labels.txt") {
                                    val tempFile = java.io.File(tempDir, baseName)
                                    tempFile.outputStream().use { output ->
                                            val written = zip.copyTo(output)
                                            totalExtractedSize += written
                                            if (totalExtractedSize > maxExtractSize) {
                                                throw IllegalStateException("ZIP 文件过大，超过 500MB 限制")
                                            }
                                    }
                                    extractedFiles[entryExt] = tempFile
                                    if (baseName == "labels.txt") {
                                        extractedFiles["labels.txt"] = tempFile
                                    }
                                }
                            }
                            entry = zip.nextEntry
                        }
                    }
                }

                if (!extractedFiles.containsKey("param") && !extractedFiles.containsKey("bin")) {
                    Toast.makeText(this@SettingsActivity, "ZIP 不包含 NCNN 模型文件 (.param/.bin)", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val rawName = topDirName?.takeIf { it.isNotBlank() }?.lowercase()
                    ?: zipFileName.substringBeforeLast(".").lowercase()
                    ?: "custom_model"
                val modelName = sanitizeModelName(rawName)

                if (customModels.contains(modelName)) {
                    Toast.makeText(this@SettingsActivity, "模型已存在: $modelName", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val modelDir = getDir("models", MODE_PRIVATE)
                val targetDir = java.io.File(modelDir, modelName)

                withContext(Dispatchers.IO) {
                    targetDir.mkdirs()
                    extractedFiles.values.forEach { tempFile ->
                        val targetFile = java.io.File(targetDir, tempFile.name)
                        tempFile.copyTo(targetFile, overwrite = true)
                    }
                }

                customModels.add(modelName)
                saveCustomModels()
                updateModelSpinner()

                val labelStatus = if (extractedFiles.containsKey("labels.txt")) "（含标签文件）" else "（已生成默认标签）"
                Toast.makeText(this@SettingsActivity, "已导入模型: $modelName $labelStatus", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import model from ZIP", e)
                Toast.makeText(this@SettingsActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                try { tempDir?.deleteRecursively() } catch (_: Exception) {}
            }
        }
    }

    private fun showModelInfoDialog(modelName: String) {
        val modelDir = java.io.File(getDir("models", MODE_PRIVATE), modelName)
        val info = StringBuilder()
        info.appendLine("模型名称: $modelName")

        if (modelDir.exists()) {
            val paramFile = java.io.File(modelDir, "$modelName.param")
            val binFile = java.io.File(modelDir, "$modelName.bin")
            val labelsFile = java.io.File(modelDir, "labels.txt")

            if (paramFile.exists()) info.appendLine("参数文件: ${formatFileSize(paramFile.length())}")
            if (binFile.exists()) info.appendLine("权重文件: ${formatFileSize(binFile.length())}")

            if (labelsFile.exists()) {
                val labels = labelsFile.readLines().filter { it.isNotBlank() }
                info.appendLine("标签数量: ${labels.size}")
                if (labels.isNotEmpty()) {
                    info.appendLine("标签预览: ${labels.take(5).joinToString(", ")}${if (labels.size > 5) "..." else ""}")
                }
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("模型信息")
            .setMessage(info.toString())
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showRenameModelDialog(modelName: String) {
        val editText = android.widget.EditText(this).apply {
            setText(modelName)
            setSelection(modelName.length)
            hint = "输入新名称"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("重命名模型")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = sanitizeModelName(editText.text.toString().trim().lowercase())
                if (newName.isBlank()) {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == modelName) return@setPositiveButton
                if (customModels.contains(newName)) {
                    Toast.makeText(this, "模型名已存在: $newName", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val modelDir = getDir("models", MODE_PRIVATE)
                val oldDir = java.io.File(modelDir, modelName)
                val newDir = java.io.File(modelDir, newName)

                if (oldDir.exists()) {
                    // 重命名内部文件
                    val paramFile = java.io.File(oldDir, "$modelName.param")
                    val binFile = java.io.File(oldDir, "$modelName.bin")
                    if (paramFile.exists()) paramFile.renameTo(java.io.File(oldDir, "$newName.param"))
                    if (binFile.exists()) binFile.renameTo(java.io.File(oldDir, "$newName.bin"))
                    val renamed = oldDir.renameTo(newDir)
                    if (!renamed) {
                        // 降级方案：复制 + 删除
                        oldDir.copyRecursively(newDir)
                        oldDir.deleteRecursively()
                    }
                }

                val index = customModels.indexOf(modelName)
                if (index >= 0) customModels[index] = newName
                saveCustomModels()
                updateModelSpinner()
                Toast.makeText(this, "已重命名为: $newName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteModelDialog(modelName: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("删除模型")
            .setMessage("确定要删除模型 \"$modelName\" 吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                val modelDir = java.io.File(getDir("models", MODE_PRIVATE), modelName)
                if (modelDir.exists()) modelDir.deleteRecursively()

                customModels.remove(modelName)
                saveCustomModels()
                updateModelSpinner()
                Toast.makeText(this, "已删除模型: $modelName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun uriToFilePath(uri: Uri): String? {
        // 处理 externalstorage documents URI: content://com.android.externalstorage.documents/tree/primary%3APictures
        val docId = uri.lastPathSegment ?: return null
        val parts = docId.split(":")
        if (parts.size == 2) {
            val type = parts[0]
            val path = parts[1]
            return when (type) {
                "primary" -> "/storage/emulated/0/$path"
                else -> "/storage/$type/$path"
            }
        }
        return null
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun setupResolutionSpinner() {
        // 获取屏幕分辨率
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        presetResolutions = AppConfig.getPresetResolutions(screenWidth, screenHeight)
        
        val resolutions = getResolutionOptions()
        val resolutionAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, resolutions)
        spinnerResolution.setAdapter(resolutionAdapter)
        spinnerResolution.setOnItemClickListener { _, _, position, _ ->
            val totalOptions = presetResolutions.size + customResolutions.size
            val isManageOption = customResolutions.isNotEmpty() && position == totalOptions + 1
            val isAddOption = position == totalOptions

            when {
                isManageOption -> {
                    showResolutionManagementDialog()
                    spinnerResolution.setText(getResolutionOptions()[currentResolutionIndex], false)
                }
                isAddOption -> {
                    currentResolutionIndex = position
                    layoutCustomResolution.visibility = View.VISIBLE
                    buttonAddResolution.visibility = View.VISIBLE
                }
                else -> {
                    currentResolutionIndex = position
                    layoutCustomResolution.visibility = View.GONE
                    buttonAddResolution.visibility = View.GONE
                    saveResolutionSetting()
                }
            }
        }

        buttonAddResolution.setOnClickListener { addCustomResolution() }
    }

    private fun showResolutionManagementDialog() {
        val resolutionArray = customResolutions.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("选择要删除的分辨率")
            .setItems(resolutionArray) { _, which ->
                showDeleteResolutionDialog(which)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteResolutionDialog(customIndex: Int) {
        val resolution = customResolutions[customIndex]
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("删除分辨率")
            .setMessage("确定要删除自定义分辨率 \"$resolution\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                customResolutions.removeAt(customIndex)
                val config = configManager.loadConfig()
                configManager.saveConfig(config.copy(customResolutions = customResolutions.toList()))
                setupResolutionSpinner()
                Toast.makeText(this, "已删除分辨率", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getResolutionOptions(): List<String> {
        val options = mutableListOf<String>()
        val presetNames = AppConfig.getPresetResolutionNames()
        presetResolutions.forEachIndexed { index, (width, height) ->
            options.add("${presetNames[index]} (${width}x${height})")
        }
        options.addAll(customResolutions.map { "$it (自定义)" })
        options.add("+ 自定义分辨率")
        if (customResolutions.isNotEmpty()) options.add("⚙ 管理分辨率")
        return options
    }

    private fun addCustomResolution() {
        val width = editCustomWidth.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val height = editCustomHeight.text?.toString()?.trim()?.toIntOrNull() ?: 0

        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "请输入有效的分辨率", Toast.LENGTH_SHORT).show()
            return
        }

        if (width > 4096 || height > 4096) {
            Toast.makeText(this, "分辨率不能超过4096", Toast.LENGTH_SHORT).show()
            return
        }

        val resolution = "${width}x${height}"
        if (customResolutions.contains(resolution)) {
            Toast.makeText(this, "该分辨率已存在", Toast.LENGTH_SHORT).show()
            return
        }

        customResolutions.add(resolution)
        currentResolutionIndex = presetResolutions.size + customResolutions.size - 1
        
        // 刷新下拉列表
        val resolutions = getResolutionOptions()
        val resolutionAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, resolutions)
        spinnerResolution.setAdapter(resolutionAdapter)
        spinnerResolution.setText(resolutions[currentResolutionIndex], false)
        
        layoutCustomResolution.visibility = View.GONE
        buttonAddResolution.visibility = View.GONE
        editCustomWidth.text?.clear()
        editCustomHeight.text?.clear()
        
        Toast.makeText(this, "已添加分辨率: $resolution", Toast.LENGTH_SHORT).show()
    }

    private fun getResolutionFromIndex(index: Int): Pair<Int, Int> {
        return when {
            index < presetResolutions.size -> {
                presetResolutions[index]
            }
            index < presetResolutions.size + customResolutions.size -> {
                val customIndex = index - presetResolutions.size
                val parts = customResolutions[customIndex].split("x")
                Pair(parts[0].toInt(), parts[1].toInt())
            }
            else -> Pair(AppConfig.DEFAULT_CAMERA_WIDTH, AppConfig.DEFAULT_CAMERA_HEIGHT)
        }
    }

    private fun updateModelSpinner() {
        val modelOptions = mutableListOf("YOLOv11n (轻量级，实时检测)")
        modelOptions.addAll(customModels.map { "$it (自定义)" })
        modelOptions.add("+ 添加模型")
        if (customModels.isNotEmpty()) modelOptions.add("⚙ 管理模型")

        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, modelOptions)
        spinnerModel.setAdapter(modelAdapter)
        spinnerModel.setOnItemClickListener { _, _, position, _ ->
            if (position == modelOptions.size - 1) {
                if (customModels.isNotEmpty()) {
                    showModelManagementListDialog()
                } else {
                    pickModelFile.launch("*/*")
                }
                spinnerModel.setText(modelAdapter.getItem(currentModel).toString(), false)
            } else if (position == modelOptions.size - 2 && customModels.isNotEmpty()) {
                pickModelFile.launch("*/*")
                spinnerModel.setText(modelAdapter.getItem(currentModel).toString(), false)
            } else {
                currentModel = position
                // 即时保存模型设置
                saveModelSetting()
            }
        }

        if (currentModel < modelOptions.size - 1) {
            spinnerModel.setText(modelOptions[currentModel], false)
        }
    }

    private fun showModelManagementListDialog() {
        val modelArray = customModels.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("选择要管理的模型")
            .setItems(modelArray) { _, which ->
                showModelManagementDialog(modelArray[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showModelManagementDialog(modelName: String) {
        val options = arrayOf("📋 模型信息", "✏️ 重命名", "🗑️ 删除")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("管理模型: $modelName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showModelInfoDialog(modelName)
                    1 -> showRenameModelDialog(modelName)
                    2 -> showDeleteModelDialog(modelName)
                }
            }
            .show()
    }

    private fun loadCustomModels() {
        val prefs = getSharedPreferences(PREFS_CUSTOM_MODELS, MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_MODEL_LIST, emptySet()) ?: emptySet()
        customModels.clear()
        customModels.addAll(saved.sorted())
    }

    private fun saveCustomModels() {
        val prefs = getSharedPreferences(PREFS_CUSTOM_MODELS, MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_MODEL_LIST, customModels.toSet()).apply()
    }

    private fun loadConfig() {
        val config = configManager.loadConfig()

        // 加载保存的模型选择
        val savedModel = config.selectedModel
        currentModel = if (savedModel == "yolov11n" || savedModel.isBlank()) {
            0
        } else {
            val index = customModels.indexOf(savedModel)
            if (index >= 0) index + 1 else 0
        }
        val modelOptions = (spinnerModel.adapter as? ArrayAdapter<*>)?.let { adapter ->
            (0 until adapter.count).map { adapter.getItem(it).toString() }
        } ?: emptyList()
        if (currentModel < modelOptions.size) {
            spinnerModel.setText(modelOptions[currentModel], false)
        }

        currentCpuGpu = if (config.useGpu) 1 else 0
        spinnerCPUGPU.setText(spinnerCPUGPU.adapter.getItem(currentCpuGpu).toString(), false)

        // 加载自定义分辨率列表
        customResolutions.clear()
        customResolutions.addAll(config.customResolutions)

        // 获取屏幕分辨率
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        presetResolutions = AppConfig.getPresetResolutions(screenWidth, screenHeight)

        // 设置当前分辨率
        val currentResolution = "${config.cameraResolutionWidth}x${config.cameraResolutionHeight}"
        val presetIndex = presetResolutions.indexOfFirst { "${it.first}x${it.second}" == currentResolution }
        currentResolutionIndex = if (presetIndex >= 0) {
            presetIndex
        } else {
            val customIndex = customResolutions.indexOf(currentResolution)
            if (customIndex >= 0) presetResolutions.size + customIndex else 0
        }
        
        // 刷新分辨率下拉列表并设置当前值
        setupResolutionSpinner()
        val resolutions = getResolutionOptions()
        if (currentResolutionIndex < resolutions.size) {
            spinnerResolution.setText(resolutions[currentResolutionIndex], false)
        }

        // 加载截图预览开关
        switchShowCapturePreview.isChecked = config.showCapturePreview

        // 加载采集间隔
        val sliderCaptureInterval = findViewById<com.google.android.material.slider.Slider>(R.id.sliderCaptureInterval)
        val textCaptureInterval = findViewById<com.google.android.material.textview.MaterialTextView>(R.id.textCaptureInterval)
        sliderCaptureInterval.value = config.captureIntervalSeconds
        textCaptureInterval.text = "${config.captureIntervalSeconds}秒"

        editImageExportPath.setText(configManager.getImageExportPath())
        editJsonExportPath.setText(configManager.getJsonExportPath())

        val prefs = getSharedPreferences(PREFS_CUSTOM_MODELS, MODE_PRIVATE)
        imageExportUri = prefs.getString(KEY_IMAGE_EXPORT_URI, null)?.let { Uri.parse(it) }
        jsonExportUri = prefs.getString(KEY_JSON_EXPORT_URI, null)?.let { Uri.parse(it) }
    }

    override fun onResume() {
        super.onResume()
        loadCustomModels()
        updateModelSpinner()
        loadConfig()
    }

    // 即时保存方法
    private fun saveModelSetting() {
        val selectedModel = if (currentModel == 0) "yolov11n" else customModels.getOrElse(currentModel - 1) { "yolov11n" }
        val config = configManager.loadConfig().copy(selectedModel = selectedModel)
        configManager.saveConfig(config)
    }

    private fun saveProcessorSetting() {
        val useGpu = currentCpuGpu == 1
        val config = configManager.loadConfig().copy(useGpu = useGpu)
        configManager.saveConfig(config)
    }

    private fun saveResolutionSetting() {
        val resolution = getResolutionFromIndex(currentResolutionIndex)
        val config = configManager.loadConfig().copy(
            cameraResolutionWidth = resolution.first,
            cameraResolutionHeight = resolution.second,
            customResolutions = customResolutions.toList()
        )
        configManager.saveConfig(config)
    }

    private fun saveCapturePreviewSetting(isChecked: Boolean) {
        val config = configManager.loadConfig().copy(showCapturePreview = isChecked)
        configManager.saveConfig(config)
    }
}

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

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_CUSTOM_MODELS = "custom_models"
        private const val KEY_MODEL_LIST = "model_list"
        private const val KEY_IMAGE_EXPORT_URI = "image_export_uri"
        private const val KEY_JSON_EXPORT_URI = "json_export_uri"
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
            editImageExportPath.setText(it.path?.substringAfterLast(":") ?: it.toString())
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
            editJsonExportPath.setText(it.path?.substringAfterLast(":") ?: it.toString())
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

        buttonAiModelSettings = findViewById(R.id.buttonAiModelSettings)
        buttonAiModelSettings.setOnClickListener {
            startActivity(Intent(this, AiModelSettingsActivity::class.java))
        }

        editImageExportPath = findViewById(R.id.editImageExportPath)
        editJsonExportPath = findViewById(R.id.editJsonExportPath)

        editImageExportPath.setOnClickListener { pickImageDir.launch(null) }
        editJsonExportPath.setOnClickListener { pickJsonDir.launch(null) }
        editImageExportPath.isFocusable = false
        editJsonExportPath.isFocusable = false

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

        if (ext != "pt" && ext != "param" && ext != "bin") {
            Toast.makeText(this, "不支持的文件格式: .$ext(仅支持 .pt / .param / .bin)", Toast.LENGTH_LONG).show()
            return
        }

        val modelName = fileName.substringBeforeLast(".").lowercase()
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
            currentResolutionIndex = position
            val isCustom = position >= presetResolutions.size + customResolutions.size
            layoutCustomResolution.visibility = if (isCustom) View.VISIBLE else View.GONE
            buttonAddResolution.visibility = if (isCustom) View.VISIBLE else View.GONE
            // 即时保存分辨率设置
            saveResolutionSetting()
        }

        buttonAddResolution.setOnClickListener { addCustomResolution() }
    }

    private fun getResolutionOptions(): List<String> {
        val options = mutableListOf<String>()
        val presetNames = AppConfig.getPresetResolutionNames()
        presetResolutions.forEachIndexed { index, (width, height) ->
            options.add("${presetNames[index]} (${width}x${height})")
        }
        options.addAll(customResolutions.map { "$it (自定义)" })
        options.add("+ 自定义分辨率")
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
        val modelOptions = mutableListOf("YOLOv11n (轻量级,实时检测)")
        modelOptions.addAll(customModels.map { "$it (自定义)" })
        modelOptions.add("+ 添加模型")

        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, modelOptions)
        spinnerModel.setAdapter(modelAdapter)
        spinnerModel.setOnItemClickListener { _, _, position, _ ->
            if (position == modelOptions.size - 1) {
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

        currentModel = 0
        spinnerModel.setText(spinnerModel.adapter.getItem(0).toString(), false)

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

        editImageExportPath.setText(configManager.getImageExportPath())
        editJsonExportPath.setText(configManager.getJsonExportPath())

        val prefs = getSharedPreferences(PREFS_CUSTOM_MODELS, MODE_PRIVATE)
        imageExportUri = prefs.getString(KEY_IMAGE_EXPORT_URI, null)?.let { Uri.parse(it) }
        jsonExportUri = prefs.getString(KEY_JSON_EXPORT_URI, null)?.let { Uri.parse(it) }
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

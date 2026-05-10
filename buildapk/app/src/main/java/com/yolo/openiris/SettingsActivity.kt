package com.yolo.openiris

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var buttonAiModelSettings: MaterialButton
    private lateinit var editImageExportPath: TextInputEditText
    private lateinit var editJsonExportPath: TextInputEditText
    private lateinit var buttonSave: MaterialButton

    private var currentModel = 0
    private var currentCpuGpu = 0
    private val customModels = mutableListOf<String>()

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

        val cpuGpuOptions = listOf("CPU (兼容性好)", "GPU (速度快)")
        val cpuGpuAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cpuGpuOptions)
        spinnerCPUGPU.setAdapter(cpuGpuAdapter)
        spinnerCPUGPU.setOnItemClickListener { _, _, position, _ ->
            currentCpuGpu = position
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

        buttonSave = findViewById(R.id.buttonSave)
        buttonSave.setOnClickListener { saveConfig() }

        updateModelSpinner()
    }

    private fun addModelFromUri(uri: Uri) {
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast(".").lowercase()

        if (ext != "pt" && ext != "param" && ext != "bin") {
            Toast.makeText(this, "不支持的文件格式: .$ext（仅支持 .pt / .param / .bin）", Toast.LENGTH_LONG).show()
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

    private fun updateModelSpinner() {
        val modelOptions = mutableListOf("YOLOv11n (轻量级，实时检测)")
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

        editImageExportPath.setText(configManager.getImageExportPath())
        editJsonExportPath.setText(configManager.getJsonExportPath())

        val prefs = getSharedPreferences(PREFS_CUSTOM_MODELS, MODE_PRIVATE)
        imageExportUri = prefs.getString(KEY_IMAGE_EXPORT_URI, null)?.let { Uri.parse(it) }
        jsonExportUri = prefs.getString(KEY_JSON_EXPORT_URI, null)?.let { Uri.parse(it) }
    }

    private fun saveConfig() {
        val selectedModel = if (currentModel == 0) "yolov11n" else customModels.getOrElse(currentModel - 1) { "yolov11n" }
        val useGpu = currentCpuGpu == 1

        val imageExportPath = editImageExportPath.text?.toString()?.trim() ?: ""
        val jsonExportPath = editJsonExportPath.text?.toString()?.trim() ?: ""

        val config = configManager.loadConfig().copy(
            selectedModel = selectedModel,
            useGpu = useGpu
        )
        configManager.saveConfig(config)
        configManager.setImageExportPath(imageExportPath)
        configManager.setJsonExportPath(jsonExportPath)

        val prefs = getSharedPreferences(PREFS_CUSTOM_MODELS, MODE_PRIVATE)
        prefs.edit().apply {
            imageExportUri?.let { putString(KEY_IMAGE_EXPORT_URI, it.toString()) }
            jsonExportUri?.let { putString(KEY_JSON_EXPORT_URI, it.toString()) }
            apply()
        }

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}

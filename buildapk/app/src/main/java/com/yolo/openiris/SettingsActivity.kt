package com.yolo.openiris

import android.content.Intent
import android.os.Bundle
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

    private val pickModelFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val path = it.path ?: "unknown"
            val fileName = path.substringAfterLast("/")
            val modelName = fileName.substringBeforeLast(".").lowercase()
            if (!customModels.contains(modelName)) {
                customModels.add(modelName)
                updateModelSpinner()
                Toast.makeText(this, "已添加模型: $modelName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "模型已存在: $modelName", Toast.LENGTH_SHORT).show()
            }
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

        buttonSave = findViewById(R.id.buttonSave)
        buttonSave.setOnClickListener { saveConfig() }

        updateModelSpinner()
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
                spinnerModel.setText(spinnerModel.adapter.getItem(currentModel).toString(), false)
            } else {
                currentModel = position
            }
        }

        if (currentModel < modelOptions.size - 1) {
            spinnerModel.setText(modelOptions[currentModel], false)
        }
    }

    private fun loadConfig() {
        val config = configManager.loadConfig()

        currentModel = 0
        spinnerModel.setText(spinnerModel.adapter.getItem(0).toString(), false)

        currentCpuGpu = if (config.useGpu) 1 else 0
        spinnerCPUGPU.setText(spinnerCPUGPU.adapter.getItem(currentCpuGpu).toString(), false)

        editImageExportPath.setText(configManager.getImageExportPath())
        editJsonExportPath.setText(configManager.getJsonExportPath())
    }

    private fun saveConfig() {
        val selectedModel = "yolov11n"
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

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}

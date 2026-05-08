package com.yolo.openiris

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configManager = ConfigManager.getInstance(this)

        initViews()
        loadConfig()
    }

    private fun initViews() {
        // YOLO 模型选择
        spinnerModel = findViewById(R.id.spinnerModel)
        val modelOptions = listOf("YOLOv11n (轻量级，实时检测)", "YOLOv11s (高精度，速度较慢)")
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, modelOptions)
        spinnerModel.setAdapter(modelAdapter)
        spinnerModel.setOnItemClickListener { _, _, position, _ ->
            currentModel = position
        }

        // 处理器选择
        spinnerCPUGPU = findViewById(R.id.spinnerCPUGPU)
        val cpuGpuOptions = listOf("CPU (兼容性好)", "GPU (速度快)")
        val cpuGpuAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cpuGpuOptions)
        spinnerCPUGPU.setAdapter(cpuGpuAdapter)
        spinnerCPUGPU.setOnItemClickListener { _, _, position, _ ->
            currentCpuGpu = position
        }

        // AI 模型设置按钮
        buttonAiModelSettings = findViewById(R.id.buttonAiModelSettings)
        buttonAiModelSettings.setOnClickListener {
            val intent = Intent(this, AiModelSettingsActivity::class.java)
            startActivity(intent)
        }

        // 导出路径
        editImageExportPath = findViewById(R.id.editImageExportPath)
        editJsonExportPath = findViewById(R.id.editJsonExportPath)

        // 保存按钮
        buttonSave = findViewById(R.id.buttonSave)
        buttonSave.setOnClickListener { saveConfig() }
    }

    private fun loadConfig() {
        val config = configManager.loadConfig()

        // 加载模型选择
        currentModel = if (config.selectedModel == "yolov11s") 1 else 0
        spinnerModel.setText(spinnerModel.adapter.getItem(currentModel).toString(), false)

        // 加载处理器选择
        currentCpuGpu = if (config.useGpu) 1 else 0
        spinnerCPUGPU.setText(spinnerCPUGPU.adapter.getItem(currentCpuGpu).toString(), false)

        // 加载导出路径
        editImageExportPath.setText(configManager.getImageExportPath())
        editJsonExportPath.setText(configManager.getJsonExportPath())
    }

    private fun saveConfig() {
        // 保存模型选择
        val selectedModel = if (currentModel == 1) "yolov11s" else "yolov11n"
        val useGpu = currentCpuGpu == 1

        // 保存导出路径
        val imageExportPath = editImageExportPath.text.toString().trim()
        val jsonExportPath = editJsonExportPath.text.toString().trim()

        // 更新配置
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

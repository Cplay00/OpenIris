package com.yolo.openiris

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.yolo.openiris.config.AppConfig
import com.yolo.openiris.config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager

    private lateinit var editApiBaseUrl: TextInputEditText
    private lateinit var editApiKey: TextInputEditText
    private lateinit var editVlmModel: TextInputEditText
    private lateinit var editLlmModel: TextInputEditText
    private lateinit var spinnerVlmInterval: Spinner
    private lateinit var checkboxUseGpu: CheckBox
    private lateinit var checkboxEnableLlmFusion: CheckBox
    private lateinit var buttonSave: Button
    private lateinit var buttonTestApi: Button

    private val vlmIntervalValues = listOf(2, 5, 10, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configManager = ConfigManager.getInstance(this)

        initViews()
        loadConfig()
    }

    private fun initViews() {
        editApiBaseUrl = findViewById(R.id.editApiBaseUrl)
        editApiKey = findViewById(R.id.editApiKey)
        editVlmModel = findViewById(R.id.editVlmModel)
        editLlmModel = findViewById(R.id.editLlmModel)
        spinnerVlmInterval = findViewById(R.id.spinnerVlmInterval)
        checkboxUseGpu = findViewById(R.id.checkboxUseGpu)
        checkboxEnableLlmFusion = findViewById(R.id.checkboxEnableLlmFusion)
        buttonSave = findViewById(R.id.buttonSave)
        buttonTestApi = findViewById(R.id.buttonTestApi)

        buttonSave.setOnClickListener { saveConfig() }
        buttonTestApi.setOnClickListener { testApiConnection() }
    }

    private fun loadConfig() {
        val config = configManager.loadConfig()

        editApiBaseUrl.setText(config.apiBaseUrl)
        editApiKey.setText(config.apiKey)
        editVlmModel.setText(config.vlmModel)
        editLlmModel.setText(config.llmModel)

        val intervalIndex = vlmIntervalValues.indexOf(config.vlmIntervalSeconds)
        if (intervalIndex >= 0) {
            spinnerVlmInterval.setSelection(intervalIndex)
        }

        checkboxUseGpu.isChecked = config.useGpu
        checkboxEnableLlmFusion.isChecked = config.enableLlmFusion
    }

    private fun saveConfig() {
        val apiBaseUrl = editApiBaseUrl.text.toString().trim()
        val apiKey = editApiKey.text.toString().trim()
        val vlmModel = editVlmModel.text.toString().trim()
        val llmModel = editLlmModel.text.toString().trim()
        val vlmInterval = vlmIntervalValues[spinnerVlmInterval.selectedItemPosition]
        val useGpu = checkboxUseGpu.isChecked
        val enableLlmFusion = checkboxEnableLlmFusion.isChecked

        val config = AppConfig(
            apiBaseUrl = apiBaseUrl,
            apiKey = apiKey,
            vlmModel = vlmModel,
            llmModel = llmModel,
            vlmIntervalSeconds = vlmInterval,
            useGpu = useGpu,
            enableLlmFusion = enableLlmFusion
        )

        val validation = config.validate()
        if (!validation.isValid) {
            val errorMessage = validation.errors.firstOrNull()?.message ?: "配置无效"
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            return
        }

        configManager.saveConfig(config)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testApiConnection() {
        val apiBaseUrl = editApiBaseUrl.text.toString().trim()
        val apiKey = editApiKey.text.toString().trim()

        if (apiBaseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先填写 API Base URL 和 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        buttonTestApi.isEnabled = false
        buttonTestApi.text = "测试中..."

        lifecycleScope.launch {
            try {
                val config = AppConfig(
                    apiBaseUrl = apiBaseUrl,
                    apiKey = apiKey,
                    vlmModel = editVlmModel.text.toString().trim(),
                    llmModel = editLlmModel.text.toString().trim()
                )

                val success = withContext(Dispatchers.IO) {
                    testApiConfig(config)
                }

                if (success) {
                    Toast.makeText(this@SettingsActivity, R.string.api_test_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.api_test_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                buttonTestApi.isEnabled = true
                buttonTestApi.text = R.string.test_api.toString()
            }
        }
    }

    private fun testApiConfig(config: AppConfig): Boolean {
        return try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("${config.getFullApiBaseUrl()}/models")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

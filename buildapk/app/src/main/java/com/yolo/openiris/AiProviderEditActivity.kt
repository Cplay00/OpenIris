package com.yolo.openiris

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModel
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.ai.AiProvider
import com.yolo.openiris.ai.ApiFormat
import com.yolo.openiris.dialog.ConnectionTestDialog
import com.yolo.openiris.dialog.ModelSettingsDialog
import kotlinx.coroutines.launch
import java.util.UUID

class AiProviderEditActivity : AppCompatActivity(), ModelSettingsDialog.OnModelSettingsListener {

    private lateinit var aiModelManager: AiModelManager

    private lateinit var toggleGroupApiFormat: MaterialButtonToggleGroup
    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var editProviderName: TextInputEditText
    private lateinit var editApiKey: TextInputEditText
    private lateinit var editBaseUrl: TextInputEditText
    private lateinit var editApiPath: TextInputEditText
    private lateinit var switchResponseApi: MaterialSwitch
    private lateinit var switchEnableStream: MaterialSwitch
    private lateinit var editSearchModel: TextInputEditText
    private lateinit var editManualModelId: TextInputEditText
    private lateinit var buttonAddManualModel: MaterialButton
    private lateinit var buttonFetchModels: MaterialButton
    private lateinit var recyclerModels: RecyclerView
    private lateinit var recyclerSelectedModels: RecyclerView
    private lateinit var textModelCount: MaterialTextView
    private lateinit var buttonSave: MaterialButton

    private var providerId: String? = null
    private var existingProvider: AiProvider? = null
    private var allAvailableModels: MutableList<String> = mutableListOf()
    private var filteredModels: MutableList<String> = mutableListOf()
    private var selectedModels: MutableList<AiModel> = mutableListOf()
    private var currentApiFormat: ApiFormat = ApiFormat.OPENAI_COMPATIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_provider_edit)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        aiModelManager = AiModelManager.getInstance(this)

        providerId = intent.getStringExtra("provider_id")

        initViews()
        loadProviderData()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // API 格式选择
        toggleGroupApiFormat = findViewById(R.id.toggleGroupApiFormat)
        toggleGroupApiFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentApiFormat = when (checkedId) {
                    R.id.buttonOpenAi -> ApiFormat.OPENAI_COMPATIBLE
                    R.id.buttonAnthropic -> ApiFormat.ANTHROPIC
                    else -> ApiFormat.OPENAI_COMPATIBLE
                }
                updateApiFormatUI()
            }
        }

        switchEnabled = findViewById(R.id.switchEnabled)
        editProviderName = findViewById(R.id.editProviderName)
        editApiKey = findViewById(R.id.editApiKey)
        editBaseUrl = findViewById(R.id.editBaseUrl)
        editApiPath = findViewById(R.id.editApiPath)
        switchResponseApi = findViewById(R.id.switchResponseApi)
        editSearchModel = findViewById(R.id.editSearchModel)

        // Response API 开关监听
        switchResponseApi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editApiPath.setText("/responses")
            } else {
                editApiPath.setText("/chat/completions")
            }
        }

        // 流式输出开关
        switchEnableStream = findViewById(R.id.switchEnableStream)

        buttonFetchModels = findViewById(R.id.buttonFetchModels)
        buttonFetchModels.setOnClickListener { fetchModelList() }

        // 测试连接按钮
        val buttonTestConnection = findViewById<MaterialButton>(R.id.buttonTestConnection)
        buttonTestConnection.setOnClickListener { showConnectionTestDialog() }

        // 手动输入模型ID
        editManualModelId = findViewById(R.id.editManualModelId)
        buttonAddManualModel = findViewById(R.id.buttonAddManualModel)
        buttonAddManualModel.setOnClickListener { addManualModel() }

        recyclerModels = findViewById(R.id.recyclerModels)
        recyclerModels.layoutManager = LinearLayoutManager(this)

        recyclerSelectedModels = findViewById(R.id.recyclerSelectedModels)
        recyclerSelectedModels.layoutManager = LinearLayoutManager(this)

        textModelCount = findViewById(R.id.textModelCount)

        buttonSave = findViewById(R.id.buttonSave)
        buttonSave.setOnClickListener { saveProvider() }

        // 搜索功能
        editSearchModel.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterModels(s.toString())
            }
        })
    }

    private fun updateApiFormatUI() {
        when (currentApiFormat) {
            ApiFormat.OPENAI_COMPATIBLE -> {
                switchResponseApi.visibility = View.VISIBLE
                editBaseUrl.hint = "API Base Url"
                if (editBaseUrl.text.toString().isEmpty() || editBaseUrl.text.toString().contains("anthropic")) {
                    editBaseUrl.setText("https://api.openai.com/v1")
                }
                // 只有启用 Response API 时才使用 /responses
                if (switchResponseApi.isChecked) {
                    editApiPath.setText("/responses")
                } else {
                    editApiPath.setText("/chat/completions")
                }
            }
            ApiFormat.ANTHROPIC -> {
                switchResponseApi.visibility = View.GONE
                editBaseUrl.hint = "API Base Url"
                if (editBaseUrl.text.toString().isEmpty() || editBaseUrl.text.toString().contains("openai")) {
                    editBaseUrl.setText("https://api.anthropic.com")
                }
                editApiPath.setText("/v1/messages")
            }
        }
    }

    private fun loadProviderData() {
        if (providerId != null) {
            existingProvider = aiModelManager.getProvider(providerId!!)
            existingProvider?.let { provider ->
                editProviderName.setText(provider.name)
                editBaseUrl.setText(provider.baseUrl)
                editApiKey.setText(provider.apiKey)
                editApiPath.setText(provider.apiPath)
                switchEnabled.isChecked = provider.isEnabled
                switchResponseApi.isChecked = provider.useResponseApi
                switchEnableStream.isChecked = provider.enableStream

                // 设置 API 格式
                currentApiFormat = provider.apiFormat
                when (currentApiFormat) {
                    ApiFormat.OPENAI_COMPATIBLE -> toggleGroupApiFormat.check(R.id.buttonOpenAi)
                    ApiFormat.ANTHROPIC -> toggleGroupApiFormat.check(R.id.buttonAnthropic)
                }

                selectedModels = provider.models.toMutableList()
                updateSelectedModelsList()
            }
        }
    }

    private fun fetchModelList() {
        val baseUrl = editBaseUrl.text.toString().trim()
        val apiKey = editApiKey.text.toString().trim()

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先填写 Base URL 和 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        buttonFetchModels.isEnabled = false
        buttonFetchModels.text = "获取中..."

        val provider = AiProvider(
            id = providerId ?: UUID.randomUUID().toString(),
            name = editProviderName.text.toString().trim(),
            baseUrl = baseUrl,
            apiKey = apiKey
        )

        lifecycleScope.launch {
            val result = aiModelManager.fetchModelList(provider)

            result.onSuccess { models ->
                allAvailableModels = models.toMutableList()
                filteredModels = models.toMutableList()
                updateAvailableModelsList()
                textModelCount.text = "${models.size} 个模型"
                Toast.makeText(this@AiProviderEditActivity, "获取到 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
            }

            result.onFailure { error ->
                Toast.makeText(this@AiProviderEditActivity, "获取失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }

            buttonFetchModels.isEnabled = true
            buttonFetchModels.text = "获取模型列表"
        }
    }

    private fun filterModels(query: String) {
        filteredModels = if (query.isEmpty()) {
            allAvailableModels.toMutableList()
        } else {
            allAvailableModels.filter { it.contains(query, ignoreCase = true) }.toMutableList()
        }
        updateAvailableModelsList()
    }

    private fun updateAvailableModelsList() {
        val adapter = AvailableModelsAdapter(
            models = filteredModels,
            selectedModels = selectedModels.map { it.modelId }.toSet(),
            onAddClick = { modelId -> addModel(modelId) }
        )
        recyclerModels.adapter = adapter
    }

    private fun updateSelectedModelsList() {
        val adapter = SelectedModelsAdapter(
            models = selectedModels,
            onSettingsClick = { model ->
                showModelSettingsDialog(model)
            },
            onDeleteClick = { model ->
                deleteModel(model)
            },
            onModelIdChange = { model, newModelId ->
                updateModelId(model, newModelId)
            }
        )
        recyclerSelectedModels.adapter = adapter
    }

    private fun deleteModel(model: AiModel) {
        selectedModels.removeAll { it.id == model.id }
        updateSelectedModelsList()
        updateAvailableModelsList()
        Toast.makeText(this, "已删除模型: ${model.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun addManualModel() {
        val modelId = editManualModelId.text?.toString()?.trim() ?: ""
        if (modelId.isBlank()) {
            Toast.makeText(this, "请输入模型ID", Toast.LENGTH_SHORT).show()
            return
        }
        addModel(modelId)
        editManualModelId.text?.clear()
    }

    private fun updateModelId(model: AiModel, newModelId: String) {
        val index = selectedModels.indexOfFirst { it.id == model.id }
        if (index >= 0) {
            selectedModels[index] = model.copy(
                modelId = newModelId,
                displayName = if (model.displayName == model.modelId) newModelId else model.displayName
            )
            Toast.makeText(this, "模型ID已更新: $newModelId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addModel(modelId: String) {
        if (selectedModels.any { it.modelId == modelId }) {
            Toast.makeText(this, "该模型已添加", Toast.LENGTH_SHORT).show()
            return
        }

        val model = AiModel(
            id = UUID.randomUUID().toString(),
            providerId = providerId ?: UUID.randomUUID().toString(),
            modelId = modelId,
            displayName = modelId,
            hasVision = false,
            isEnabled = true,
            isDefault = selectedModels.isEmpty(),
            priority = selectedModels.size
        )
        selectedModels.add(model)
        updateSelectedModelsList()
        updateAvailableModelsList()
    }

    private fun saveProvider() {
        val name = editProviderName.text.toString().trim()
        val baseUrl = editBaseUrl.text.toString().trim()
        val apiKey = editApiKey.text.toString().trim()
        val apiPath = editApiPath.text.toString().trim()
        val isEnabled = switchEnabled.isChecked
        val useResponseApi = switchResponseApi.isChecked
        val enableStream = switchEnableStream.isChecked

        if (name.isEmpty() || baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return
        }

        val provider = AiProvider(
            id = providerId ?: UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            models = selectedModels,
            isEnabled = isEnabled,
            apiFormat = currentApiFormat,
            apiPath = apiPath,
            useResponseApi = useResponseApi,
            enableStream = enableStream
        )

        if (existingProvider != null) {
            aiModelManager.updateProvider(provider)
        } else {
            aiModelManager.saveProvider(provider)
            // 保存后更新 providerId，以便后续操作（如连接测试）可用
            providerId = provider.id
            existingProvider = provider
        }

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        // 保存后停留当前页，不调用 finish()
    }

    // ModelSettingsDialog.OnModelSettingsListener 实现
    override fun onModelSettingsConfirmed(
        modelId: String, displayName: String, hasVision: Boolean,
        enableReasoning: Boolean, assignedTasks: List<String>,
        customHeaders: Map<String, String>, customBody: Map<String, String>
    ) {
        val index = selectedModels.indexOfFirst { it.id == modelId }
        if (index >= 0) {
            selectedModels[index] = selectedModels[index].copy(
                displayName = displayName,
                hasVision = hasVision,
                enableReasoning = enableReasoning,
                assignedTasks = assignedTasks,
                customHeaders = customHeaders,
                customBody = customBody
            )
            updateSelectedModelsList()
        }
    }

    inner class AvailableModelsAdapter(
        private val models: List<String>,
        private val selectedModels: Set<String>,
        private val onAddClick: (String) -> Unit
    ) : RecyclerView.Adapter<AvailableModelsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_available_model, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(models[position])
        }

        override fun getItemCount() = models.size

        inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val textModelId: android.widget.TextView = itemView.findViewById(R.id.textModelId)
            private val buttonAdd: ImageButton = itemView.findViewById(R.id.buttonAdd)

            fun bind(modelId: String) {
                textModelId.text = modelId
                val isSelected = selectedModels.contains(modelId)
                buttonAdd.setImageResource(
                    if (isSelected) android.R.drawable.ic_menu_add
                    else android.R.drawable.ic_input_add
                )
                buttonAdd.isEnabled = !isSelected
                buttonAdd.alpha = if (isSelected) 0.5f else 1.0f
                buttonAdd.setOnClickListener { onAddClick(modelId) }
            }
        }
    }

    inner class SelectedModelsAdapter(
        private val models: List<AiModel>,
        private val onSettingsClick: (AiModel) -> Unit,
        private val onDeleteClick: (AiModel) -> Unit,
        private val onModelIdChange: (AiModel, String) -> Unit
    ) : RecyclerView.Adapter<SelectedModelsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selected_model_v2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(models[position])
        }

        override fun getItemCount() = models.size

        inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val imageProvider: android.widget.ImageView = itemView.findViewById(R.id.imageProvider)
            private val textInitial: android.widget.TextView = itemView.findViewById(R.id.textInitial)
            private val textModelName: android.widget.TextView = itemView.findViewById(R.id.textModelName)
            private val editModelId: TextInputEditText = itemView.findViewById(R.id.editModelId)
            private val iconVision: android.widget.ImageView = itemView.findViewById(R.id.iconVision)
            private val buttonSettings: ImageButton = itemView.findViewById(R.id.buttonSettings)
            private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)

            fun bind(model: AiModel) {
                textModelName.text = model.displayName
                editModelId.setText(model.modelId)

                // 设置供应商图标
                setProviderIcon(model.modelId)

                // 设置视觉图标
                iconVision.alpha = if (model.hasVision) 1.0f else 0.3f

                // 设置高级选项按钮
                buttonSettings.setOnClickListener { onSettingsClick(model) }

                // 设置删除按钮
                buttonDelete.setOnClickListener { onDeleteClick(model) }

                // 模型ID编辑失去焦点时保存
                editModelId.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val newModelId = editModelId.text?.toString()?.trim() ?: ""
                        if (newModelId.isNotBlank() && newModelId != model.modelId) {
                            onModelIdChange(model, newModelId)
                        }
                    }
                }
            }

            private fun setProviderIcon(modelId: String) {
                val lowerModelId = modelId.lowercase()
                val iconRes = when {
                    lowerModelId.startsWith("gpt") -> R.drawable.ic_provider_openai
                    lowerModelId.startsWith("claude") -> R.drawable.ic_provider_claude
                    lowerModelId.startsWith("gemini") -> R.drawable.ic_provider_gemini
                    lowerModelId.startsWith("deepseek") -> R.drawable.ic_provider_deepseek
                    lowerModelId.startsWith("qwen") -> R.drawable.ic_provider_qwen
                    lowerModelId.startsWith("moonshot") -> R.drawable.ic_provider_moonshot
                    else -> null
                }

                if (iconRes != null) {
                    imageProvider.setImageResource(iconRes)
                    imageProvider.visibility = View.VISIBLE
                    textInitial.visibility = View.GONE
                } else {
                    // 显示首字母
                    val initial = modelId.firstOrNull()?.uppercase() ?: "?"
                    textInitial.text = initial
                    textInitial.visibility = View.VISIBLE
                    imageProvider.visibility = View.GONE

                    // 设置圆形背景颜色
                    val colors = listOf("#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336", "#00BCD4")
                    val colorIndex = modelId.hashCode().mod(colors.size).let { if (it < 0) it + colors.size else it }
                    textInitial.setBackgroundColor(android.graphics.Color.parseColor(colors[colorIndex]))
                }
            }
        }
    }

    private fun showModelSettingsDialog(model: AiModel) {
        val dialog = ModelSettingsDialog.newInstance(model)
        dialog.listener = this
        dialog.show(supportFragmentManager, "ModelSettingsDialog")
    }

    private fun showConnectionTestDialog() {
        val currentProviderId = providerId
        if (currentProviderId == null) {
            Toast.makeText(this, "请先保存提供商再进行测试", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = ConnectionTestDialog.newInstance(currentProviderId)
        dialog.show(supportFragmentManager, "ConnectionTestDialog")
    }
}

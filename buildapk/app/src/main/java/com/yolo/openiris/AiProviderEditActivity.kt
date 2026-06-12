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

        // API 鏍煎紡閫夋嫨
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

        // Response API 寮€鍏崇洃鍚?        switchResponseApi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editApiPath.setText("/responses")
            } else {
                editApiPath.setText("/chat/completions")
            }
        }

        // 娴佸紡杈撳嚭寮€鍏?        switchEnableStream = findViewById(R.id.switchEnableStream)

        buttonFetchModels = findViewById(R.id.buttonFetchModels)
        buttonFetchModels.setOnClickListener { fetchModelList() }

        // 娴嬭瘯杩炴帴鎸夐挳
        val buttonTestConnection = findViewById<MaterialButton>(R.id.buttonTestConnection)
        buttonTestConnection.setOnClickListener { showConnectionTestDialog() }

        // 鎵嬪姩杈撳叆妯″瀷ID
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

        // 鎼滅储鍔熻兘
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
                // 鍙湁鍚敤 Response API 鏃舵墠浣跨敤 /responses
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
            existingProvider = providerId?.let { aiModelManager.getProvider(it) }
            existingProvider?.let { provider ->
                editProviderName.setText(provider.name)
                editBaseUrl.setText(provider.baseUrl)
                editApiKey.setText(provider.apiKey)
                editApiPath.setText(provider.apiPath)
                switchEnabled.isChecked = provider.isEnabled
                switchResponseApi.isChecked = provider.useResponseApi
                switchEnableStream.isChecked = provider.enableStream

                // 璁剧疆 API 鏍煎紡
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
            Toast.makeText(this, "璇峰厛濉啓 Base URL 鍜?API Key", Toast.LENGTH_SHORT).show()
            return
        }

        buttonFetchModels.isEnabled = false
        buttonFetchModels.text = "鑾峰彇涓?.."

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
                textModelCount.text = "${models.size} 涓ā鍨?
                Toast.makeText(this@AiProviderEditActivity, "鑾峰彇鍒?${models.size} 涓ā鍨?, Toast.LENGTH_SHORT).show()
            }

            result.onFailure { error ->
                Toast.makeText(this@AiProviderEditActivity, "鑾峰彇澶辫触: ${error.message}", Toast.LENGTH_SHORT).show()
            }

            buttonFetchModels.isEnabled = true
            buttonFetchModels.text = "鑾峰彇妯″瀷鍒楄〃"
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
        Toast.makeText(this, "宸插垹闄ゆā鍨? ${model.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun addManualModel() {
        val modelId = editManualModelId.text?.toString()?.trim() ?: ""
        if (modelId.isBlank()) {
            Toast.makeText(this, "璇疯緭鍏ユā鍨婭D", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "妯″瀷ID宸叉洿鏂? $newModelId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addModel(modelId: String) {
        if (selectedModels.any { it.modelId == modelId }) {
            Toast.makeText(this, "璇ユā鍨嬪凡娣诲姞", Toast.LENGTH_SHORT).show()
            return
        }

        // 纭繚浣跨敤褰撳墠鐨?providerId锛堝鏋滄湭淇濆瓨鍒欎娇鐢ㄤ复鏃禝D锛?        val currentProviderId = providerId ?: existingProvider?.id ?: UUID.randomUUID().toString()

        val model = AiModel(
            id = UUID.randomUUID().toString(),
            providerId = currentProviderId,
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
            Toast.makeText(this, "璇峰~鍐欏畬鏁翠俊鎭?, Toast.LENGTH_SHORT).show()
            return
        }

        val currentProviderId = providerId ?: existingProvider?.id ?: UUID.randomUUID().toString()

        val provider = AiProvider(
            id = currentProviderId,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            // 纭繚鎵€鏈夋ā鍨嬬殑 providerId 閮芥槸褰撳墠鎻愪緵鍟嗙殑 ID
            models = selectedModels.map { it.copy(providerId = currentProviderId) },
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
        }

        // 鏇存柊 providerId 鍜?existingProvider
        providerId = currentProviderId
        existingProvider = provider

        // 鍚屾鏇存柊 selectedModels 鐨?providerId
        selectedModels = selectedModels.map { it.copy(providerId = currentProviderId) }.toMutableList()

        Toast.makeText(this, "淇濆瓨鎴愬姛", Toast.LENGTH_SHORT).show()
    }

    // ModelSettingsDialog.OnModelSettingsListener 瀹炵幇
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

                // 璁剧疆渚涘簲鍟嗗浘鏍?                setProviderIcon(model.modelId)

                // 璁剧疆瑙嗚鍥炬爣
                iconVision.alpha = if (model.hasVision) 1.0f else 0.3f

                // 璁剧疆楂樼骇閫夐」鎸夐挳
                buttonSettings.setOnClickListener { onSettingsClick(model) }

                // 璁剧疆鍒犻櫎鎸夐挳
                buttonDelete.setOnClickListener { onDeleteClick(model) }

                // 妯″瀷ID缂栬緫澶卞幓鐒︾偣鏃朵繚瀛?                editModelId.setOnFocusChangeListener { _, hasFocus ->
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
                    // 鏄剧ず棣栧瓧姣?                    val initial = modelId.firstOrNull()?.uppercase() ?: "?"
                    textInitial.text = initial
                    textInitial.visibility = View.VISIBLE
                    imageProvider.visibility = View.GONE

                    // 璁剧疆鍦嗗舰鑳屾櫙棰滆壊
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
            Toast.makeText(this, "璇峰厛淇濆瓨鎻愪緵鍟嗗啀杩涜娴嬭瘯", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = ConnectionTestDialog.newInstance(currentProviderId)
        dialog.show(supportFragmentManager, "ConnectionTestDialog")
    }
}


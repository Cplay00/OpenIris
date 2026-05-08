package com.yolo.openiris

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModel
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.ai.AiProvider
import kotlinx.coroutines.launch
import java.util.UUID

class AiProviderEditActivity : AppCompatActivity() {

    private lateinit var aiModelManager: AiModelManager

    private lateinit var editProviderName: TextInputEditText
    private lateinit var editBaseUrl: TextInputEditText
    private lateinit var editApiKey: TextInputEditText
    private lateinit var editSearchModel: TextInputEditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_provider_edit)

        aiModelManager = AiModelManager.getInstance(this)

        providerId = intent.getStringExtra("provider_id")

        initViews()
        loadProviderData()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        editProviderName = findViewById(R.id.editProviderName)
        editBaseUrl = findViewById(R.id.editBaseUrl)
        editApiKey = findViewById(R.id.editApiKey)
        editSearchModel = findViewById(R.id.editSearchModel)

        buttonFetchModels = findViewById(R.id.buttonFetchModels)
        buttonFetchModels.setOnClickListener { fetchModelList() }

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

    private fun loadProviderData() {
        if (providerId != null) {
            existingProvider = aiModelManager.getProvider(providerId!!)
            existingProvider?.let { provider ->
                editProviderName.setText(provider.name)
                editBaseUrl.setText(provider.baseUrl)
                editApiKey.setText(provider.apiKey)
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
            onRemoveClick = { model ->
                selectedModels.remove(model)
                updateSelectedModelsList()
                updateAvailableModelsList()
            },
            onVisionToggle = { model, hasVision ->
                val index = selectedModels.indexOfFirst { it.id == model.id }
                if (index >= 0) {
                    selectedModels[index] = model.copy(hasVision = hasVision)
                }
            },
            onDefaultToggle = { model, isDefault ->
                if (isDefault) {
                    selectedModels.forEachIndexed { index, m ->
                        selectedModels[index] = m.copy(isDefault = m.id == model.id)
                    }
                } else {
                    val index = selectedModels.indexOfFirst { it.id == model.id }
                    if (index >= 0) {
                        selectedModels[index] = model.copy(isDefault = false)
                    }
                }
                updateSelectedModelsList()
            },
            onMoveUp = { model ->
                val index = selectedModels.indexOfFirst { it.id == model.id }
                if (index > 0) {
                    selectedModels.removeAt(index)
                    selectedModels.add(index - 1, model)
                    updateSelectedModelsList()
                }
            },
            onMoveDown = { model ->
                val index = selectedModels.indexOfFirst { it.id == model.id }
                if (index < selectedModels.size - 1) {
                    selectedModels.removeAt(index)
                    selectedModels.add(index + 1, model)
                    updateSelectedModelsList()
                }
            },
            onNameChange = { model, newName ->
                val index = selectedModels.indexOfFirst { it.id == model.id }
                if (index >= 0) {
                    selectedModels[index] = model.copy(displayName = newName)
                    updateSelectedModelsList()
                }
            }
        )
        recyclerSelectedModels.adapter = adapter
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

        if (name.isEmpty() || baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return
        }

        val provider = AiProvider(
            id = providerId ?: UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            models = selectedModels
        )

        if (existingProvider != null) {
            aiModelManager.updateProvider(provider)
        } else {
            aiModelManager.saveProvider(provider)
        }

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        finish()
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
        private val onRemoveClick: (AiModel) -> Unit,
        private val onVisionToggle: (AiModel, Boolean) -> Unit,
        private val onDefaultToggle: (AiModel, Boolean) -> Unit,
        private val onMoveUp: (AiModel) -> Unit,
        private val onMoveDown: (AiModel) -> Unit,
        private val onNameChange: (AiModel, String) -> Unit
    ) : RecyclerView.Adapter<SelectedModelsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selected_model, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(models[position])
        }

        override fun getItemCount() = models.size

        inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val editModelName: TextInputEditText = itemView.findViewById(R.id.editModelName)
            private val textModelId: android.widget.TextView = itemView.findViewById(R.id.textModelId)
            private val switchVision: MaterialSwitch = itemView.findViewById(R.id.switchVision)
            private val switchDefault: MaterialSwitch = itemView.findViewById(R.id.switchDefault)
            private val buttonRemove: ImageButton = itemView.findViewById(R.id.buttonRemove)
            private val buttonMoveUp: ImageButton = itemView.findViewById(R.id.buttonMoveUp)
            private val buttonMoveDown: ImageButton = itemView.findViewById(R.id.buttonMoveDown)

            fun bind(model: AiModel) {
                editModelName.setText(model.displayName)
                textModelId.text = model.modelId

                editModelName.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val newName = editModelName.text.toString().trim()
                        if (newName.isNotEmpty() && newName != model.displayName) {
                            onNameChange(model, newName)
                        }
                    }
                }

                switchVision.isChecked = model.hasVision
                switchVision.setOnCheckedChangeListener { _, isChecked ->
                    onVisionToggle(model, isChecked)
                }

                switchDefault.isChecked = model.isDefault
                switchDefault.setOnCheckedChangeListener { _, isChecked ->
                    onDefaultToggle(model, isChecked)
                }

                buttonRemove.setOnClickListener { onRemoveClick(model) }
                buttonMoveUp.setOnClickListener { onMoveUp(model) }
                buttonMoveDown.setOnClickListener { onMoveDown(model) }
            }
        }
    }
}

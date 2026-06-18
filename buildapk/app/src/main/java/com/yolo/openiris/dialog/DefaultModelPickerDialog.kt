package com.yolo.openiris.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.R
import com.yolo.openiris.ai.AiModel
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.ai.AiProvider

/**
 * 默认模型选择对话框
 */
class DefaultModelPickerDialog : BottomSheetDialogFragment() {

    interface OnModelSelectedListener {
        fun onDefaultModelSelected(model: AiModel?)
    }

    var listener: OnModelSelectedListener? = null

    private lateinit var aiModelManager: AiModelManager
    private lateinit var editSearchModel: TextInputEditText
    private lateinit var textCurrentDefault: MaterialTextView
    private lateinit var recyclerModels: RecyclerView
    private lateinit var buttonCancel: MaterialButton
    private lateinit var buttonClear: MaterialButton

    private var allModels: List<ModelItem> = emptyList()
    private var filteredModels: List<ModelItem> = emptyList()
    private var currentDefaultModelId: String? = null

    data class ModelItem(
        val model: AiModel,
        val providerName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aiModelManager = AiModelManager.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_default_model_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editSearchModel = view.findViewById(R.id.editSearchModel)
        textCurrentDefault = view.findViewById(R.id.textCurrentDefault)
        recyclerModels = view.findViewById(R.id.recyclerModels)
        buttonCancel = view.findViewById(R.id.buttonCancel)
        buttonClear = view.findViewById(R.id.buttonClear)

        loadModels()
        setupButtons()
        setupSearch()
    }

    override fun onStart() {
        super.onStart()
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(requireView().parent as android.view.View)
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    private fun loadModels() {
        val providers = aiModelManager.getProviders().filter { it.isEnabled }
        val defaultModel = aiModelManager.getDefaultModel()
        currentDefaultModelId = defaultModel?.id

        allModels = providers.flatMap { provider ->
            provider.models.filter { it.isEnabled }.map { model ->
                ModelItem(model, provider.name)
            }
        }

        filteredModels = allModels
        updateCurrentDefaultText()
        updateList()
    }

    private fun updateCurrentDefaultText() {
        val defaultItem = allModels.find { it.model.id == currentDefaultModelId }
        textCurrentDefault.text = if (defaultItem != null) {
            "当前默认模型：${defaultItem.model.displayName} (${defaultItem.providerName})"
        } else {
            "当前默认模型：无"
        }
    }

    private fun updateList() {
        val adapter = ModelAdapter(filteredModels, currentDefaultModelId) { modelItem ->
            listener?.onDefaultModelSelected(modelItem.model)
            aiModelManager.setDefaultModel(modelItem.model.id)
            Toast.makeText(context, "已设置默认模型: ${modelItem.model.displayName}", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        recyclerModels.layoutManager = LinearLayoutManager(context)
        recyclerModels.adapter = adapter
    }

    private fun setupButtons() {
        buttonCancel.setOnClickListener { dismiss() }
        buttonClear.setOnClickListener {
            listener?.onDefaultModelSelected(null)
            aiModelManager.setDefaultModel(null)
            Toast.makeText(context, "已清除默认模型", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setupSearch() {
        editSearchModel.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim() ?: ""
                filteredModels = if (query.isEmpty()) {
                    allModels
                } else {
                    allModels.filter {
                        it.model.displayName.contains(query, ignoreCase = true) ||
                        it.model.modelId.contains(query, ignoreCase = true) ||
                        it.providerName.contains(query, ignoreCase = true)
                    }
                }
                updateList()
            }
        })
    }

    /**
     * 模型列表适配器
     */
    inner class ModelAdapter(
        private val models: List<ModelItem>,
        private val selectedModelId: String?,
        private val onSelect: (ModelItem) -> Unit
    ) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_model_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(models[position])
        }

        override fun getItemCount() = models.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textModelName: TextView = itemView.findViewById(R.id.textModelName)
            private val textModelId: TextView = itemView.findViewById(R.id.textModelId)
            private val textProviderName: TextView = itemView.findViewById(R.id.textProviderName)
            private val iconCheck: ImageButton = itemView.findViewById(R.id.iconCheck)

            fun bind(modelItem: ModelItem) {
                textModelName.text = modelItem.model.displayName
                textModelId.text = modelItem.model.modelId
                textProviderName.text = modelItem.providerName

                val isSelected = modelItem.model.id == selectedModelId
                iconCheck.setImageResource(
                    if (isSelected) android.R.drawable.checkbox_on_background
                    else android.R.drawable.checkbox_off_background
                )

                itemView.setOnClickListener { onSelect(modelItem) }
            }
        }
    }
}

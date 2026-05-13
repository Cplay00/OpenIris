package com.yolo.openiris.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.yolo.openiris.R
import com.yolo.openiris.ai.AiModel

class ModelSettingsDialog : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(model: AiModel): ModelSettingsDialog {
            val dialog = ModelSettingsDialog()
            val args = Bundle()
            args.putString("model_id", model.id)
            args.putString("model_model_id", model.modelId)
            args.putString("model_display_name", model.displayName)
            args.putBoolean("model_has_vision", model.hasVision)
            args.putBoolean("model_enable_reasoning", model.enableReasoning)
            args.putStringArrayList("model_assigned_tasks", ArrayList(model.assignedTasks))
            dialog.arguments = args
            return dialog
        }
    }

    interface OnModelSettingsListener {
        fun onModelSettingsConfirmed(modelId: String, displayName: String, hasVision: Boolean, 
                                     enableReasoning: Boolean, assignedTasks: List<String>,
                                     customHeaders: Map<String, String>, customBody: Map<String, Any>)
    }

    // 使用WeakReference避免内存泄漏
    private var listenerRef: java.lang.ref.WeakReference<OnModelSettingsListener>? = null
    
    var listener: OnModelSettingsListener?
        get() = listenerRef?.get()
        set(value) {
            listenerRef = if (value != null) java.lang.ref.WeakReference(value) else null
        }

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var buttonCancel: MaterialButton
    private lateinit var buttonConfirm: MaterialButton

    private var modelId: String = ""
    private var modelModelId: String = ""
    private var modelDisplayName: String = ""
    private var modelHasVision: Boolean = false
    private var modelEnableReasoning: Boolean = false
    private var modelAssignedTasks: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            modelId = it.getString("model_id", "")
            modelModelId = it.getString("model_model_id", "")
            modelDisplayName = it.getString("model_display_name", "")
            modelHasVision = it.getBoolean("model_has_vision", false)
            modelEnableReasoning = it.getBoolean("model_enable_reasoning", false)
            modelAssignedTasks = it.getStringArrayList("model_assigned_tasks") ?: emptyList()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_model_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)
        buttonCancel = view.findViewById(R.id.buttonCancel)
        buttonConfirm = view.findViewById(R.id.buttonConfirm)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        val adapter = ModelSettingsPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "基本设置"
                1 -> "高级设置"
                else -> ""
            }
        }.attach()
    }

    private fun setupButtons() {
        buttonCancel.setOnClickListener {
            dismiss()
        }

        buttonConfirm.setOnClickListener {
            collectAndReturnSettings()
        }
    }

    private fun collectAndReturnSettings() {
        val basicFragment = childFragmentManager.findFragmentByTag("f0") as? BasicSettingsFragment
        val advancedFragment = childFragmentManager.findFragmentByTag("f1") as? AdvancedSettingsFragment

        val displayName = basicFragment?.getDisplayName() ?: modelDisplayName
        val hasVision = basicFragment?.hasVision() ?: modelHasVision
        val enableReasoning = basicFragment?.hasReasoning() ?: modelEnableReasoning
        val assignedTasks = basicFragment?.getAssignedTasks() ?: modelAssignedTasks
        val customHeaders = advancedFragment?.getCustomHeaders() ?: emptyMap()
        val customBody = advancedFragment?.getCustomBody() ?: emptyMap()

        listener?.onModelSettingsConfirmed(
            modelId, displayName, hasVision, enableReasoning, assignedTasks, customHeaders, customBody
        )
        dismiss()
    }

    // 基本设置 Fragment
    class BasicSettingsFragment : androidx.fragment.app.Fragment() {
        private lateinit var editModelId: TextInputEditText
        private lateinit var editModelName: TextInputEditText
        private lateinit var switchVision: MaterialSwitch
        private lateinit var switchReasoning: MaterialSwitch
        private lateinit var checkboxVisualRecognition: CheckBox
        private lateinit var checkboxDetectionSummary: CheckBox

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.dialog_model_settings_basic, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            editModelId = view.findViewById(R.id.editModelId)
            editModelName = view.findViewById(R.id.editModelName)
            switchVision = view.findViewById(R.id.switchVision)
            switchReasoning = view.findViewById(R.id.switchReasoning)
            checkboxVisualRecognition = view.findViewById(R.id.checkboxVisualRecognition)
            checkboxDetectionSummary = view.findViewById(R.id.checkboxDetectionSummary)

            loadData()
        }

        private fun loadData() {
            val args = (parentFragment as? ModelSettingsDialog)?.arguments
            args?.let {
                editModelId.setText(it.getString("model_model_id", ""))
                editModelName.setText(it.getString("model_display_name", ""))
                switchVision.isChecked = it.getBoolean("model_has_vision", false)
                switchReasoning.isChecked = it.getBoolean("model_enable_reasoning", false)
                val tasks = it.getStringArrayList("model_assigned_tasks") ?: emptyList()
                checkboxVisualRecognition.isChecked = tasks.contains(AiModel.TASK_VISUAL_RECOGNITION)
                checkboxDetectionSummary.isChecked = tasks.contains(AiModel.TASK_DETECTION_SUMMARY)
            }
        }

        fun getDisplayName(): String = editModelName.text?.toString()?.trim() ?: ""
        fun hasVision(): Boolean = switchVision.isChecked
        fun hasReasoning(): Boolean = switchReasoning.isChecked
        fun getAssignedTasks(): List<String> {
            val tasks = mutableListOf<String>()
            if (checkboxVisualRecognition.isChecked) tasks.add(AiModel.TASK_VISUAL_RECOGNITION)
            if (checkboxDetectionSummary.isChecked) tasks.add(AiModel.TASK_DETECTION_SUMMARY)
            return tasks
        }
    }

    // 高级设置 Fragment
    class AdvancedSettingsFragment : androidx.fragment.app.Fragment() {
        private lateinit var recyclerHeaders: RecyclerView
        private lateinit var recyclerBody: RecyclerView
        private lateinit var buttonAddHeader: MaterialButton
        private lateinit var buttonAddBody: MaterialButton

        private val headers = mutableListOf<Pair<String, String>>()
        private val bodyParams = mutableListOf<Pair<String, String>>()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.dialog_model_settings_advanced, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            recyclerHeaders = view.findViewById(R.id.recyclerHeaders)
            recyclerBody = view.findViewById(R.id.recyclerBody)
            buttonAddHeader = view.findViewById(R.id.buttonAddHeader)
            buttonAddBody = view.findViewById(R.id.buttonAddBody)

            setupRecyclerViews()
            setupButtons()
        }

        private fun setupRecyclerViews() {
            recyclerHeaders.layoutManager = LinearLayoutManager(context)
            recyclerBody.layoutManager = LinearLayoutManager(context)

            // 添加初始空项
            headers.add(Pair("", ""))
            bodyParams.add(Pair("", ""))

            updateHeadersAdapter()
            updateBodyAdapter()
        }

        private fun setupButtons() {
            buttonAddHeader.setOnClickListener {
                headers.add(Pair("", ""))
                updateHeadersAdapter()
            }

            buttonAddBody.setOnClickListener {
                bodyParams.add(Pair("", ""))
                updateBodyAdapter()
            }
        }

        private fun updateHeadersAdapter() {
            recyclerHeaders.adapter = KeyValueAdapter(headers) { position ->
                headers.removeAt(position)
                updateHeadersAdapter()
            }
        }

        private fun updateBodyAdapter() {
            recyclerBody.adapter = KeyValueAdapter(bodyParams) { position ->
                bodyParams.removeAt(position)
                updateBodyAdapter()
            }
        }

        fun getCustomHeaders(): Map<String, String> {
            val result = mutableMapOf<String, String>()
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
            return result
        }

        fun getCustomBody(): Map<String, Any> {
            val result = mutableMapOf<String, Any>()
            bodyParams.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
            return result
        }
    }

    // KeyValue Adapter
    class KeyValueAdapter(
        private val items: MutableList<Pair<String, String>>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<KeyValueAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val editKey: TextInputEditText = itemView.findViewById(R.id.editKey)
            val editValue: TextInputEditText = itemView.findViewById(R.id.editValue)
            val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_key_value, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (key, value) = items[position]
            holder.editKey.setText(key)
            holder.editValue.setText(value)

            holder.editKey.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    items[position] = Pair(holder.editKey.text?.toString() ?: "", items[position].second)
                }
            }

            holder.editValue.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    items[position] = Pair(items[position].first, holder.editValue.text?.toString() ?: "")
                }
            }

            holder.buttonDelete.setOnClickListener {
                onDelete(position)
            }
        }

        override fun getItemCount() = items.size
    }

    // ViewPager2 Adapter
    private inner class ModelSettingsPagerAdapter(fragment: androidx.fragment.app.Fragment) : 
        androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {
        
        override fun getItemCount() = 2

        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when (position) {
                0 -> BasicSettingsFragment()
                1 -> AdvancedSettingsFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}

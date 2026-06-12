package com.yolo.openiris

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
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
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.ai.AiModel
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.ai.AiProvider
import com.yolo.openiris.dialog.DefaultModelPickerDialog
import kotlinx.coroutines.launch

/**
 * AI 模型设置页面
 */
class AiModelSettingsActivity : AppCompatActivity() {

    private lateinit var aiModelManager: AiModelManager
    private lateinit var switchAiEnabled: MaterialSwitch
    private lateinit var editCallInterval: TextInputEditText
    private lateinit var editVisualPrompt: TextInputEditText
    private lateinit var editSummaryPrompt: TextInputEditText
    private lateinit var buttonSaveVisualPrompt: MaterialButton
    private lateinit var buttonSaveSummaryPrompt: MaterialButton
    private lateinit var textDefaultModelName: MaterialTextView
    private lateinit var textDefaultModelProvider: MaterialTextView
    private lateinit var buttonSelectDefaultModel: MaterialButton
    private lateinit var recyclerProviders: RecyclerView
    private lateinit var buttonAddProvider: MaterialButton

    private lateinit var providerAdapter: AiProviderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_model_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        aiModelManager = AiModelManager.getInstance(this)

        initViews()
        loadData()
    }

    private fun initViews() {
        // 工具栏
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // AI 开关
        switchAiEnabled = findViewById(R.id.switchAiEnabled)
        switchAiEnabled.setOnCheckedChangeListener { _, isChecked ->
            aiModelManager.setAiEnabled(isChecked)
        }

        // 调用间隔
        editCallInterval = findViewById(R.id.editCallInterval)

        // 提示词输入框
        editVisualPrompt = findViewById(R.id.editVisualPrompt)
        editSummaryPrompt = findViewById(R.id.editSummaryPrompt)
        
        // 修复提示词滚动:阻止父容器拦截触摸事件
        editVisualPrompt.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            v.onTouchEvent(event)
            true
        }
        editSummaryPrompt.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            v.onTouchEvent(event)
            true
        }

        // 提示词保存按钮
        buttonSaveVisualPrompt = findViewById(R.id.buttonSaveVisualPrompt)
        buttonSaveSummaryPrompt = findViewById(R.id.buttonSaveSummaryPrompt)
        buttonSaveVisualPrompt.setOnClickListener { saveVisualPrompt() }
        buttonSaveSummaryPrompt.setOnClickListener { saveSummaryPrompt() }

        // 默认模型选择
        textDefaultModelName = findViewById(R.id.textDefaultModelName)
        textDefaultModelProvider = findViewById(R.id.textDefaultModelProvider)
        buttonSelectDefaultModel = findViewById(R.id.buttonSelectDefaultModel)
        buttonSelectDefaultModel.setOnClickListener { showDefaultModelPicker() }
        findViewById<LinearLayout>(R.id.layoutDefaultModel).setOnClickListener { showDefaultModelPicker() }

        // 提供商列表
        recyclerProviders = findViewById(R.id.recyclerProviders)
        recyclerProviders.layoutManager = LinearLayoutManager(this)

        providerAdapter = AiProviderAdapter(
            onProviderClick = { provider ->
                val intent = Intent(this, AiProviderEditActivity::class.java)
                intent.putExtra("provider_id", provider.id)
                startActivity(intent)
            },
            onDeleteClick = { provider ->
                aiModelManager.deleteProvider(provider.id)
                loadData()
                Toast.makeText(this, "提供商已删除", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerProviders.adapter = providerAdapter

        // 添加提供商按钮
        buttonAddProvider = findViewById(R.id.buttonAddProvider)
        buttonAddProvider.setOnClickListener {
            val intent = Intent(this, AiProviderEditActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadData() {
        // 加载 AI 启用状态(临时移除监听避免递归触发)
        switchAiEnabled.setOnCheckedChangeListener(null)
        switchAiEnabled.isChecked = aiModelManager.isAiEnabled()
        switchAiEnabled.setOnCheckedChangeListener { _, isChecked ->
            aiModelManager.setAiEnabled(isChecked)
        }

        // 加载调用间隔
        editCallInterval.setText(aiModelManager.getCallIntervalSeconds().toString())

        // 加载提示词
        editVisualPrompt.setText(aiModelManager.getVisualRecognitionPrompt())
        editSummaryPrompt.setText(aiModelManager.getDetectionSummaryPrompt())

        // 加载默认模型
        updateDefaultModelDisplay()

        // 加载提供商列表
        val providers = aiModelManager.getProviders()
        providerAdapter.submitList(providers)
    }

    private fun updateDefaultModelDisplay() {
        val defaultModel = aiModelManager.getDefaultModel()
        if (defaultModel != null) {
            val provider = aiModelManager.getProvider(defaultModel.providerId)
            textDefaultModelName.text = defaultModel.displayName
            textDefaultModelProvider.text = provider?.name ?: "未知提供商"
        } else {
            textDefaultModelName.text = "未选择"
            textDefaultModelProvider.text = "点击选择默认模型"
        }
    }

    private fun showDefaultModelPicker() {
        val dialog = DefaultModelPickerDialog()
        dialog.listener = object : DefaultModelPickerDialog.OnModelSelectedListener {
            override fun onDefaultModelSelected(model: AiModel?) {
                updateDefaultModelDisplay()
            }
        }
        dialog.show(supportFragmentManager, "DefaultModelPickerDialog")
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onPause() {
        super.onPause()
        // 保存调用间隔
        val interval = (editCallInterval.text?.toString()?.toIntOrNull() ?: 5).coerceAtLeast(1)
        aiModelManager.setCallIntervalSeconds(interval)
    }

    private fun saveVisualPrompt() {
        val prompt = editVisualPrompt.text?.toString()?.trim() ?: ""
        if (prompt.isNotBlank()) {
            aiModelManager.setVisualRecognitionPrompt(prompt)
            Toast.makeText(this, "视觉识别提示词已保存", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "提示词不能为空", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSummaryPrompt() {
        val prompt = editSummaryPrompt.text?.toString()?.trim() ?: ""
        if (prompt.isNotBlank()) {
            aiModelManager.setDetectionSummaryPrompt(prompt)
            Toast.makeText(this, "检测总结提示词已保存", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "提示词不能为空", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 提供商列表适配器
     */
    inner class AiProviderAdapter(
        private val onProviderClick: (AiProvider) -> Unit,
        private val onDeleteClick: (AiProvider) -> Unit
    ) : RecyclerView.Adapter<AiProviderAdapter.ViewHolder>() {

        private var providers: List<AiProvider> = emptyList()

        fun submitList(list: List<AiProvider>) {
            providers = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_provider, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(providers[position])
        }

        override fun getItemCount() = providers.size

        inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val textProviderName: android.widget.TextView = itemView.findViewById(R.id.textProviderName)
            private val textBaseUrl: android.widget.TextView = itemView.findViewById(R.id.textBaseUrl)
            private val textModelCount: android.widget.TextView = itemView.findViewById(R.id.textModelCount)
            private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)

            fun bind(provider: AiProvider) {
                textProviderName.text = provider.name
                textBaseUrl.text = provider.baseUrl
                textModelCount.text = "${provider.models.size} 个模型"

                itemView.setOnClickListener { onProviderClick(provider) }
                buttonDelete.setOnClickListener { onDeleteClick(provider) }
            }
        }
    }
}

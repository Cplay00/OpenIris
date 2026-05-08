package com.yolo.openiris

import android.content.Intent
import android.os.Bundle
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
import com.yolo.openiris.ai.AiModelManager
import com.yolo.openiris.ai.AiProvider
import kotlinx.coroutines.launch

/**
 * AI 模型设置页面
 */
class AiModelSettingsActivity : AppCompatActivity() {

    private lateinit var aiModelManager: AiModelManager
    private lateinit var switchAiEnabled: MaterialSwitch
    private lateinit var editCallInterval: TextInputEditText
    private lateinit var recyclerProviders: RecyclerView
    private lateinit var buttonAddProvider: MaterialButton

    private lateinit var providerAdapter: AiProviderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_model_settings)

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
        // 加载 AI 启用状态
        switchAiEnabled.isChecked = aiModelManager.isAiEnabled()

        // 加载调用间隔
        editCallInterval.setText(aiModelManager.getCallIntervalSeconds().toString())

        // 加载提供商列表
        val providers = aiModelManager.getProviders()
        providerAdapter.submitList(providers)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onPause() {
        super.onPause()
        // 保存调用间隔
        val interval = editCallInterval.text.toString().toIntOrNull() ?: 5
        aiModelManager.setCallIntervalSeconds(interval)
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

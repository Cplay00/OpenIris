package com.yolo.openiris.dialog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.yolo.openiris.R
import com.yolo.openiris.ai.AiModel
import com.yolo.openiris.ai.AiModelManager
import kotlinx.coroutines.*

/**
 * 妯″瀷杩炴帴娴嬭瘯瀵硅瘽妗? */
class ConnectionTestDialog : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(providerId: String): ConnectionTestDialog {
            val dialog = ConnectionTestDialog()
            val args = Bundle()
            args.putString("provider_id", providerId)
            dialog.arguments = args
            return dialog
        }
    }

    private lateinit var aiModelManager: AiModelManager
    private lateinit var spinnerTestModel: AutoCompleteTextView
    private lateinit var textNonStreamResult: MaterialTextView
    private lateinit var textStreamResult: MaterialTextView
    private lateinit var buttonCancel: MaterialButton
    private lateinit var buttonTest: MaterialButton

    private var providerId: String = ""
    private var models: List<AiModel> = emptyList()
    private var selectedModel: AiModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        providerId = arguments?.getString("provider_id") ?: ""
        aiModelManager = AiModelManager.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_connection_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerTestModel = view.findViewById(R.id.spinnerTestModel)
        textNonStreamResult = view.findViewById(R.id.textNonStreamResult)
        textStreamResult = view.findViewById(R.id.textStreamResult)
        buttonCancel = view.findViewById(R.id.buttonCancel)
        buttonTest = view.findViewById(R.id.buttonTest)

        loadModels()
        setupButtons()
    }

    private fun loadModels() {
        if (com.yolo.openiris.BuildConfig.DEBUG) {
            Log.d("ConnectionTest", "Loading models for providerId: '$providerId'")
        }
        
        if (providerId.isBlank()) {
            Log.e("ConnectionTest", "Provider ID is blank")
            Toast.makeText(context, "鎻愪緵鍟咺D涓虹┖锛岃鍏堜繚瀛樻彁渚涘晢", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        
        val provider = aiModelManager.getProvider(providerId)
        if (provider == null) {
            if (com.yolo.openiris.BuildConfig.DEBUG) {
                Log.e("ConnectionTest", "Provider not found: '$providerId'")
                val allProviders = aiModelManager.getProviders()
                Log.d("ConnectionTest", "Available providers (${allProviders.size}):")
                allProviders.forEach { p ->
                    Log.d("ConnectionTest", "  - ID: '${p.id}', Name: '${p.name}', Models: ${p.models.size}")
                }
            } else {
                Log.e("ConnectionTest", "Provider not found")
            }
            Toast.makeText(context, "鎻愪緵鍟嗕笉瀛樺湪 (ID: $providerId)", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }

        if (com.yolo.openiris.BuildConfig.DEBUG) {
            Log.d("ConnectionTest", "Found provider: ${provider.name}, models: ${provider.models.size}")
        }
        models = provider.models.filter { it.isEnabled }
        if (com.yolo.openiris.BuildConfig.DEBUG) {
            Log.d("ConnectionTest", "Enabled models: ${models.size}")
        }
        if (models.isEmpty()) {
            Toast.makeText(context, "娌℃湁鍙敤鐨勬ā鍨?, Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        val modelNames = models.map { "${it.displayName} (${it.modelId})" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, modelNames)
        spinnerTestModel.setAdapter(adapter)
        spinnerTestModel.setOnItemClickListener { _, _, position, _ ->
            selectedModel = models[position]
        }

        // 榛樿閫夋嫨绗竴涓ā鍨?        if (models.isNotEmpty()) {
            selectedModel = models[0]
            spinnerTestModel.setText(modelNames[0], false)
        }
    }

    private fun setupButtons() {
        buttonCancel.setOnClickListener { dismiss() }
        buttonTest.setOnClickListener { startTest() }
    }

    private fun startTest() {
        val model = selectedModel
        if (model == null) {
            Toast.makeText(context, "璇烽€夋嫨娴嬭瘯妯″瀷", Toast.LENGTH_SHORT).show()
            return
        }

        buttonTest.isEnabled = false
        buttonTest.text = "娴嬭瘯涓?.."
        textNonStreamResult.text = "娴嬭瘯涓?.."
        textStreamResult.text = "娴嬭瘯涓?.."

        lifecycleScope.launch {
            // 娴嬭瘯闈炴祦寮?            val nonStreamResult = aiModelManager.testConnectionNonStream(model)
            updateResult(textNonStreamResult, nonStreamResult)

            // 娴嬭瘯娴佸紡
            val streamResult = aiModelManager.testConnectionStream(model)
            updateResult(textStreamResult, streamResult)

            buttonTest.isEnabled = true
            buttonTest.text = "娴嬭瘯"
        }
    }

    private fun updateResult(textView: MaterialTextView, result: com.yolo.openiris.ai.TestResult) {
        if (result.success) {
            textView.text = "鉁?鎴愬姛 (${result.durationMs}ms)"
            textView.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            textView.text = "鉁?澶辫触: ${result.message}"
            textView.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }
    }
}

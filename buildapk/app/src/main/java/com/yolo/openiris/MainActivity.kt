package com.yolo.openiris

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OpenIris-Main"
    }

    private lateinit var yolov11Ncnn: Yolov11Ncnn
    private var currentModel = 0
    private var currentCpuGpu = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Log.w(TAG, "Some permissions denied")
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { videoUri ->
            val intent = Intent(this, VideoDetectActivity::class.java)
            intent.putExtra("video_uri", videoUri.toString())
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        yolov11Ncnn = Yolov11Ncnn()

        // Setup action buttons
        val buttonRealtimeDetect: MaterialButton = findViewById(R.id.buttonRealtimeDetect)
        buttonRealtimeDetect.setOnClickListener {
            startActivity(Intent(this, RealtimeDetectActivity::class.java))
        }

        val buttonImageDetect: MaterialButton = findViewById(R.id.buttonImageDetect)
        buttonImageDetect.setOnClickListener {
            startActivity(Intent(this, ImageDetectActivity::class.java))
        }

        val buttonVideoDetect: MaterialButton = findViewById(R.id.buttonVideoDetect)
        buttonVideoDetect.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        val buttonSettings: MaterialButton = findViewById(R.id.buttonSettings)
        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Setup model dropdown
        val spinnerModel: MaterialAutoCompleteTextView = findViewById(R.id.spinnerModel)
        val modelAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.model_array,
            android.R.layout.simple_list_item_1
        )
        spinnerModel.setAdapter(modelAdapter)
        spinnerModel.setText(modelAdapter.getItem(0).toString(), false)
        spinnerModel.setOnItemClickListener { _, _, position, _ ->
            currentModel = position
        }

        // Setup CPU/GPU dropdown
        val spinnerCPUGPU: MaterialAutoCompleteTextView = findViewById(R.id.spinnerCPUGPU)
        val cpuGpuAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.cpugpu_array,
            android.R.layout.simple_list_item_1
        )
        spinnerCPUGPU.setAdapter(cpuGpuAdapter)
        spinnerCPUGPU.setText(cpuGpuAdapter.getItem(0).toString(), false)
        spinnerCPUGPU.setOnItemClickListener { _, _, position, _ ->
            currentCpuGpu = position
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    fun getCurrentModel(): Int = currentModel
    fun getCurrentCpuGpu(): Int = currentCpuGpu
}

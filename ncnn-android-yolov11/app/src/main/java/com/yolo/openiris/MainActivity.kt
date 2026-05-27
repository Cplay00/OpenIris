package com.yolo.openiris

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OpenIris-Main"
    }

    private lateinit var yolov11ncnn: Yolov11Ncnn
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

        yolov11ncnn = Yolov11Ncnn()

        val buttonRealtimeDetect: Button = findViewById(R.id.buttonRealtimeDetect)
        buttonRealtimeDetect.setOnClickListener {
            val intent = Intent(this, RealtimeDetectActivity::class.java)
            startActivity(intent)
        }

        val buttonImageDetect: Button = findViewById(R.id.buttonImageDetect)
        buttonImageDetect.setOnClickListener {
            val intent = Intent(this, ImageDetectActivity::class.java)
            startActivity(intent)
        }

        val buttonVideoDetect: Button = findViewById(R.id.buttonVideoDetect)
        buttonVideoDetect.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        val buttonSettings: Button = findViewById(R.id.buttonSettings)
        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        val spinnerModel: Spinner = findViewById(R.id.spinnerModel)
        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentModel = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val spinnerCPUGPU: Spinner = findViewById(R.id.spinnerCPUGPU)
        spinnerCPUGPU.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentCpuGpu = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

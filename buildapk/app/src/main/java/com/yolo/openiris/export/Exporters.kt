package com.yolo.openiris.export

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yolo.openiris.config.ConfigManager
import com.yolo.openiris.detection.AnalysisResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonExporter {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    }

    fun export(context: Context, result: AnalysisResult): ExportResult {
        return try {
            val configManager = ConfigManager.getInstance(context)
            val jsonPath = configManager.getJsonExportPath()

            val timestamp = getTimestamp()
            val fileName = "openiris_analysis_${timestamp}.json"

            // 使用公共存储目录
            val baseDir = Environment.getExternalStorageDirectory()
            val exportDir = if (jsonPath.isNotBlank()) {
                File(baseDir, jsonPath)
            } else {
                File(baseDir, "YOLO_Export/JSON")
            }
            exportDir.mkdirs()

            val file = File(exportDir, fileName)

            val jsonData = ExportData(
                version = "1.0",
                exportTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()),
                appVersion = getAppVersion(context),
                deviceInfo = DeviceInfo(
                    model = android.os.Build.MODEL,
                    androidVersion = android.os.Build.VERSION.RELEASE,
                    sdkVersion = android.os.Build.VERSION.SDK_INT
                ),
                analysisResult = result
            )

            file.writeText(gson.toJson(jsonData))

            ExportResult(
                type = ExportType.JSON,
                filePath = file.absolutePath,
                success = true
            )
        } catch (e: Exception) {
            ExportResult(
                type = ExportType.JSON,
                filePath = "",
                success = false,
                errorMessage = e.message
            )
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}

object ImageExporter {

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    }

    fun export(context: Context, bitmap: Bitmap): ExportResult {
        return try {
            val configManager = ConfigManager.getInstance(context)
            val imagePath = configManager.getImageExportPath()

            val timestamp = getTimestamp()
            val fileName = "openiris_annotated_${timestamp}.jpg"

            // 使用公共存储目录
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val exportDir = if (imagePath.isNotBlank() && imagePath != "Pictures") {
                File(Environment.getExternalStorageDirectory(), imagePath)
            } else {
                File(baseDir, "OpenIris")
            }
            exportDir.mkdirs()

            val file = File(exportDir, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            ExportResult(
                type = ExportType.ANNOTATED_IMAGE,
                filePath = file.absolutePath,
                success = true
            )
        } catch (e: Exception) {
            ExportResult(
                type = ExportType.ANNOTATED_IMAGE,
                filePath = "",
                success = false,
                errorMessage = e.message
            )
        }
    }
}

data class ExportData(
    val version: String,
    val exportTime: String,
    val appVersion: String,
    val deviceInfo: DeviceInfo,
    val analysisResult: AnalysisResult
)

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int
)

data class ExportResult(
    val type: ExportType,
    val filePath: String,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class ExportType {
    JSON,
    ANNOTATED_IMAGE,
    ANNOTATED_VIDEO
}

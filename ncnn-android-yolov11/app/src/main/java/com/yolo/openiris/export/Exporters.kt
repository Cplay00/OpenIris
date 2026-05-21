package com.yolo.openiris.export

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yolo.openiris.detection.AnalysisResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JSON 导出器
 */
object JsonExporter {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * 导出分析结果为 JSON 文件
     */
    fun export(context: Context, result: AnalysisResult): ExportResult {
        return try {
            val timestamp = dateFormat.format(Date())
            val fileName = "openiris_analysis_${timestamp}.json"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

            file.parentFile?.mkdirs()

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

/**
 * 图片导出器
 */
object ImageExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * 导出标注后的图片
     */
    fun export(context: Context, bitmap: Bitmap): ExportResult {
        return try {
            val timestamp = dateFormat.format(Date())
            val fileName = "openiris_annotated_${timestamp}.jpg"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

            file.parentFile?.mkdirs()

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

/**
 * 导出数据类
 */
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

/**
 * 导出结果
 */
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

package com.yolo.openiris.label

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStreamReader

/**
 * 标签预设管理器
 * 管理预置的标签文件，支持加载、列表、匹配等功能
 */
class LabelPresetManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LabelPresetManager"
        private const val LABELS_DIR = "labels"
        
        // 预设标签文件名
        const val PRESET_COCO_80 = "coco_80"
        const val PRESET_COCO_128 = "coco_128"
        const val PRESET_VOC_20 = "voc_20"
        const val PRESET_IMAGENET_1000 = "imagenet_1000"
        
        // 预设标签显示名称
        val PRESET_DISPLAY_NAMES = mapOf(
            PRESET_COCO_80 to "COCO 80类（默认）",
            PRESET_COCO_128 to "COCO 133类（扩展）",
            PRESET_VOC_20 to "Pascal VOC 20类",
            PRESET_IMAGENET_1000 to "ImageNet 993类"
        )
        
        // 预设标签描述
        val PRESET_DESCRIPTIONS = mapOf(
            PRESET_COCO_80 to "YOLOv8/v11默认训练数据集，包含80个常见目标类别",
            PRESET_COCO_128 to "COCO扩展版，包含133个目标类别（含物体和材质）",
            PRESET_VOC_20 to "Pascal VOC数据集，包含20个经典目标检测类别",
            PRESET_IMAGENET_1000 to "ImageNet大规模图像识别数据集，包含993个类别"
        )
        
        // 预设标签数量
        val PRESET_LABEL_COUNTS = mapOf(
            PRESET_COCO_80 to 80,
            PRESET_COCO_128 to 133,
            PRESET_VOC_20 to 20,
            PRESET_IMAGENET_1000 to 993
        )
    }
    
    /**
     * 获取所有可用的预设标签
     * @return 预设名称列表
     */
    fun getAvailablePresets(): List<String> {
        return listOf(PRESET_COCO_80, PRESET_COCO_128, PRESET_VOC_20, PRESET_IMAGENET_1000)
    }
    
    /**
     * 获取预设标签的显示名称
     * @param presetName 预设名称
     * @return 显示名称
     */
    fun getPresetDisplayName(presetName: String): String {
        return PRESET_DISPLAY_NAMES[presetName] ?: presetName
    }
    
    /**
     * 获取预设标签的描述
     * @param presetName 预设名称
     * @return 描述信息
     */
    fun getPresetDescription(presetName: String): String {
        return PRESET_DESCRIPTIONS[presetName] ?: ""
    }
    
    /**
     * 获取预设标签的数量
     * @param presetName 预设名称
     * @return 标签数量
     */
    fun getPresetLabelCount(presetName: String): Int {
        return PRESET_LABEL_COUNTS[presetName] ?: 0
    }
    
    /**
     * 从assets加载预设标签
     * @param presetName 预设名称
     * @return 标签列表，如果加载失败返回null
     */
    fun loadPresetLabels(presetName: String): List<String>? {
        return try {
            val fileName = "$LABELS_DIR/$presetName.txt"
            val inputStream = context.assets.open(fileName)
            val labels = InputStreamReader(inputStream).buffered().use { reader ->
                reader.readLines()
                    .map { it.trimStart('\uFEFF').trim() }
                    .filter { it.isNotEmpty() }
            }
            Log.d(TAG, "Loaded ${labels.size} labels from preset: $presetName")
            labels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preset labels: $presetName", e)
            null
        }
    }
    
    /**
     * 根据标签数量自动推荐预设
     * @param labelCount 标签数量
     * @return 推荐的预设名称，如果没有匹配返回null
     */
    fun suggestPresetByLabelCount(labelCount: Int): String? {
        return when (labelCount) {
            80 -> PRESET_COCO_80
            128 -> PRESET_COCO_128
            20 -> PRESET_VOC_20
            1000 -> PRESET_IMAGENET_1000
            else -> {
                // 尝试找最接近的预设
                val counts = PRESET_LABEL_COUNTS.values.toList()
                val closest = counts.minByOrNull { Math.abs(it - labelCount) }
                if (closest != null && Math.abs(closest - labelCount) <= 10) {
                    PRESET_LABEL_COUNTS.entries.find { it.value == closest }?.key
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * 将预设标签复制到指定目录
     * @param presetName 预设名称
     * @param targetDir 目标目录
     * @return 是否成功
     */
    fun copyPresetToDirectory(presetName: String, targetDir: File): Boolean {
        return try {
            val labels = loadPresetLabels(presetName)
            if (labels != null) {
                val labelsFile = File(targetDir, "labels.txt")
                labelsFile.writeText(labels.joinToString("\n"))
                Log.d(TAG, "Copied ${labels.size} labels to: ${labelsFile.absolutePath}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy preset labels", e)
            false
        }
    }
    
    /**
     * 从文件加载标签
     * @param file 标签文件
     * @return 标签列表
     */
    fun loadLabelsFromFile(file: File): List<String>? {
        return try {
            if (!file.exists()) {
                Log.w(TAG, "Labels file does not exist: ${file.absolutePath}")
                return null
            }
            val labels = file.bufferedReader().use { reader ->
                reader.readLines()
                    .map { it.trimStart('\uFEFF').trim() }
                    .filter { it.isNotEmpty() }
            }
            if (labels.isEmpty()) {
                Log.w(TAG, "Labels file is empty: ${file.absolutePath}")
                null
            } else {
                Log.d(TAG, "Loaded ${labels.size} labels from file: ${file.absolutePath}")
                labels
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels from file: ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * 根据文件内容识别预设类型
     * @param labels 标签列表
     * @return 识别出的预设名称，如果无法识别返回null
     */
    fun identifyPresetFromLabels(labels: List<String>): String? {
        val labelSet = labels.toSet()
        
        // 尝试匹配COCO 80类
        val coco80Labels = loadPresetLabels(PRESET_COCO_80)
        if (coco80Labels != null && labelSet.containsAll(coco80Labels.take(10))) {
            return PRESET_COCO_80
        }
        
        // 尝试匹配VOC 20类
        val voc20Labels = loadPresetLabels(PRESET_VOC_20)
        if (voc20Labels != null && labelSet.containsAll(voc20Labels.take(5))) {
            return PRESET_VOC_20
        }
        
        // 根据数量推荐
        return suggestPresetByLabelCount(labels.size)
    }
    
    /**
     * 创建自定义标签文件
     * @param labels 标签列表
     * @param targetDir 目标目录
     * @return 是否成功
     */
    fun createCustomLabelsFile(labels: List<String>, targetDir: File): Boolean {
        return try {
            val labelsFile = File(targetDir, "labels.txt")
            labelsFile.writeText(labels.joinToString("\n"))
            Log.d(TAG, "Created custom labels file with ${labels.size} labels")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom labels file", e)
            false
        }
    }
}

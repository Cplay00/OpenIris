package com.yolo.openiris.fusion

import com.yolo.openiris.detection.BoundingBox
import com.yolo.openiris.detection.DetectedObject
import com.yolo.openiris.detection.DetectionResult
import com.yolo.openiris.vlm.VlmObject
import com.yolo.openiris.vlm.VlmResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFusionTest {

    @Test
    fun `simpleFusion YOLO 和 VLM 结果匹配应生成 high confidence`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("person", 0, 0.9f, BoundingBox(10f, 20f, 100f, 200f)),
                DetectedObject("car", 1, 0.8f, BoundingBox(50f, 50f, 150f, 100f))
            )
        )
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("person", 2, listOf("站立")),
                VlmObject("car", 1, listOf("红色"))
            )
        )

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        assertNotNull(result)
        assertTrue(result.objects.isNotEmpty())

        val personObj = result.objects.find { it.name == "person" }
        assertNotNull(personObj)
        assertEquals("high", personObj!!.confidence)
        assertTrue(personObj.evidence.containsAll(listOf("yolo", "vlm")))
    }

    @Test
    fun `simpleFusion 仅 YOLO 检测到应生成 medium confidence`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("dog", 0, 0.7f, BoundingBox(10f, 20f, 100f, 200f))
            )
        )
        val vlmResult = VlmResult()

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        val dogObj = result.objects.find { it.name == "dog" }
        assertNotNull(dogObj)
        assertEquals("medium", dogObj!!.confidence)
        assertTrue(dogObj.evidence.contains("yolo"))
        assertFalse(dogObj.evidence.contains("vlm"))
    }

    @Test
    fun `simpleFusion 仅 VLM 检测到应生成 medium confidence`() {
        val yoloResult = DetectionResult()
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("tree", 3, listOf("绿色"))
            )
        )

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        val treeObj = result.objects.find { it.name == "tree" }
        assertNotNull(treeObj)
        assertEquals("medium", treeObj!!.confidence)
        assertTrue(treeObj.evidence.contains("vlm"))
        assertFalse(treeObj.evidence.contains("yolo"))
    }

    @Test
    fun `simpleFusion 数量不一致应生成差异报告`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("person", 0, 0.9f, BoundingBox(10f, 20f, 100f, 200f))
            )
        )
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("person", 3, listOf("站立"))
            )
        )

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        assertTrue(result.hasDiscrepancies())
        val countMismatch = result.discrepancies.find { it.type == "count_mismatch" }
        assertNotNull(countMismatch)
        assertTrue(countMismatch!!.description.contains("person"))
    }

    @Test
    fun `simpleFusion 仅在一个来源中检测到应生成差异报告`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("car", 0, 0.8f, BoundingBox(50f, 50f, 150f, 100f))
            )
        )
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("tree", 1, listOf("绿色"))
            )
        )

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        assertTrue(result.hasDiscrepancies())
        val onlyYolo = result.discrepancies.find { it.type == "only_in_yolo" }
        val onlyVlm = result.discrepancies.find { it.type == "only_in_vlm" }
        assertNotNull(onlyYolo)
        assertNotNull(onlyVlm)
    }

    @Test
    fun `simpleFusion 空结果应返回空摘要`() {
        val yoloResult = DetectionResult()
        val vlmResult = VlmResult()

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        assertNotNull(result)
        assertTrue(result.objects.isEmpty())
        assertFalse(result.hasDiscrepancies())
    }

    @Test
    fun `simpleFusion summary 应包含场景描述`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("person", 0, 0.9f, BoundingBox(10f, 20f, 100f, 200f))
            )
        )
        val vlmResult = VlmResult(
            sceneSummary = "城市街道场景"
        )

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        assertTrue(result.summary.contains("城市街道场景"))
    }

    @Test
    fun `simpleFusion summary 应包含检测对象数量`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("person", 0, 0.9f, BoundingBox(10f, 20f, 100f, 200f)),
                DetectedObject("car", 1, 0.8f, BoundingBox(50f, 50f, 150f, 100f))
            )
        )
        val vlmResult = VlmResult()

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        assertTrue(result.summary.contains("2 类对象"))
    }

    @Test
    fun `simpleFusion VLM 属性应传递到融合结果`() {
        val yoloResult = DetectionResult()
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("car", 1, listOf("红色", "轿车"))
            )
        )

        val result = ResultFusion.simpleFusion(yoloResult, vlmResult)

        val carObj = result.objects.find { it.name == "car" }
        assertNotNull(carObj)
        assertTrue(carObj!!.attributes.containsAll(listOf("红色", "轿车")))
    }

    @Test
    fun `isConsistent 两个来源都有结果时应返回 true`() {
        val yoloResult = DetectionResult(
            objects = listOf(
                DetectedObject("person", 0, 0.9f, BoundingBox(10f, 20f, 100f, 200f))
            )
        )
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("person", 1, listOf("站立"))
            )
        )

        assertTrue(ResultFusion.isConsistent(yoloResult, vlmResult))
    }

    @Test
    fun `isConsistent 任一来源为空时应返回 false`() {
        val yoloResult = DetectionResult()
        val vlmResult = VlmResult(
            objects = listOf(
                VlmObject("person", 1, listOf("站立"))
            )
        )

        assertFalse(ResultFusion.isConsistent(yoloResult, vlmResult))
    }
}

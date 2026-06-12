package com.yolo.openiris.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingWindowTrackerTest {

    @Test
    fun `addDetection should track object stats`() {
        val tracker = SlidingWindowTracker(windowDurationMs = 10000)
        tracker.addDetection("person", 0.95f)
        tracker.addDetection("person", 0.90f)
        tracker.addDetection("car", 0.85f)

        val summary = tracker.getSummary()
        assertEquals(2, summary.size)
        assertEquals(2, summary["person"]?.count)
        assertEquals(1, summary["car"]?.count)
    }

    @Test
    fun `addDetections should batch add correctly`() {
        val tracker = SlidingWindowTracker(windowDurationMs = 10000)
        val detections = listOf(
            Pair("person", 0.95f),
            Pair("person", 0.90f),
            Pair("car", 0.85f)
        )
        tracker.addDetections(detections)

        val summary = tracker.getSummary()
        assertEquals(2, summary.size)
        assertEquals(2, summary["person"]?.count)
        assertEquals(1, summary["car"]?.count)
    }

    @Test
    fun `getTotalCount should return correct count`() {
        val tracker = SlidingWindowTracker(windowDurationMs = 10000)
        tracker.addDetection("person", 0.95f)
        tracker.addDetection("car", 0.85f)

        assertEquals(2, tracker.getTotalCount())
    }

    @Test
    fun `empty tracker should have zero counts`() {
        val tracker = SlidingWindowTracker(windowDurationMs = 10000)
        assertEquals(0, tracker.getTotalCount())
        assertTrue(tracker.getSummary().isEmpty())
    }
}

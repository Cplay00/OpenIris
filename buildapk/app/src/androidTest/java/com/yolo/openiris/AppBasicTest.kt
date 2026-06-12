package com.yolo.openiris

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic instrumentation tests for OpenIris app.
 */
@RunWith(AndroidJUnit4::class)
class AppBasicTest {

    @Test
    fun appContextPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.yolo.openiris", appContext.packageName)
    }

    @Test
    fun appContextNotNull() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(appContext)
    }
}

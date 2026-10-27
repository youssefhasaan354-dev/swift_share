package com.swiftshare.common

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerTest {

    @Test
    fun testAppLoggerEmitsUserVisibleError() = runBlocking {
        val errorMessage = "Critical Wi-Fi Direct connection timeout"
        AppLogger.e("TestTag", errorMessage, notifyUser = true)

        val emitted = AppLogger.userVisibleErrors.first()
        assertEquals(errorMessage, emitted)
    }

    @Test
    fun testAppLoggerHandlesLoggingWithoutException() {
        var success = false
        try {
            AppLogger.d("TestTag", "Debug message")
            AppLogger.i("TestTag", "Info message")
            AppLogger.w("TestTag", "Warning message", RuntimeException("Warn"))
            AppLogger.e("TestTag", "Error message", RuntimeException("Err"), notifyUser = false)
            success = true
        } catch (_: Exception) {
            success = false
        }
        assertTrue("AppLogger methods must execute safely without throwing uncaught exceptions", success)
    }
}

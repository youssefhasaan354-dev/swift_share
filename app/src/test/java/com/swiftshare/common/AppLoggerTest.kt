package com.swiftshare.common

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerTest {

    @Test(timeout = 60000)
    fun testAppLoggerEmitsUserVisibleError() = runTest {
        val errorMessage = "Critical Wi-Fi Direct connection timeout"
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                AppLogger.userVisibleErrors.first()
            }
        }
        AppLogger.e("TestTag", errorMessage, notifyUser = true)
        assertEquals(errorMessage, result.await())
    }

    @Test(timeout = 60000)
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

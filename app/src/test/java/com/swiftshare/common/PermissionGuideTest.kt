package com.swiftshare.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionGuideTest {

    @Test
    fun testCategorizedPermissionsNotEmpty() {
        val nearby = PermissionManager.getNearbyPermissions()
        val storage = PermissionManager.getStoragePermissions()
        val camera = PermissionManager.getCameraPermissions()
        val bluetooth = PermissionManager.getBluetoothPermissions()

        assertTrue("Nearby permissions must not be empty", nearby.isNotEmpty())
        assertTrue("Storage permissions must not be empty", storage.isNotEmpty())
        assertTrue("Camera permissions must not be empty", camera.isNotEmpty())
        assertTrue("Bluetooth permissions must not be empty", bluetooth.isNotEmpty())
    }

    @Test
    fun testAllRequiredPermissionsConsolidated() {
        val all = PermissionManager.getRequiredPermissions()
        assertTrue("Consolidated permissions must include camera", all.contains("android.permission.CAMERA"))
        assertTrue("Consolidated permissions count must match distinct categories", all.size >= 4)
    }
}


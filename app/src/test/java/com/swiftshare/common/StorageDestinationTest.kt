package com.swiftshare.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageDestinationTest {

    @Test
    fun testStorageVolumeTypeValues() {
        assertEquals("internal", StorageVolumeType.INTERNAL.key)
        assertEquals("sd_card", StorageVolumeType.SD_CARD.key)
    }

    @Test
    fun testStorageVolumeInfoDataClass() {
        val root = File(System.getProperty("java.io.tmpdir"), "test_root")
        val download = File(root, "SwiftShare")
        val info = StorageVolumeInfo(
            type = StorageVolumeType.INTERNAL,
            title = "Internal Storage",
            rootDir = root,
            downloadDir = download,
            freeBytes = 10_000_000_000L,
            totalBytes = 64_000_000_000L,
            isRemovable = false,
            isMounted = true
        )

        assertEquals(StorageVolumeType.INTERNAL, info.type)
        assertEquals("Internal Storage", info.title)
        assertEquals(download, info.downloadDir)
        assertEquals(10_000_000_000L, info.freeBytes)
        assertEquals(64_000_000_000L, info.totalBytes)
        assertTrue(info.isMounted)
    }

    @Test
    fun testFreeSpaceCalculationsNonNegative() {
        val tmpDir = File(System.getProperty("java.io.tmpdir") ?: ".")
        val free = tmpDir.freeSpace
        val total = tmpDir.totalSpace

        assertTrue("Free space should be non-negative", free >= 0L)
        assertTrue("Total space should be non-negative", total >= 0L)
    }
}


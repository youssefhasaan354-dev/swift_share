package com.swiftshare.cleaner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CleanerEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(timeout = 60000)
    fun testTempAndCacheFilesDetection() = runBlocking {
        val root = tempFolder.newFolder("storage_root")
        val cacheFile = File(root, "temp_data.cache").apply { writeBytes(ByteArray(1024)) }
        val logFile = File(root, "system.log").apply { writeBytes(ByteArray(2048)) }
        val bakFile = File(root, "backup.bak").apply { writeBytes(ByteArray(512)) }
        val metaFile = File(root, "movie.part.meta").apply { writeBytes(ByteArray(256)) }
        val emptyDir = File(root, "empty_folder").apply { mkdir() }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        val items = engine.startScan()
        val tempItems = items.filter { it.category == CleanerCategory.CACHE_TEMP }

        assertEquals(5, tempItems.size)
        assertTrue(tempItems.all { it.isSelected })
        assertTrue(tempItems.any { it.file == cacheFile })
        assertTrue(tempItems.any { it.file == logFile })
        assertTrue(tempItems.any { it.file == bakFile })
        assertTrue(tempItems.any { it.file == metaFile })
        assertTrue(tempItems.any { it.file == emptyDir })
    }

    @Test(timeout = 60000)
    fun testObsoleteApkDetection() = runBlocking {
        val downloadDir = tempFolder.newFolder("Download")
        val apkFile = File(downloadDir, "OldAppInstaller_v1.apk").apply {
            writeBytes(ByteArray(2048))
        }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(downloadDir) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        val items = engine.startScan()
        val apkItems = items.filter { it.category == CleanerCategory.OBSOLETE_APKS }

        assertEquals(1, apkItems.size)
        assertEquals("OldAppInstaller_v1.apk", apkItems[0].name)
        assertTrue(apkItems[0].isSelected)
    }

    @Test(timeout = 60000)
    fun testAppLeftoversDetection() = runBlocking {
        val root = tempFolder.newFolder("android_root")
        val dataDir = File(root, "data").apply { mkdir() }

        // 1. Leftover folder from uninstalled app
        val leftoverDir = File(dataDir, "com.uninstalled.awesomegame").apply {
            mkdir()
            File(this, "savegame.dat").writeBytes(ByteArray(4096))
        }

        // 2. Folder from currently installed app
        val installedDir = File(dataDir, "com.installed.swiftshare").apply {
            mkdir()
            File(this, "cache.dat").writeBytes(ByteArray(2048))
        }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { setOf("com.installed.swiftshare") },
            ioDispatcher = Dispatchers.Unconfined
        )

        val items = engine.startScan()
        val leftoverItems = items.filter { it.category == CleanerCategory.APP_LEFTOVERS }

        assertEquals(1, leftoverItems.size)
        assertEquals("com.uninstalled.awesomegame", leftoverItems[0].name)
        assertEquals(4096L, leftoverItems[0].size)
        assertTrue(leftoverItems[0].isSelected)
    }

    @Test(timeout = 60000)
    fun testLargeFileDetectionUncheckedByDefault() = runBlocking {
        val root = tempFolder.newFolder("media_root")
        // Create 52 MB file
        val largeFile = File(root, "big_database.bin")
        largeFile.outputStream().use { fos ->
            val chunk = ByteArray(1024 * 1024)
            repeat(52) {
                fos.write(chunk)
            }
        }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        val items = engine.startScan()
        val largeItems = items.filter { it.category == CleanerCategory.LARGE_FILES }

        assertEquals(1, largeItems.size)
        assertEquals("big_database.bin", largeItems[0].name)
        assertFalse("Large files must NOT be selected by default to prevent accidental data loss", largeItems[0].isSelected)
    }

    @Test(timeout = 60000)
    fun testDuplicateFilesDetectionBySizeAndSha256() = runBlocking {
        val root = tempFolder.newFolder("files_root")
        val identicalBytes = ByteArray(150 * 1024) { (it % 250).toByte() }

        // Copy 1 (original)
        val fileOriginal = File(root, "presentation_original.pptx").apply { writeBytes(identicalBytes) }
        // Copy 2 (duplicate)
        val fileDuplicate = File(root, "presentation_copy.pptx").apply { writeBytes(identicalBytes) }

        // Different file with the exact same size
        val differentBytes = ByteArray(150 * 1024) { ((it + 1) % 250).toByte() }
        val fileDifferent = File(root, "other_document.pdf").apply { writeBytes(differentBytes) }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        val items = engine.startScan()
        val duplicateItems = items.filter { it.category == CleanerCategory.DUPLICATES }

        assertEquals(1, duplicateItems.size)
        val dup = duplicateItems[0]
        val (expectedDup, expectedOriginal) = if (dup.name == "presentation_copy.pptx") {
            Pair("presentation_copy.pptx", fileOriginal.absolutePath)
        } else {
            Pair("presentation_original.pptx", fileDuplicate.absolutePath)
        }
        assertEquals(expectedDup, dup.name)
        assertEquals(expectedOriginal, dup.duplicateOriginalPath)
        assertTrue(dup.isSelected)
    }

    @Test(timeout = 60000)
    fun testProtectedFilesNeverCleaned() = runBlocking {
        val root = tempFolder.newFolder("protected_root")
        val vaultDir = File(root, ".safebox").apply { mkdir() }
        val vaultSecret = File(vaultDir, "encrypted.vault").apply { writeBytes(ByteArray(1024)) }

        val cameraDir = File(root, "DCIM/Camera").apply { mkdirs() }
        val userPhoto = File(cameraDir, "IMG_2026.jpg").apply { writeBytes(ByteArray(2048)) }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        assertTrue(engine.isProtectedFile(vaultSecret))
        assertTrue(engine.isProtectedFile(userPhoto))

        // Attempting to clean protected files must fail safely
        val fakeCleanable = CleanableItem(
            file = vaultSecret,
            name = vaultSecret.name,
            size = vaultSecret.length(),
            category = CleanerCategory.CACHE_TEMP
        )
        val result = engine.cleanSelected(listOf(fakeCleanable))

        assertEquals(0, result.deletedCount)
        assertEquals(1, result.failedCount)
        assertTrue(vaultSecret.exists())
    }

    @Test(timeout = 60000)
    fun testCleanSelectedExecution() = runBlocking {
        val root = tempFolder.newFolder("clean_target")
        val junkFile1 = File(root, "cache_1.tmp").apply { writeBytes(ByteArray(1024)) }
        val junkFile2 = File(root, "cache_2.tmp").apply { writeBytes(ByteArray(2048)) }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        val items = engine.startScan()
        assertEquals(2, items.size)

        val result = engine.cleanSelected(items)
        assertEquals(2, result.deletedCount)
        assertEquals(3072L, result.freedBytes)
        assertEquals(0, result.failedCount)

        assertFalse(junkFile1.exists())
        assertFalse(junkFile2.exists())
    }
}


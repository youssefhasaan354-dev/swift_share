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
    fun testCleanerNeverDeletesOutsideTargetDirectory() = runBlocking {
        val targetRoot = tempFolder.newFolder("target_dir")
        val outsideRoot = tempFolder.newFolder("outside_dir")

        val targetJunk = File(targetRoot, "target.tmp").apply { writeBytes(ByteArray(512)) }
        val outsideFile = File(outsideRoot, "system_important.tmp").apply { writeBytes(ByteArray(1024)) }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(targetRoot) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        // 1. Scan must never discover files outside target root
        val scanned = engine.startScan()
        assertEquals(1, scanned.size)
        assertEquals(targetJunk, scanned[0].file)
        assertFalse(scanned.any { it.file == outsideFile })

        // 2. Explicit attempt to clean an outside file must be rejected
        val outsideItem = CleanableItem(
            file = outsideFile,
            name = outsideFile.name,
            size = outsideFile.length(),
            category = CleanerCategory.CACHE_TEMP,
            isSelected = true
        )
        val result = engine.cleanSelected(listOf(outsideItem))
        assertEquals(0, result.deletedCount)
        assertEquals(1, result.failedCount)
        assertTrue("File outside target directory must remain untouched", outsideFile.exists())
    }

    @Test(timeout = 60000)
    fun testCleanerNeverTouchesDcimCameraOrSafeBoxVault() = runBlocking {
        val root = tempFolder.newFolder("root_with_protected_folders")

        val dcimCamera = File(root, "DCIM/Camera").apply { mkdirs() }
        val cameraPhoto = File(dcimCamera, "IMG_20261029.jpg").apply { writeBytes(ByteArray(4096)) }

        val dcimVacation = File(root, "DCIM/Vacation").apply { mkdirs() }
        val vacationPhoto = File(dcimVacation, "beach.png").apply { writeBytes(ByteArray(2048)) }

        val dotSafeBox = File(root, ".safebox").apply { mkdirs() }
        val vaultFile = File(dotSafeBox, "my_secrets.vault").apply { writeBytes(ByteArray(8192)) }

        val safeBoxPlain = File(root, "SafeBox").apply { mkdirs() }
        val vaultDoc = File(safeBoxPlain, "confidential.pdf").apply { writeBytes(ByteArray(1024)) }

        val engine = CleanerEngine(
            rootDirsProvider = { listOf(root) },
            installedPackagesProvider = { emptySet() },
            ioDispatcher = Dispatchers.Unconfined
        )

        // None of these should be scanned as junk
        val scanned = engine.startScan()
        assertTrue("No protected DCIM or SafeBox files should be discovered", scanned.isEmpty())

        // Explicit deletion attempt must be strictly rejected
        val protectedItems = listOf(
            CleanableItem(cameraPhoto, cameraPhoto.name, cameraPhoto.length(), CleanerCategory.CACHE_TEMP),
            CleanableItem(vacationPhoto, vacationPhoto.name, vacationPhoto.length(), CleanerCategory.CACHE_TEMP),
            CleanableItem(vaultFile, vaultFile.name, vaultFile.length(), CleanerCategory.CACHE_TEMP),
            CleanableItem(vaultDoc, vaultDoc.name, vaultDoc.length(), CleanerCategory.CACHE_TEMP)
        )
        val result = engine.cleanSelected(protectedItems)
        assertEquals(0, result.deletedCount)
        assertEquals(4, result.failedCount)

        assertTrue(cameraPhoto.exists())
        assertTrue(vacationPhoto.exists())
        assertTrue(vaultFile.exists())
        assertTrue(vaultDoc.exists())
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


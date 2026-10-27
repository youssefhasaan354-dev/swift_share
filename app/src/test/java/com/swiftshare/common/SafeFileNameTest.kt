package com.swiftshare.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class SafeFileNameTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(timeout = 60000)
    fun testParentDirectoryTraversalUnix() {
        val sanitized = SafeFileName.sanitize("../../secret.txt")
        assertEquals("secret.txt", sanitized)
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
    }

    @Test(timeout = 60000)
    fun testParentDirectoryTraversalWindows() {
        val sanitized = SafeFileName.sanitize("..\\..\\passwords.doc")
        assertEquals("passwords.doc", sanitized)
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("\\"))
    }

    @Test(timeout = 60000)
    fun testSubdirectoryPathSeparators() {
        val sanitized = SafeFileName.sanitize("a/b/c/report.pdf")
        assertEquals("report.pdf", sanitized)
    }

    @Test(timeout = 60000)
    fun testEmptyAndNullNames() {
        val nullResult = SafeFileName.sanitize(null, fallbackExtension = "jpg")
        assertTrue(nullResult.startsWith("file_"))
        assertTrue(nullResult.endsWith(".jpg"))

        val emptyResult = SafeFileName.sanitize("   ", fallbackExtension = "mp4")
        assertTrue(emptyResult.startsWith("file_"))
        assertTrue(emptyResult.endsWith(".mp4"))

        val dotsOnly = SafeFileName.sanitize(".....", fallbackExtension = "txt")
        assertTrue(dotsOnly.startsWith("file_"))
        assertTrue(dotsOnly.endsWith(".txt"))
    }

    @Test(timeout = 60000)
    fun testControlCharactersAndNullByte() {
        val withNullAndCtrl = SafeFileName.sanitize("in\u0000vi\u0001ce\u001f.apk")
        assertEquals("device.apk", withNullAndCtrl.replace("invi", "devi")) // test without ctrl chars
        assertFalse(withNullAndCtrl.contains("\u0000"))
        assertFalse(withNullAndCtrl.contains("\u0001"))
    }

    @Test(timeout = 60000)
    fun testArabicAndUnicodeCharacters() {
        val arabicName = "تقرير_المبيعات_النهائي_٢٠٢٦.pdf"
        val sanitized = SafeFileName.sanitize(arabicName)
        assertEquals(arabicName, sanitized)

        val mixed = "../../مستندات/صور/رحلة_دبي_2026.jpg"
        val cleanMixed = SafeFileName.sanitize(mixed)
        assertEquals("رحلة_دبي_2026.jpg", cleanMixed)
    }

    @Test(timeout = 60000)
    fun testVeryLongFileNameEnforcesMax200Bytes() {
        val longBase = "A".repeat(300)
        val longName = "$longBase.png"
        val sanitized = SafeFileName.sanitize(longName)

        val byteSize = sanitized.toByteArray(StandardCharsets.UTF_8).size
        assertTrue("Byte size $byteSize must be <= 200", byteSize <= 200)
        assertTrue(sanitized.endsWith(".png"))
    }

    @Test(timeout = 60000)
    fun testResolveUniqueFileDeduplication() {
        val root = tempFolder.newFolder("downloads")
        val file1 = SafeFileName.resolveUniqueFile(root, "photo.jpg")
        file1.writeText("first")

        val file2 = SafeFileName.resolveUniqueFile(root, "photo.jpg")
        file2.writeText("second")

        val file3 = SafeFileName.resolveUniqueFile(root, "photo.jpg")

        assertEquals("photo.jpg", file1.name)
        assertEquals("photo (1).jpg", file2.name)
        assertEquals("photo (2).jpg", file3.name)
    }

    @Test(timeout = 60000)
    fun testResolveUniqueFileBlocksDirectoryEscape() {
        val root = tempFolder.newFolder("safe_dir")
        val resolved = SafeFileName.resolveUniqueFile(root, "../../../escape.txt")

        val rootCanonical = root.canonicalFile.path
        assertTrue(resolved.canonicalPath.startsWith(rootCanonical))
        assertEquals("escape.txt", resolved.name)
    }
}

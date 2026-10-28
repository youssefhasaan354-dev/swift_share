package com.swiftshare.explorer

import com.swiftshare.explorer.zip.ZipExplorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExplorerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(timeout = 60000)
    fun testBrowseValidZipArchive() {
        val zipFile = File(tempFolder.root, "test_archive.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Entry 1: file
            zos.putNextEntry(ZipEntry("hello.txt"))
            zos.write("Hello SwiftShare World".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Entry 2: directory
            zos.putNextEntry(ZipEntry("docs/"))
            zos.closeEntry()

            // Entry 3: file in directory
            zos.putNextEntry(ZipEntry("docs/manual.md"))
            zos.write("# Documentation".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val content = ZipExplorer.browse(zipFile)
        assertEquals(3, content.totalEntries)
        assertTrue(content.totalUncompressedBytes > 0)

        val helloEntry = content.entries.find { it.name == "hello.txt" }
        assertNotNull(helloEntry)
        assertFalse(helloEntry!!.isDirectory)
        assertEquals(22L, helloEntry.uncompressedSize)

        val docsDir = content.entries.find { it.name == "docs/" }
        assertNotNull(docsDir)
        assertTrue(docsDir!!.isDirectory)
    }

    @Test(timeout = 60000)
    fun testPreviewTextAndBinary() {
        val zipFile = File(tempFolder.root, "preview_test.zip")
        val sampleText = "{\"app\":\"SwiftShare\",\"version\":\"2.0\"}"
        val sampleBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) // PNG magic

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("config.json"))
            zos.write(sampleText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("logo.png"))
            zos.write(sampleBytes)
            zos.closeEntry()
        }

        val textPreview = ZipExplorer.previewText(zipFile, "config.json")
        assertEquals(sampleText, textPreview)

        val bytesPreview = ZipExplorer.previewBytes(zipFile, "logo.png")
        assertNotNull(bytesPreview)
        assertEquals(8, bytesPreview?.size)
        assertEquals(0x89.toByte(), bytesPreview?.get(0))
    }

    @Test(timeout = 60000)
    fun testExtractAllCleanZip() {
        val zipFile = File(tempFolder.root, "clean_extract.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("notes.txt"))
            zos.write("Clean note content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("assets/icon.svg"))
            zos.write("<svg></svg>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val targetDir = File(tempFolder.root, "extracted_clean")
        val result = ZipExplorer.extract(zipFile, targetDir)

        assertEquals(2, result.extractedFiles.size)
        assertEquals(0, result.skippedSuspiciousFiles.size)

        val noteFile = File(targetDir, "notes.txt")
        assertTrue(noteFile.exists())
        assertEquals("Clean note content", noteFile.readText())

        val iconFile = File(targetDir, "assets/icon.svg")
        assertTrue(iconFile.exists())
        assertEquals("<svg></svg>", iconFile.readText())
    }

    @Test(timeout = 60000)
    fun testZipSlipVulnerabilityMitigationWithMaliciousArchive() {
        // Create an isolated sub-workspace directory structure
        val sandboxBase = tempFolder.newFolder("sandbox_base")
        val targetExtractDir = File(sandboxBase, "destination").apply { mkdirs() }
        val maliciousZip = File(tempFolder.root, "malicious_zipslip.zip")

        // Construct malicious zip entries attempting directory traversal outside destination
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            // Malicious entry 1: ../../escaped_root.txt
            zos.putNextEntry(ZipEntry("../../escaped_root.txt"))
            zos.write("MALICIOUS PAYLOAD ROOT".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Malicious entry 2: subdir/../../../escaped_parent.sh
            zos.putNextEntry(ZipEntry("subdir/../../../escaped_parent.sh"))
            zos.write("#!/bin/sh\necho malicious".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Malicious entry 3: ..\..\windows_traversal.bat
            zos.putNextEntry(ZipEntry("..\\..\\windows_traversal.bat"))
            zos.write("@echo off".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Legitimate entry: safe_file.txt
            zos.putNextEntry(ZipEntry("safe_file.txt"))
            zos.write("Safe content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Legitimate nested entry: docs/manual.txt
            zos.putNextEntry(ZipEntry("docs/manual.txt"))
            zos.write("Legit manual".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Execute extraction
        val result = ZipExplorer.extract(maliciousZip, targetExtractDir)

        // 1. Verify that all 3 malicious zip-slip entries were detected and blocked
        assertEquals(3, result.skippedSuspiciousFiles.size)
        assertTrue(result.skippedSuspiciousFiles.contains("../../escaped_root.txt"))
        assertTrue(result.skippedSuspiciousFiles.contains("subdir/../../../escaped_parent.sh"))
        assertTrue(result.skippedSuspiciousFiles.contains("..\\..\\windows_traversal.bat"))

        // 2. CRITICAL SECURITY ASSERTIONS: Files must NEVER exist outside targetExtractDir
        val outsideFile1 = File(sandboxBase, "escaped_root.txt")
        val outsideFile2 = File(sandboxBase, "escaped_parent.sh")
        val outsideFile3 = File(sandboxBase, "windows_traversal.bat")
        val rootDirFile1 = File(tempFolder.root, "escaped_root.txt")

        assertFalse("Zip-slip target must not exist in parent directory!", outsideFile1.exists())
        assertFalse("Zip-slip target must not exist in parent directory!", outsideFile2.exists())
        assertFalse("Zip-slip target must not exist in parent directory!", outsideFile3.exists())
        assertFalse("Zip-slip target must not exist in temp root!", rootDirFile1.exists())

        // 3. Verify legitimate files were safely extracted inside targetExtractDir
        assertEquals(2, result.extractedFiles.size)
        val safeFile = File(targetExtractDir, "safe_file.txt")
        assertTrue(safeFile.exists())
        assertEquals("Safe content", safeFile.readText())

        val manualFile = File(targetExtractDir, "docs/manual.txt")
        assertTrue(manualFile.exists())
        assertEquals("Legit manual", manualFile.readText())
    }
}


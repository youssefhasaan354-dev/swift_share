package com.swiftshare.webshare

import com.swiftshare.nft.channel.models.FileManifestItem
import io.ktor.client.call.body
import io.ktor.client.request.cookie
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class WebShareZipTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(timeout = 60000)
    fun testDownloadAllZipWithoutCookieReturns401() = testApplication {
        val server = EmbeddedWebShareServer()
        application {
            server.configureModule(this)
        }

        val res = client.get("/api/download/all")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test(timeout = 60000)
    fun testDownloadAllZipWithValidCookieStreamsValidZipWithCorrectEntries() = testApplication {
        val storageDir = tempFolder.newFolder("zip_source_dir")
        val file1 = File(storageDir, "document.txt").apply { writeText("Hello SwiftShare PC Dashboard") }
        val file2 = File(storageDir, "config.json").apply { writeText("{\"pc\":true,\"version\":2}") }
        val file3 = File(storageDir, "image.bin").apply { writeBytes(ByteArray(8192) { (it % 128).toByte() }) }

        val server = EmbeddedWebShareServer()
        val queuedList: List<Pair<FileManifestItem, File>> = listOf(
            Pair(FileManifestItem(fileId = "f1", name = "document.txt", size = file1.length()), file1),
            Pair(FileManifestItem(fileId = "f2", name = "config.json", size = file2.length()), file2),
            Pair(FileManifestItem(fileId = "f3", name = "image.bin", size = file3.length()), file3)
        )
        server.setQueuedFiles(queuedList)

        application {
            server.configureModule(this)
        }

        val sessionToken = server.authManager.createAuthorizedSession()

        val response = client.get("/api/download/all") {
            cookie(WebAuthManager.COOKIE_NAME, sessionToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val contentType = response.headers[HttpHeaders.ContentType]
        assertNotNull(contentType)
        assertTrue(contentType!!.contains("zip") || contentType.contains("application"))

        // Read and verify the ZIP stream content
        val zipBytes: ByteArray = response.readBytes()
        assertTrue("ZIP stream must not be empty", zipBytes.isNotEmpty())

        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val bytes = zis.readBytes()
                zipEntries[entry.name] = bytes
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertEquals(3, zipEntries.size)
        assertTrue(zipEntries.containsKey("document.txt"))
        assertTrue(zipEntries.containsKey("config.json"))
        assertTrue(zipEntries.containsKey("image.bin"))

        assertEquals("Hello SwiftShare PC Dashboard", String(zipEntries["document.txt"]!!))
        assertEquals("{\"pc\":true,\"version\":2}", String(zipEntries["config.json"]!!))
        assertEquals(8192, zipEntries["image.bin"]!!.size)
    }

    @Test(timeout = 60000)
    fun testUploadWithoutCookieReturns401() = testApplication {
        val uploadDir = tempFolder.newFolder("upload_dest")
        val server = EmbeddedWebShareServer(customUploadDir = uploadDir)
        application {
            server.configureModule(this, uploadDirOverride = uploadDir)
        }

        val res = client.post("/api/upload")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test(timeout = 60000)
    fun testUploadWithValidCookieStoresFileAndReturns200() = testApplication {
        val uploadDir = tempFolder.newFolder("upload_dest")
        val server = EmbeddedWebShareServer(customUploadDir = uploadDir)
        application {
            server.configureModule(this, uploadDirOverride = uploadDir)
        }

        val sessionToken = server.authManager.createAuthorizedSession()
        val fileData = "File uploaded from PC to SwiftShare".toByteArray()

        val response = client.submitFormWithBinaryData(
            url = "/api/upload",
            formData = formData {
                append("file", fileData, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"pc_report.docx\"")
                })
            }
        ) {
            cookie(WebAuthManager.COOKIE_NAME, sessionToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val savedFile = File(uploadDir, "pc_report.docx")
        assertTrue("Uploaded file must exist in upload directory", savedFile.exists())
        assertEquals("File uploaded from PC to SwiftShare", savedFile.readText())
    }

    @Test(timeout = 60000)
    fun testUploadSanitizesPathTraversalMaliciousFilename() = testApplication {
        val uploadDir = tempFolder.newFolder("upload_dest")
        val server = EmbeddedWebShareServer(customUploadDir = uploadDir)
        application {
            server.configureModule(this, uploadDirOverride = uploadDir)
        }

        val sessionToken = server.authManager.createAuthorizedSession()
        val maliciousPayload = "@echo off\r\necho hack".toByteArray()

        val response = client.submitFormWithBinaryData(
            url = "/api/upload",
            formData = formData {
                append("file", maliciousPayload, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"../../windows_startup.bat\"")
                })
            }
        ) {
            cookie(WebAuthManager.COOKIE_NAME, sessionToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Traversal path must NOT exist in ancestor directory
        val evilFile = File(uploadDir.parentFile, "windows_startup.bat")
        assertFalse("Path traversal file must never escape to parent folder", evilFile.exists())

        // File must land safely inside uploadDir
        val safeFile = File(uploadDir, "windows_startup.bat")
        assertTrue("File should land sanitized inside upload directory", safeFile.exists())
    }
}

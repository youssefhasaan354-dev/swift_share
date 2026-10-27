package com.swiftshare.webshare

import com.swiftshare.nft.channel.models.FileManifestItem
import io.ktor.client.request.cookie
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WebAuthTest {

    @Test(timeout = 60000)
    fun testPinVerificationAndSessionValidation() {
        val authManager = WebAuthManager()
        val pin = authManager.currentPin.value

        assertTrue(authManager.verifyPin(pin))
        assertFalse(authManager.verifyPin("wrong-pin"))

        val sessionId = authManager.createAuthorizedSession()
        assertTrue(authManager.isValidSession(sessionId))
        assertFalse(authManager.isValidSession("random-non-existent-session"))
        assertFalse(authManager.isValidSession(null))
    }

    @Test(timeout = 60000)
    fun testPinRateLimitingAndLockout() {
        val authManager = WebAuthManager()
        val testIp = "192.168.1.55"

        assertFalse(authManager.isIpLockedOut(testIp))

        // First 4 failed attempts should NOT lock out
        for (i in 1..4) {
            val locked = authManager.recordFailedAttempt(testIp)
            assertFalse("Attempt $i should not lock out yet", locked)
            assertFalse(authManager.isIpLockedOut(testIp))
        }

        // 5th failed attempt MUST lock out
        val lockedOn5th = authManager.recordFailedAttempt(testIp)
        assertTrue("5th attempt must trigger lockout", lockedOn5th)
        assertTrue(authManager.isIpLockedOut(testIp))

        // Reset rate limit resets status
        authManager.resetRateLimit(testIp)
        assertFalse(authManager.isIpLockedOut(testIp))
    }

    @Test(timeout = 60000)
    fun testRealModuleEnforcesAuthenticationAndSanitizesUpload() = testApplication {
        val tempDir = Files.createTempDirectory("swiftshare_test_upload").toFile()
        val dummyFile = File(tempDir, "sample.txt").apply { writeText("Sample Content") }
        val server = EmbeddedWebShareServer(customUploadDir = tempDir)
        server.setQueuedFiles(listOf(
            FileManifestItem(fileId = "test_file_x", name = "sample.txt", size = dummyFile.length()) to dummyFile
        ))

        application {
            server.configureModule(this, uploadDirOverride = tempDir)
        }

        // 1. Assert 401 Unauthorized without cookie on protected routes:
        // /api/manifest
        val resManifestNoCookie = client.get("/api/manifest")
        assertEquals(HttpStatusCode.Unauthorized, resManifestNoCookie.status)

        // /api/download/test_file_x
        val resDownloadNoCookie = client.get("/api/download/test_file_x")
        assertEquals(HttpStatusCode.Unauthorized, resDownloadNoCookie.status)

        // /api/upload
        val resUploadNoCookie = client.post("/api/upload")
        assertEquals(HttpStatusCode.Unauthorized, resUploadNoCookie.status)

        // /api/remote/files
        val resRemoteFilesNoCookie = client.get("/api/remote/files")
        assertEquals(HttpStatusCode.Unauthorized, resRemoteFilesNoCookie.status)

        // /api/ppt/control
        val resPptControlNoCookie = client.post("/api/ppt/control") {
            setBody("{\"action\":\"next\"}")
        }
        assertEquals(HttpStatusCode.Unauthorized, resPptControlNoCookie.status)

        // 2. Authorize via valid session cookie
        val validSession = server.authManager.createAuthorizedSession()

        // /api/manifest with valid cookie -> 200 OK
        val resManifestAuth = client.get("/api/manifest") {
            cookie(WebAuthManager.COOKIE_NAME, validSession)
        }
        assertEquals(HttpStatusCode.OK, resManifestAuth.status)

        // /api/download/test_file_x with valid cookie -> 200 OK
        val resDownloadAuth = client.get("/api/download/test_file_x") {
            cookie(WebAuthManager.COOKIE_NAME, validSession)
        }
        assertEquals(HttpStatusCode.OK, resDownloadAuth.status)

        // /api/remote/files with valid cookie -> 200 OK
        val resRemoteFilesAuth = client.get("/api/remote/files") {
            cookie(WebAuthManager.COOKIE_NAME, validSession)
        }
        assertEquals(HttpStatusCode.OK, resRemoteFilesAuth.status)

        // /api/ppt/control with valid cookie -> 200 OK
        val resPptControlAuth = client.post("/api/ppt/control") {
            cookie(WebAuthManager.COOKIE_NAME, validSession)
            setBody("{\"action\":\"next\"}")
        }
        assertEquals(HttpStatusCode.OK, resPptControlAuth.status)

        // 3. Upload with filename "../evil.txt" lands sanitized inside the upload dir
        val uploadContent = "Secret payload".toByteArray()
        val resUploadAuth = client.submitFormWithBinaryData(
            url = "/api/upload",
            formData = formData {
                append("file", uploadContent, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"../evil.txt\"")
                })
            }
        ) {
            cookie(WebAuthManager.COOKIE_NAME, validSession)
        }
        assertEquals(HttpStatusCode.OK, resUploadAuth.status)

        // Verify file landed safely sanitized inside upload dir as evil.txt, NOT in parent dir
        val evilFileInDir = File(tempDir, "evil.txt")
        assertTrue("Uploaded file must exist sanitized inside uploadDir", evilFileInDir.exists())
        assertEquals("Secret payload", evilFileInDir.readText())

        val evilParentFile = File(tempDir.parentFile, "evil.txt")
        assertFalse("Uploaded file must NEVER escape into parent directory", evilParentFile.exists())

        tempDir.deleteRecursively()
    }

    @Test(timeout = 60000)
    fun testRealModulePinRateLimitingEndpointReturns429OnLockout() = testApplication {
        val server = EmbeddedWebShareServer()
        application {
            server.configureModule(this)
        }

        // 4 failed attempts -> 401
        for (i in 1..4) {
            val res = client.post("/api/auth/pin") {
                contentType(ContentType.Application.Json)
                setBody("{\"pin\":\"000000\"}")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }

        // 5th failed attempt -> 429 Too Many Requests
        val lockedRes = client.post("/api/auth/pin") {
            contentType(ContentType.Application.Json)
            setBody("{\"pin\":\"000000\"}")
        }
        assertEquals(HttpStatusCode.TooManyRequests, lockedRes.status)

        // Subsequent attempt while locked out -> 429
        val subsequentRes = client.post("/api/auth/pin") {
            contentType(ContentType.Application.Json)
            setBody("{\"pin\":\"000000\"}")
        }
        assertEquals(HttpStatusCode.TooManyRequests, subsequentRes.status)
    }
}

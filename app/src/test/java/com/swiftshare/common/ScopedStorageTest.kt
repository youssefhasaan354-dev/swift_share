package com.swiftshare.common

import com.swiftshare.nft.discovery.QrPayloadManager
import com.swiftshare.nft.discovery.models.ConnectionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class ScopedStorageTest {

    @Test(timeout = 60000)
    fun testQrTokenExpiryWithinTtl() {
        val payload = ConnectionPayload(
            ssid = "SwiftShare_Test",
            preSharedKey = "secret123",
            sessionToken = "TOKEN_VALID",
            deviceName = "HostDevice",
            timestamp = System.currentTimeMillis() - 60_000L, // 1 minute old
            ttlMillis = 600_000L // 10 minutes
        )
        assertFalse("1-minute old QR token must not be expired", payload.isExpired)

        val json = payload.toJson()
        val parsed = QrPayloadManager.parseQrString(json)
        assertNotNull("Active QR token must parse successfully", parsed)
        assertEquals("TOKEN_VALID", parsed!!.sessionToken)
    }

    @Test(timeout = 60000)
    fun testQrTokenExpiryAfterTtl() {
        val expiredPayload = ConnectionPayload(
            ssid = "SwiftShare_Old",
            preSharedKey = "secret123",
            sessionToken = "TOKEN_EXPIRED",
            deviceName = "HostDevice",
            timestamp = System.currentTimeMillis() - 650_000L, // 10.8 minutes old
            ttlMillis = 600_000L // 10 minutes
        )
        assertTrue("Token older than 10 minutes must be expired", expiredPayload.isExpired)

        val json = expiredPayload.toJson()
        val parsed = QrPayloadManager.parseQrString(json)
        assertNull("Expired QR token must be rejected by parser", parsed)
    }

    @Test(timeout = 60000)
    fun testDirectFilePendingTargetWriteAndPublish() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "scoped_storage_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val destFile = File(tempDir, "document.pdf")
        val raf = java.io.RandomAccessFile(destFile, "rw")

        val target = ScopedStorageManager.PendingTarget(
            uri = null,
            file = destFile,
            pfd = null,
            channel = raf.channel,
            isMediaStore = false
        )

        try {
            // Write chunk 0
            val chunk0 = "First chunk bytes".toByteArray(Charsets.UTF_8)
            target.channel!!.write(ByteBuffer.wrap(chunk0), 0L)

            // Write chunk 1 at offset
            val chunk1 = "Second chunk bytes".toByteArray(Charsets.UTF_8)
            target.channel!!.write(ByteBuffer.wrap(chunk1), chunk0.size.toLong())

            target.channel!!.force(true)
            target.channel!!.close()

            assertTrue(destFile.exists())
            assertEquals((chunk0.size + chunk1.size).toLong(), destFile.length())
            val content = destFile.readText(Charsets.UTF_8)
            assertEquals("First chunk bytesSecond chunk bytes", content)
        } finally {
            destFile.delete()
            tempDir.delete()
        }
    }
}

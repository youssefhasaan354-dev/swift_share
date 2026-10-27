package com.swiftshare.nft.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DataChannelTokenTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(timeout = 60000)
    fun testDataChannelHeaderSerializationAndParsing() {
        val original = DataChannelHeader(
            token = "AUTH_TOKEN_TEST_999",
            fileId = "12345678-abcd-ef01-2345-6789abcdef01",
            offset = 1024L,
            totalSize = 4096L,
            fileName = "sample_video.mp4",
            thumbnailBytes = byteArrayOf(1, 2, 3, 4, 5)
        )

        val buffer = original.serialize()
        val parsed = DataChannelHeader.parse(buffer)

        assertNotNull("Parsed header must not be null", parsed)
        assertEquals(original.token, parsed!!.token)
        assertEquals(original.fileId, parsed.fileId)
        assertEquals(original.offset, parsed.offset)
        assertEquals(original.totalSize, parsed.totalSize)
        assertEquals(original.fileName, parsed.fileName)
        assertTrue(original.thumbnailBytes.contentEquals(parsed.thumbnailBytes))
    }

    @Test(timeout = 60000)
    fun testDataChannelHeaderInvalidMagicReturnsNull() {
        val original = DataChannelHeader(
            token = "TOKEN",
            fileId = "file1",
            offset = 0L,
            totalSize = 100L,
            fileName = "test.txt"
        )
        val buffer = original.serialize()
        // Corrupt first byte of magic
        buffer.put(0, 'X'.code.toByte())

        val parsed = DataChannelHeader.parse(buffer)
        assertNull("Corrupt magic must return null", parsed)
    }

    @Test(timeout = 60000)
    fun testTokenValidationConstantTime() {
        val expected = "SECRET_123_XYZ"
        assertTrue(DataChannelHeader.validateToken(expected, "SECRET_123_XYZ"))
        assertFalse(DataChannelHeader.validateToken(expected, "WRONG_SECRET"))
        assertFalse(DataChannelHeader.validateToken(expected, ""))
        assertTrue(DataChannelHeader.validateToken(null, "ANY_TOKEN"))
        assertTrue(DataChannelHeader.validateToken("", "ANY_TOKEN"))
    }

    @Test(timeout = 60000)
    fun testLoopbackRejectsWrongTokenAndCreatesNoFile() {
        val rxDir = tempFolder.newFolder("rx_reject")
        val channel = HighSpeedDataChannel()
        val expectedToken = "SECURE_SESSION_TOKEN_ALPHA"

        val boundLatch = CountDownLatch(1)
        channel.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = expectedToken,
            onBound = { boundLatch.countDown() }
        )

        assertTrue("Server must bind to ephemeral port within 5s", boundLatch.await(5, TimeUnit.SECONDS))
        val port = channel.serverPort
        assertTrue("Port must be > 0", port > 0)

        try {
            val socket = Socket("127.0.0.1", port)
            socket.soTimeout = 5000

            val evilHeader = DataChannelHeader(
                token = "ATTACKER_TOKEN_EVIL",
                fileId = "evil-file-id-36-chars-00000000000",
                offset = 0L,
                totalSize = 100L,
                fileName = "evil.txt"
            )

            val out = socket.getOutputStream()
            out.write(evilHeader.serialize().array())
            out.flush()

            // Server must close the connection due to token mismatch
            val input = socket.getInputStream()
            val read = try {
                input.read()
            } catch (_: Exception) {
                -1
            }
            assertEquals("Connection must be closed by receiver on token mismatch", -1, read)
            socket.close()

            // Verify NO file was created
            val filesInDir = rxDir.listFiles() ?: emptyArray()
            assertEquals("No files must be created when token is rejected", 0, filesInDir.size)
            assertFalse(File(rxDir, "evil.txt").exists())
        } finally {
            channel.cancel()
        }
    }

    @Test(timeout = 60000)
    fun testLoopbackAcceptsRightTokenAndCreatesFile() {
        val rxDir = tempFolder.newFolder("rx_accept")
        val channel = HighSpeedDataChannel()
        val expectedToken = "SECURE_SESSION_TOKEN_BETA"

        val boundLatch = CountDownLatch(1)
        val fileReceivedLatch = CountDownLatch(1)
        var receivedFileResult: File? = null

        channel.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = expectedToken,
            onBound = { boundLatch.countDown() },
            onFileReceived = { file, _ ->
                receivedFileResult = file
                fileReceivedLatch.countDown()
            }
        )

        assertTrue("Server must bind to ephemeral port within 5s", boundLatch.await(5, TimeUnit.SECONDS))
        val port = channel.serverPort
        assertTrue("Port must be > 0", port > 0)

        try {
            val payload = "Hello SwiftShare Secure Data Channel!".toByteArray(Charsets.UTF_8)
            val goodHeader = DataChannelHeader(
                token = expectedToken,
                fileId = "good-file-id-36-chars-00000000000",
                offset = 0L,
                totalSize = payload.size.toLong(),
                fileName = "welcome.txt"
            )

            val socket = Socket("127.0.0.1", port)
            socket.soTimeout = 5000

            val out = socket.getOutputStream()
            out.write(goodHeader.serialize().array())
            out.write(payload)
            out.flush()
            socket.close()

            assertTrue("File must be fully received within 5s", fileReceivedLatch.await(5, TimeUnit.SECONDS))
            assertNotNull(receivedFileResult)
            assertTrue("Received file must exist", receivedFileResult!!.exists())
            assertEquals("welcome.txt", receivedFileResult!!.name)
            assertEquals("Hello SwiftShare Secure Data Channel!", receivedFileResult!!.readText(Charsets.UTF_8))
        } finally {
            channel.cancel()
        }
    }
}

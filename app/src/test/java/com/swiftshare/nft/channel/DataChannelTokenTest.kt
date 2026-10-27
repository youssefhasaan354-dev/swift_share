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

    @Test(timeout = 60000)
    fun testStreamAuthHeaderSerializationAndParsing() {
        val original = StreamAuthHeader(token = "AUTH_STREAM_TOKEN_1", streamIndex = 2.toByte())
        val buffer = original.serialize()
        val parsed = StreamAuthHeader.parse(buffer)

        assertNotNull("StreamAuthHeader must parse correctly", parsed)
        assertEquals(original.token, parsed!!.token)
        assertEquals(original.streamIndex, parsed.streamIndex)
    }

    @Test(timeout = 60000)
    fun testChunkHeaderSerializationAndParsing() {
        val payload = "Testing Chunk CRC32 payload consistency".toByteArray(Charsets.UTF_8)
        val crc = ChunkHeader.computeCrc32(payload)
        val original = ChunkHeader(
            streamId = 3.toByte(),
            fileId = "file-uuid-1234-5678-abcdef012345",
            offset = 65536L,
            length = payload.size,
            crc32 = crc,
            isEof = false
        )

        val buffer = original.serialize()
        val parsed = ChunkHeader.parse(buffer)

        assertNotNull("ChunkHeader must parse correctly", parsed)
        assertEquals(original.streamId, parsed!!.streamId)
        assertEquals(original.fileId, parsed.fileId)
        assertEquals(original.offset, parsed.offset)
        assertEquals(original.length, parsed.length)
        assertEquals(original.crc32, parsed.crc32)
        assertEquals(original.isEof, parsed.isEof)
    }

    @Test(timeout = 60000)
    fun testChunkHeaderDefensiveBoundaryValidation() {
        val validFileId = "target-file-id-36-chars-00000000"
        val activeIds = setOf(validFileId)
        val fileSize = 150_000L // ~2.28 chunks: chunk 0 (64KB), chunk 1 (64KB), chunk 2 (18928 bytes)

        // 1. Valid full first chunk (64KB at offset 0)
        val validChunk0 = ChunkHeader(0, validFileId, 0L, CHUNK_MAX_SIZE, 12345L, false)
        assertTrue(ChunkHeader.validate(validChunk0, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Valid)

        // 2. Valid full second chunk (64KB at offset 64KB)
        val validChunk1 = ChunkHeader(1, validFileId, 65536L, CHUNK_MAX_SIZE, 12345L, false)
        assertTrue(ChunkHeader.validate(validChunk1, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Valid)

        // 3. Valid last chunk (remaining 18928 bytes at offset 131072)
        val remaining = (fileSize - 131072L).toInt()
        val validChunk2 = ChunkHeader(2, validFileId, 131072L, remaining, 12345L, false)
        assertTrue(ChunkHeader.validate(validChunk2, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Valid)

        // 4. Valid EOF chunk
        val validEof = ChunkHeader(0, validFileId, 0L, 0, 0L, true)
        assertTrue(ChunkHeader.validate(validEof, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Valid)

        // 5. Boundary Rejection: Unknown fileId
        val badFileId = ChunkHeader(0, "unknown-file-id", 0L, CHUNK_MAX_SIZE, 12345L, false)
        val resBadId = ChunkHeader.validate(badFileId, fileSize, activeIds)
        assertTrue(resBadId is ChunkHeader.Companion.ValidationResult.Invalid)
        assertTrue((resBadId as ChunkHeader.Companion.ValidationResult.Invalid).reason.contains("Unknown fileId"))

        // 6. Boundary Rejection: Negative offset
        val negOffset = ChunkHeader(0, validFileId, -65536L, CHUNK_MAX_SIZE, 12345L, false)
        assertTrue(ChunkHeader.validate(negOffset, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)

        // 7. Boundary Rejection: Offset not multiple of 64KB
        val unalignedOffset = ChunkHeader(0, validFileId, 1024L, CHUNK_MAX_SIZE, 12345L, false)
        assertTrue(ChunkHeader.validate(unalignedOffset, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)

        // 8. Boundary Rejection: Length out of bounds (> 64KB)
        val tooLong = ChunkHeader(0, validFileId, 0L, CHUNK_MAX_SIZE + 1, 12345L, false)
        assertTrue(ChunkHeader.validate(tooLong, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)

        // 9. Boundary Rejection: Length negative
        val negLength = ChunkHeader(0, validFileId, 0L, -1, 12345L, false)
        assertTrue(ChunkHeader.validate(negLength, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)

        // 10. Boundary Rejection: Offset == fileSize (for non-EOF chunk)
        val offsetEqualsSize = ChunkHeader(0, validFileId, 196608L, 0, 12345L, false)
        assertTrue(ChunkHeader.validate(offsetEqualsSize, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)

        // 11. Boundary Rejection: Offset + length > fileSize
        val overflowSize = ChunkHeader(2, validFileId, 131072L, CHUNK_MAX_SIZE, 12345L, false)
        assertTrue(ChunkHeader.validate(overflowSize, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)

        // 12. Boundary Rejection: Long.MAX_VALUE offset
        val maxOffset = ChunkHeader(0, validFileId, Long.MAX_VALUE, CHUNK_MAX_SIZE, 12345L, false)
        assertTrue(ChunkHeader.validate(maxOffset, fileSize, activeIds) is ChunkHeader.Companion.ValidationResult.Invalid)
    }
}


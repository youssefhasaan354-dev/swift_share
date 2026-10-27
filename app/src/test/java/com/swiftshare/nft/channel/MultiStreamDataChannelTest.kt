package com.swiftshare.nft.channel

import com.swiftshare.nft.channel.models.FileManifestItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MultiStreamDataChannelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rxDir: File
    private lateinit var txDir: File
    private val activeChannels = mutableListOf<HighSpeedDataChannel>()

    @Before
    fun setUp() {
        rxDir = tempFolder.newFolder("rx")
        txDir = tempFolder.newFolder("tx")
    }

    @After
    fun tearDown() {
        activeChannels.forEach { it.cancel() }
        activeChannels.clear()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createSyntheticFile(dir: File, name: String, sizeBytes: Long): File {
        val file = File(dir, name)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(sizeBytes)
            val pattern = "SWIFTSHARE_MULTI_STREAM_TEST_PATTERN_1234567890\n".toByteArray(Charsets.UTF_8)
            var written = 0L
            while (written < sizeBytes) {
                val toWrite = minOf(pattern.size.toLong(), sizeBytes - written).toInt()
                raf.write(pattern, 0, toWrite)
                written += toWrite
            }
        }
        return file
    }

    @Test(timeout = 60000)
    fun test100MbParallelTransferWithSha256VerificationAndThroughput() = runBlocking {
        val receiver = HighSpeedDataChannel()
        val sender = HighSpeedDataChannel()
        activeChannels.add(receiver)
        activeChannels.add(sender)

        val fileSize = 100L * 1024L * 1024L // 100 MB
        val sourceFile = createSyntheticFile(txDir, "large_100mb.bin", fileSize)
        val expectedSha = computeSha256(sourceFile)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(
            fileId = fileId,
            name = "large_100mb.bin",
            size = fileSize,
            sha256 = expectedSha
        )
        receiver.registerManifestItem(manifestItem)
        val token = "SESSION_TOKEN_100MB_TEST"

        val boundLatch = CountDownLatch(1)
        val fileReceivedLatch = CountDownLatch(1)
        var receivedFile: File? = null

        receiver.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() },
            onFileReceived = { file, _ ->
                receivedFile = file
                fileReceivedLatch.countDown()
            }
        )

        assertTrue("Receiver must bind within 5s", boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        // Provide checksum on receiver before or during transfer
        receiver.notifyChecksumReceived(fileId, expectedSha)

        val startTime = System.currentTimeMillis()
        val sendSuccessLatch = CountDownLatch(1)
        val sendErrorRef = AtomicReference<Throwable?>(null)

        sender.sendFileParallel(
            targetIp = "127.0.0.1",
            manifestItem = manifestItem,
            sourceFile = sourceFile,
            sessionToken = token,
            port = port,
            streamCount = 4,
            onSuccess = { sendSuccessLatch.countDown() },
            onError = { err ->
                sendErrorRef.set(err)
                sendSuccessLatch.countDown()
            }
        )

        assertTrue("Sender must finish within 45s", sendSuccessLatch.await(45, TimeUnit.SECONDS))
        if (sendErrorRef.get() != null) {
            throw AssertionError("Sender failed: ${sendErrorRef.get()?.message}", sendErrorRef.get())
        }

        assertTrue("Receiver must finalize within 10s after sender", fileReceivedLatch.await(10, TimeUnit.SECONDS))
        val elapsedMs = System.currentTimeMillis() - startTime
        val speedMbPerSec = (fileSize.toDouble() / (1024.0 * 1024.0)) / (elapsedMs.toDouble() / 1000.0)

        // Log throughput explicitly
        println("=== T2.2 BENCHMARK: 4 Parallel Streams ===")
        println("File Size: ${fileSize / (1024 * 1024)} MB")
        println("Elapsed Time: ${elapsedMs} ms")
        println("Throughput (4 Streams): %.2f MB/s (LOOPBACK ONLY - does not represent Wi-Fi medium)".format(speedMbPerSec))

        // Assert all 4 streams actively transferred data (> 0 bytes on each stream)
        for (i in 0 until 4) {
            val bytes = sender.bytesSentPerStream[i.toByte()]?.get() ?: 0L
            println("Stream $i bytes sent: $bytes")
            assertTrue("Stream $i must have carried data (> 0 bytes)", bytes > 0L)
        }

        assertNotNull("Received file must not be null", receivedFile)
        assertTrue("Received file must exist", receivedFile!!.exists())
        assertEquals("File size must match exactly", fileSize, receivedFile!!.length())

        val actualSha = computeSha256(receivedFile!!)
        assertEquals("Full-file SHA-256 must match exactly", expectedSha, actualSha)

        // Ensure no lingering .part file remains
        val partFile = File(rxDir, "large_100mb.bin.part")
        assertFalse(".part file must be removed after successful commit", partFile.exists())
    }

    @Test(timeout = 60000)
    fun testSingleStreamThroughputBaselineComparison() = runBlocking {
        val receiver = HighSpeedDataChannel()
        val sender = HighSpeedDataChannel()
        activeChannels.add(receiver)
        activeChannels.add(sender)

        val fileSize = 20L * 1024L * 1024L // 20 MB for single-stream baseline comparison
        val sourceFile = createSyntheticFile(txDir, "single_stream.bin", fileSize)
        val expectedSha = computeSha256(sourceFile)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(fileId = fileId, name = "single_stream.bin", size = fileSize)
        receiver.registerManifestItem(manifestItem)
        receiver.notifyChecksumReceived(fileId, expectedSha)

        val token = "SINGLE_STREAM_TOKEN"
        val boundLatch = CountDownLatch(1)
        val receivedLatch = CountDownLatch(1)

        receiver.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() },
            onFileReceived = { _, _ -> receivedLatch.countDown() }
        )

        assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        val startTime = System.currentTimeMillis()
        val successLatch = CountDownLatch(1)

        sender.sendFileParallel(
            targetIp = "127.0.0.1",
            manifestItem = manifestItem,
            sourceFile = sourceFile,
            sessionToken = token,
            port = port,
            streamCount = 1,
            onSuccess = { successLatch.countDown() },
            onError = { successLatch.countDown() }
        )

        assertTrue(successLatch.await(30, TimeUnit.SECONDS))
        assertTrue(receivedLatch.await(10, TimeUnit.SECONDS))

        val elapsedMs = System.currentTimeMillis() - startTime
        val speedMbPerSec = (fileSize.toDouble() / (1024.0 * 1024.0)) / (elapsedMs.toDouble() / 1000.0)
        println("=== T2.2 BENCHMARK: 1 Stream Baseline ===")
        println("Throughput (1 Stream): %.2f MB/s (LOOPBACK ONLY)".format(speedMbPerSec))
    }

    @Test(timeout = 60000)
    fun testNegativeCorruptedChunkCrcIsDetectedAndAborts() = runBlocking {
        val receiver = HighSpeedDataChannel()
        activeChannels.add(receiver)

        val fileId = UUID.randomUUID().toString()
        val fileSize = 65536L
        val manifestItem = FileManifestItem(fileId = fileId, name = "corrupt.bin", size = fileSize)
        receiver.registerManifestItem(manifestItem)
        val token = "CORRUPT_TEST_TOKEN"

        val boundLatch = CountDownLatch(1)
        val receiverErrorRef = AtomicReference<Throwable?>(null)
        val receiverErrorLatch = CountDownLatch(1)

        receiver.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() },
            onError = { err ->
                receiverErrorRef.set(err)
                receiverErrorLatch.countDown()
            }
        )

        assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        val sc = SocketChannel.open().apply {
            connect(InetSocketAddress("127.0.0.1", port))
        }

        // Authenticate stream
        val auth = StreamAuthHeader(token = token, streamIndex = 0)
        sc.write(auth.serialize())

        // Send chunk with deliberate CRC32 corruption
        val payload = ByteArray(CHUNK_MAX_SIZE) { 0x42 }
        val badCrc = 999999999L // corrupt CRC
        val header = ChunkHeader(
            streamId = 0,
            fileId = fileId,
            offset = 0L,
            length = CHUNK_MAX_SIZE,
            crc32 = badCrc,
            isEof = false
        )
        sc.write(header.serialize())
        sc.write(ByteBuffer.wrap(payload))

        assertTrue("Receiver must encounter error upon CRC mismatch within 5s", receiverErrorLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(receiverErrorRef.get())
        assertTrue("Error must mention CRC32 mismatch", receiverErrorRef.get()!!.message!!.contains("CRC32 mismatch"))

        sc.close()
    }

    @Test(timeout = 60000)
    fun testNegativeStreamWithInvalidTokenRejectedAndClosed() = runBlocking {
        val receiver = HighSpeedDataChannel()
        activeChannels.add(receiver)
        val expectedToken = "RIGHT_TOKEN"

        val boundLatch = CountDownLatch(1)
        receiver.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = expectedToken,
            onBound = { boundLatch.countDown() }
        )

        assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        val sc = SocketChannel.open().apply {
            connect(InetSocketAddress("127.0.0.1", port))
        }

        // Send wrong token
        val auth = StreamAuthHeader(token = "WRONG_TOKEN", streamIndex = 0)
        sc.write(auth.serialize())

        // Read from socket; server must immediately close socket (-1)
        val buf = ByteBuffer.allocate(64)
        val read = sc.read(buf)
        assertEquals("Server must immediately close connection on invalid token", -1, read)
        sc.close()
    }

    @Test(timeout = 60000)
    fun testNegativeMidTransferCancellationHasNoLeaksAndPurgesPartFile() = runBlocking {
        val receiver = HighSpeedDataChannel()
        val sender = HighSpeedDataChannel()
        activeChannels.add(receiver)
        activeChannels.add(sender)

        val fileSize = 50L * 1024L * 1024L // 50 MB
        val sourceFile = createSyntheticFile(txDir, "cancel_test.bin", fileSize)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(fileId = fileId, name = "cancel_test.bin", size = fileSize)
        receiver.registerManifestItem(manifestItem)
        val token = "CANCEL_TEST_TOKEN"

        val boundLatch = CountDownLatch(1)
        receiver.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() }
        )

        assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
        val serverPort = receiver.serverPort

        val baselineThreadCount = Thread.activeCount()

        val progressReported = CountDownLatch(1)
        val senderJob = CoroutineScope(Dispatchers.IO).launch {
            sender.sendFileParallel(
                targetIp = "127.0.0.1",
                manifestItem = manifestItem,
                sourceFile = sourceFile,
                sessionToken = token,
                port = serverPort,
                streamCount = 4,
                onProgress = { transferred, _ ->
                    if (transferred > 1024 * 1024) {
                        progressReported.countDown()
                    }
                },
                onSuccess = {},
                onError = {}
            )
        }

        assertTrue("Transfer must make progress before cancel", progressReported.await(10, TimeUnit.SECONDS))

        // Trigger clean cancel
        receiver.cancel()
        sender.cancel()
        senderJob.cancel()

        // 1. Assert all registered channels are closed
        synchronized(receiver.channelRegistry) {
            receiver.channelRegistry.forEach { channel ->
                assertFalse("Receiver channel must be closed after cancel", channel.isOpen)
            }
        }
        synchronized(sender.channelRegistry) {
            sender.channelRegistry.forEach { channel ->
                assertFalse("Sender channel must be closed after cancel", channel.isOpen)
            }
        }

        // 2. Assert server port is immediately re-bindable without BindException
        var testServerSocket: ServerSocket? = null
        try {
            testServerSocket = ServerSocket()
            testServerSocket.reuseAddress = true
            testServerSocket.bind(InetSocketAddress("127.0.0.1", serverPort))
            assertTrue("Server port $serverPort must be immediately re-bindable", testServerSocket.isBound)
        } finally {
            testServerSocket?.close()
        }

        // 3. Assert active .part file is purged
        val partFile = File(rxDir, "cancel_test.bin.part")
        assertFalse("Incomplete .part file must be deleted upon cancel", partFile.exists())

        // 4. Assert active threads return to baseline within 5s polling
        val threadSettled = AtomicBoolean(false)
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val currentThreads = Thread.activeCount()
            if (currentThreads <= baselineThreadCount + 4) { // small margin for JVM daemon/GC threads
                threadSettled.set(true)
                break
            }
            Thread.sleep(100)
        }
        assertTrue("Thread count must return to baseline after cancel", threadSettled.get())
    }

    @Test(timeout = 60000)
    fun testWatchdogAbortsStalledPeerWithinTimeout() = runBlocking {
        val receiver = HighSpeedDataChannel()
        receiver.watchdogTimeoutMs = 1500L // 1.5 second watchdog for test
        activeChannels.add(receiver)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(fileId = fileId, name = "stall.bin", size = 65536L)
        receiver.registerManifestItem(manifestItem)
        val token = "STALL_TEST_TOKEN"

        val boundLatch = CountDownLatch(1)
        receiver.startReceiverServer(
            downloadDir = rxDir,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() }
        )

        assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        val sc = SocketChannel.open().apply {
            connect(InetSocketAddress("127.0.0.1", port))
        }

        // Authenticate stream
        val auth = StreamAuthHeader(token = token, streamIndex = 0)
        sc.write(auth.serialize())

        // Now STALL: client does not send any chunks.
        val startStall = System.currentTimeMillis()
        val buf = ByteBuffer.allocate(16)
        val read = sc.read(buf) // Server watchdog should close socket, returning -1
        val elapsed = System.currentTimeMillis() - startStall

        assertEquals("Server watchdog must close stalled connection", -1, read)
        assertTrue("Watchdog should abort within ~3500ms (timeout was 1500ms)", elapsed < 3500)
        sc.close()
    }
}

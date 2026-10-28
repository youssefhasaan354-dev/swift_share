package com.swiftshare.nft.channel

import com.swiftshare.common.PublishedFile
import com.swiftshare.common.ReceivedFilePublisher
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.PartFileMeta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DataChannelPublisherTest {

    private lateinit var tempDir: File
    private lateinit var partsDir: File
    private lateinit var destDir: File
    private lateinit var txDir: File
    private val activeChannels = mutableListOf<HighSpeedDataChannel>()

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "test_publisher_" + UUID.randomUUID())
        partsDir = File(tempDir, "parts").apply { mkdirs() }
        destDir = File(tempDir, "dest").apply { mkdirs() }
        txDir = File(tempDir, "tx").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        activeChannels.forEach { it.cancel() }
        activeChannels.clear()
        tempDir.deleteRecursively()
    }

    class FakePublisher(
        private val destinationDir: File,
        var shouldThrow: Boolean = false
    ) : ReceivedFilePublisher {
        val publishCallCount = AtomicInteger(0)
        val publishedNames = mutableListOf<String>()
        val publishedCategories = mutableListOf<String>()

        override suspend fun publish(
            partFile: File,
            finalName: String,
            mimeType: String,
            category: String
        ): PublishedFile {
            publishCallCount.incrementAndGet()
            publishedNames.add(finalName)
            publishedCategories.add(category)

            if (shouldThrow) {
                throw IOException("Simulated storage full or MediaStore permission failure")
            }

            val targetFile = File(destinationDir, finalName)
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()

            return PublishedFile("file://${targetFile.absolutePath}", targetFile)
        }
    }

    private fun createSyntheticFile(parent: File, name: String, size: Long): File {
        val file = File(parent, name)
        FileOutputStream(file).use { fos ->
            val buf = ByteArray(64 * 1024)
            var written = 0L
            var b = 0.toByte()
            while (written < size) {
                val toWrite = (size - written).coerceAtMost(buf.size.toLong()).toInt()
                for (i in 0 until toWrite) {
                    buf[i] = b++
                }
                fos.write(buf, 0, toWrite)
                written += toWrite
            }
        }
        return file
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test(timeout = 30000)
    fun testReceiveWithInjectedFakePublisher_publishesOnceAndDeletesPart() = runBlocking {
        val receiver = HighSpeedDataChannel()
        val sender = HighSpeedDataChannel()
        activeChannels.add(receiver)
        activeChannels.add(sender)

        val fileSize = 256 * 1024L // 256 KB
        val sourceFile = createSyntheticFile(txDir, "document.pdf", fileSize)
        val expectedSha = computeSha256(sourceFile)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(
            fileId = fileId,
            name = "document.pdf",
            size = fileSize,
            sha256 = expectedSha,
            mimeType = "application/pdf"
        )
        receiver.registerManifestItem(manifestItem)
        val token = "SESSION_TOKEN_PUB_TEST"

        val fakePublisher = FakePublisher(destDir)
        val boundLatch = CountDownLatch(1)
        val publishedLatch = CountDownLatch(1)
        var receivedPublishedFile: PublishedFile? = null

        receiver.startReceiverServer(
            partsDir = partsDir,
            publisher = fakePublisher,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() },
            onPublished = { pub, _ ->
                receivedPublishedFile = pub
                publishedLatch.countDown()
            }
        )

        assertTrue("Receiver must bind within 5s", boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        receiver.notifyChecksumReceived(fileId, expectedSha)

        val sendSuccessLatch = CountDownLatch(1)
        val sendErrorRef = AtomicReference<Throwable?>(null)

        sender.sendFileParallel(
            targetIp = "127.0.0.1",
            manifestItem = manifestItem,
            sourceFile = sourceFile,
            sessionToken = token,
            port = port,
            streamCount = 2,
            onSuccess = { sendSuccessLatch.countDown() },
            onError = { err ->
                sendErrorRef.set(err)
                sendSuccessLatch.countDown()
            }
        )

        assertTrue("Sender must complete", sendSuccessLatch.await(15, TimeUnit.SECONDS))
        if (sendErrorRef.get() != null) {
            throw AssertionError("Sender failed: ${sendErrorRef.get()?.message}", sendErrorRef.get())
        }

        assertTrue("Receiver onPublished must be invoked", publishedLatch.await(10, TimeUnit.SECONDS))

        // Assert 1: FakePublisher was called exactly once with the right name
        assertEquals("publish() must be called exactly once", 1, fakePublisher.publishCallCount.get())
        assertEquals("Published name must match manifest name", "document.pdf", fakePublisher.publishedNames[0])

        // Assert 2: Published file exists at destination
        val destFile = File(destDir, "document.pdf")
        assertTrue("Final destination file must exist", destFile.exists())
        assertEquals(fileSize, destFile.length())
        assertEquals("Published URI must match", "file://${destFile.absolutePath}", receivedPublishedFile?.uriString)

        // Assert 3: The .part and .part.meta files are deleted from injected partsDir
        val partFile = File(partsDir, "document.pdf.part")
        val metaFile = File(partsDir, "document.pdf.part.meta")
        assertFalse("Temporary .part file MUST be deleted after publish", partFile.exists())
        assertFalse("Temporary .part.meta file MUST be deleted after publish", metaFile.exists())
    }

    @Test(timeout = 30000)
    fun testResumeStillWorksWithPartsInInjectedPartsDir() = runBlocking {
        val receiver = HighSpeedDataChannel()
        val sender = HighSpeedDataChannel()
        activeChannels.add(receiver)
        activeChannels.add(sender)

        val fileSize = 256 * 1024L // 4 chunks of 64 KB
        val sourceFile = createSyntheticFile(txDir, "resumable.bin", fileSize)
        val expectedSha = computeSha256(sourceFile)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(
            fileId = fileId,
            name = "resumable.bin",
            size = fileSize,
            sha256 = expectedSha
        )
        receiver.registerManifestItem(manifestItem)
        val token = "SESSION_TOKEN_RESUME_TEST"

        // Pre-create partial state in partsDir: first 2 chunks (128 KB) already written
        val partFile = File(partsDir, "resumable.bin.part")
        val metaFile = File(partsDir, "resumable.bin.part.meta")
        FileOutputStream(partFile).use { fos ->
            sourceFile.inputStream().use { fis ->
                val buf = ByteArray(128 * 1024)
                val read = fis.read(buf)
                fos.write(buf, 0, read)
            }
        }
        val bitSet = BitSet(4).apply {
            set(0)
            set(1)
        }
        val meta = PartFileMeta(
            fileId = fileId,
            fileName = "resumable.bin",
            size = fileSize,
            chunkSize = HighSpeedDataChannel.CHUNK_SIZE,
            totalChunks = 4,
            completedChunksBase64 = PartFileManager.bitSetToBase64(bitSet)
        )
        PartFileManager.writeMeta(metaFile, meta)

        assertTrue("Pre-existing .part file must exist in partsDir", partFile.exists())
        assertTrue("Pre-existing .part.meta file must exist in partsDir", metaFile.exists())

        val fakePublisher = FakePublisher(destDir)
        val boundLatch = CountDownLatch(1)
        val publishedLatch = CountDownLatch(1)

        receiver.startReceiverServer(
            partsDir = partsDir,
            publisher = fakePublisher,
            port = 0,
            expectedSessionToken = token,
            onBound = { boundLatch.countDown() },
            onPublished = { _, _ -> publishedLatch.countDown() }
        )

        assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
        val port = receiver.serverPort

        receiver.notifyChecksumReceived(fileId, expectedSha)

        val sendSuccessLatch = CountDownLatch(1)
        val sendErrorRef = AtomicReference<Throwable?>(null)

        sender.sendFileParallel(
            targetIp = "127.0.0.1",
            manifestItem = manifestItem,
            sourceFile = sourceFile,
            sessionToken = token,
            port = port,
            streamCount = 2,
            resumeBitmap = bitSet,
            onSuccess = { sendSuccessLatch.countDown() },
            onError = { err ->
                sendErrorRef.set(err)
                sendSuccessLatch.countDown()
            }
        )

        assertTrue("Sender must finish", sendSuccessLatch.await(15, TimeUnit.SECONDS))
        if (sendErrorRef.get() != null) {
            throw AssertionError("Sender failed: ${sendErrorRef.get()?.message}", sendErrorRef.get())
        }

        assertTrue("Receiver must finalize and publish resumed file", publishedLatch.await(10, TimeUnit.SECONDS))
        assertEquals(1, fakePublisher.publishCallCount.get())

        val destFile = File(destDir, "resumable.bin")
        assertTrue("Resumed destination file must exist", destFile.exists())
        assertEquals(fileSize, destFile.length())
        assertEquals(expectedSha, computeSha256(destFile))

        assertFalse(".part must be deleted after resumed publish", partFile.exists())
        assertFalse(".part.meta must be deleted after resumed publish", metaFile.exists())
    }

    @Test(timeout = 30000)
    fun testPublishFailureKeepsVerifiedPartFileForRetry() = runBlocking {
        val receiver = HighSpeedDataChannel()
        val sender = HighSpeedDataChannel()
        activeChannels.add(receiver)
        activeChannels.add(sender)

        val fileSize = 128 * 1024L // 128 KB
        val sourceFile = createSyntheticFile(txDir, "critical_data.dat", fileSize)
        val expectedSha = computeSha256(sourceFile)

        val fileId = UUID.randomUUID().toString()
        val manifestItem = FileManifestItem(
            fileId = fileId,
            name = "critical_data.dat",
            size = fileSize,
            sha256 = expectedSha
        )
        receiver.registerManifestItem(manifestItem)
        val token = "SESSION_TOKEN_FAIL_TEST"

        // Publisher that throws an exception to simulate write/storage failure
        val failingPublisher = FakePublisher(destDir, shouldThrow = true)
        val boundLatch = CountDownLatch(1)
        val receiverErrorLatch = CountDownLatch(1)
        val receiverErrorRef = AtomicReference<Throwable?>(null)

        receiver.startReceiverServer(
            partsDir = partsDir,
            publisher = failingPublisher,
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

        receiver.notifyChecksumReceived(fileId, expectedSha)

        val sendSuccessLatch = CountDownLatch(1)
        sender.sendFileParallel(
            targetIp = "127.0.0.1",
            manifestItem = manifestItem,
            sourceFile = sourceFile,
            sessionToken = token,
            port = port,
            streamCount = 2,
            onSuccess = { sendSuccessLatch.countDown() },
            onError = { sendSuccessLatch.countDown() }
        )

        sendSuccessLatch.await(10, TimeUnit.SECONDS)

        // Wait a short moment for finalizeFileTransfer to execute and catch publish error
        receiverErrorLatch.await(5, TimeUnit.SECONDS)

        // Assert 1: FakePublisher was called
        assertEquals("publish() was invoked", 1, failingPublisher.publishCallCount.get())

        // Assert 2: The verified .part file is PRESERVED on disk for retry
        val partFile = File(partsDir, "critical_data.dat.part")
        assertTrue("Verified .part file MUST STILL EXIST on disk for retry after publish failure!", partFile.exists())
        assertEquals("Part file must contain full file bytes", fileSize, partFile.length())
        assertEquals("Part file SHA-256 must match source data", expectedSha, computeSha256(partFile))
    }
}

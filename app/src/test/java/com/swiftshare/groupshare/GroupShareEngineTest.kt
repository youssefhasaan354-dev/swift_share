package com.swiftshare.groupshare

import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.discovery.models.PeerConnectionType
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GroupShareEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var txDir: File
    private lateinit var rxDir1: File
    private lateinit var rxDir2: File

    private val activeChannels = mutableListOf<HighSpeedDataChannel>()

    @Before
    fun setUp() {
        txDir = tempFolder.newFolder("tx")
        rxDir1 = tempFolder.newFolder("rx1")
        rxDir2 = tempFolder.newFolder("rx2")
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

    private fun createSyntheticFile(parent: File, name: String, sizeBytes: Long): File {
        val file = File(parent, name)
        RandomAccessFile(file, "rw").use { raf ->
            val pattern = "SWIFTSHARE_GROUP_TEST_PAYLOAD_CHUNK_DATA_PATTERN\n".toByteArray(Charsets.UTF_8)
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
    fun testBroadcastToOneSenderAndTwoLoopbackReceivers() = runBlocking {
        // Setup Receiver 1
        val receiver1 = HighSpeedDataChannel()
        activeChannels.add(receiver1)
        val boundLatch1 = CountDownLatch(1)
        val receivedLatch1 = CountDownLatch(1)
        var receivedFile1: File? = null
        val token1 = "TOKEN_RX_1"

        receiver1.startReceiverServer(
            downloadDir = rxDir1,
            port = 0,
            expectedSessionToken = token1,
            onBound = { boundLatch1.countDown() },
            onFileReceived = { file, _ ->
                receivedFile1 = file
                receivedLatch1.countDown()
            }
        )
        assertTrue("Receiver 1 must bind within 5s", boundLatch1.await(5, TimeUnit.SECONDS))
        val port1 = receiver1.serverPort

        // Setup Receiver 2
        val receiver2 = HighSpeedDataChannel()
        activeChannels.add(receiver2)
        val boundLatch2 = CountDownLatch(1)
        val receivedLatch2 = CountDownLatch(1)
        var receivedFile2: File? = null
        val token2 = "TOKEN_RX_2"

        receiver2.startReceiverServer(
            downloadDir = rxDir2,
            port = 0,
            expectedSessionToken = token2,
            onBound = { boundLatch2.countDown() },
            onFileReceived = { file, _ ->
                receivedFile2 = file
                receivedLatch2.countDown()
            }
        )
        assertTrue("Receiver 2 must bind within 5s", boundLatch2.await(5, TimeUnit.SECONDS))
        val port2 = receiver2.serverPort

        // Create test payload
        val fileSize = 2L * 1024L * 1024L // 2 MB
        val sourceFile = createSyntheticFile(txDir, "group_presentation.pdf", fileSize)
        val expectedSha = computeSha256(sourceFile)
        val fileId = UUID.randomUUID().toString()

        val manifestItem = FileManifestItem(
            fileId = fileId,
            name = "group_presentation.pdf",
            size = fileSize,
            sha256 = expectedSha
        )

        receiver1.registerManifestItem(manifestItem)
        receiver1.notifyChecksumReceived(fileId, expectedSha)

        receiver2.registerManifestItem(manifestItem)
        receiver2.notifyChecksumReceived(fileId, expectedSha)

        // Setup GroupShareEngine
        val groupEngine = GroupShareEngine(
            channelFactory = {
                val ch = HighSpeedDataChannel()
                activeChannels.add(ch)
                ch
            }
        )

        val hostDevice = PeerDevice(
            id = "host_device_id",
            deviceName = "Host Device",
            ip = "127.0.0.1",
            port = 0,
            connectionType = PeerConnectionType.SAME_LAN_NSD
        )
        groupEngine.createRoom(host = hostDevice, roomName = "Test Portal")

        val peer1 = PeerDevice(
            id = "rx_peer_1",
            deviceName = "Receiver Device 1",
            ip = "127.0.0.1",
            port = port1,
            sessionToken = token1,
            connectionType = PeerConnectionType.SAME_LAN_NSD
        )
        val peer2 = PeerDevice(
            id = "rx_peer_2",
            deviceName = "Receiver Device 2",
            ip = "127.0.0.1",
            port = port2,
            sessionToken = token2,
            connectionType = PeerConnectionType.SAME_LAN_NSD
        )

        groupEngine.addMember(peer1)
        groupEngine.addMember(peer2)

        val allCompletedLatch = CountDownLatch(1)

        // Broadcast to all members simultaneously
        groupEngine.broadcastFileToAllMembers(
            file = sourceFile,
            manifestItem = manifestItem,
            onAllCompleted = {
                allCompletedLatch.countDown()
            }
        )

        assertTrue("Receiver 1 must receive file within 30s", receivedLatch1.await(30, TimeUnit.SECONDS))
        assertTrue("Receiver 2 must receive file within 30s", receivedLatch2.await(30, TimeUnit.SECONDS))
        assertTrue("All broadcast targets must complete within 30s", allCompletedLatch.await(30, TimeUnit.SECONDS))

        assertNotNull("Receiver 1 file must not be null", receivedFile1)
        assertNotNull("Receiver 2 file must not be null", receivedFile2)

        assertEquals("Receiver 1 file size must equal source", fileSize, receivedFile1!!.length())
        assertEquals("Receiver 2 file size must equal source", fileSize, receivedFile2!!.length())

        assertEquals("Receiver 1 SHA-256 must match original", expectedSha, computeSha256(receivedFile1!!))
        assertEquals("Receiver 2 SHA-256 must match original", expectedSha, computeSha256(receivedFile2!!))

        val progressMap = groupEngine.memberProgress.value
        assertEquals("Progress must be tracked for 2 receivers", 2, progressMap.size)

        val prog1 = progressMap["rx_peer_1"]
        assertNotNull("Peer 1 progress must exist", prog1)
        assertEquals(FileTransferStatus.COMPLETED, prog1!!.status)
        assertEquals(fileSize, prog1.transferredBytes)

        val prog2 = progressMap["rx_peer_2"]
        assertNotNull("Peer 2 progress must exist", prog2)
        assertEquals(FileTransferStatus.COMPLETED, prog2!!.status)
        assertEquals(fileSize, prog2.transferredBytes)

        groupEngine.leaveRoom()
    }
}

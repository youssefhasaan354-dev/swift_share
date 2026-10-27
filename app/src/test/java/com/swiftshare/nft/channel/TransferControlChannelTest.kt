package com.swiftshare.nft.channel

import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.channel.models.HandshakeAck
import com.swiftshare.nft.channel.models.HandshakeReq
import com.swiftshare.nft.channel.models.MessageType
import com.swiftshare.nft.channel.models.TransferManifest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TransferControlChannelTest {

    @Test(timeout = 60000)
    fun testControlChannelHandshakeAcceptedWhenTokensMatch() {
        val server = TransferControlChannel()
        val client = TransferControlChannel()
        val token = "SESSION_VALID_123"

        val boundLatch = CountDownLatch(1)
        val serverHandshakeLatch = CountDownLatch(1)
        val clientHandshakeLatch = CountDownLatch(1)
        var serverReceivedReq: HandshakeReq? = null
        var clientReceivedAck: HandshakeAck? = null

        try {
            server.startServer(
                expectedSessionToken = token,
                myDeviceName = "Pixel_8",
                freeStorageBytes = 50_000_000L,
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = { req ->
                    serverReceivedReq = req
                    serverHandshakeLatch.countDown()
                }
            )

            assertTrue("Server must bind to ephemeral port within 5s", boundLatch.await(5, TimeUnit.SECONDS))
            val port = server.serverPort
            assertTrue("Bound port must be > 0", port > 0)

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "Galaxy_S24",
                sessionToken = token,
                freeStorageBytes = 20_000_000L,
                port = port,
                onHandshakeSuccess = { ack ->
                    clientReceivedAck = ack
                    clientHandshakeLatch.countDown()
                },
                onError = {}
            )

            assertTrue("Server handshake must complete within 5s", serverHandshakeLatch.await(5, TimeUnit.SECONDS))
            assertTrue("Client handshake must complete within 5s", clientHandshakeLatch.await(5, TimeUnit.SECONDS))

            assertNotNull(serverReceivedReq)
            assertEquals("Galaxy_S24", serverReceivedReq!!.deviceName)
            assertEquals(token, serverReceivedReq!!.sessionToken)

            assertNotNull(clientReceivedAck)
            assertTrue("Handshake must be accepted", clientReceivedAck!!.accepted)
            assertEquals("OK", clientReceivedAck!!.reason)
        } finally {
            server.close()
            client.close()
        }
    }

    @Test(timeout = 60000)
    fun testControlChannelHandshakeRejectedOnTokenMismatch() {
        val server = TransferControlChannel()
        val client = TransferControlChannel()

        val boundLatch = CountDownLatch(1)
        val errorLatch = CountDownLatch(1)
        var errorMessage: String? = null

        try {
            server.startServer(
                expectedSessionToken = "EXPECTED_TOKEN_CORRECT",
                myDeviceName = "Host_Receiver",
                freeStorageBytes = 10_000_000L,
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = {}
            )

            assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
            val port = server.serverPort

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "Attacker_Sender",
                sessionToken = "WRONG_TOKEN_XYZ",
                freeStorageBytes = 5_000_000L,
                port = port,
                onHandshakeSuccess = {},
                onError = { err ->
                    errorMessage = err
                    errorLatch.countDown()
                }
            )

            assertTrue("Error callback must be called on token mismatch", errorLatch.await(5, TimeUnit.SECONDS))
            assertNotNull(errorMessage)
            assertTrue("Error reason must mention mismatch", errorMessage!!.contains("mismatch", ignoreCase = true))
        } finally {
            server.close()
            client.close()
        }
    }

    @Test(timeout = 60000)
    fun testControlChannelManifestExchangeAndStatusAck() = runBlocking {
        val server = TransferControlChannel()
        val client = TransferControlChannel()
        val token = "SESSION_MANIFEST_TEST"

        val boundLatch = CountDownLatch(1)
        val serverHandshakeLatch = CountDownLatch(1)
        val clientHandshakeLatch = CountDownLatch(1)

        try {
            server.startServer(
                expectedSessionToken = token,
                myDeviceName = "ServerDev",
                freeStorageBytes = 100_000_000L,
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = { serverHandshakeLatch.countDown() }
            )

            assertTrue(boundLatch.await(5, TimeUnit.SECONDS))
            val port = server.serverPort

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "ClientDev",
                sessionToken = token,
                freeStorageBytes = 50_000_000L,
                port = port,
                onHandshakeSuccess = { clientHandshakeLatch.countDown() },
                onError = {}
            )

            assertTrue(serverHandshakeLatch.await(5, TimeUnit.SECONDS))
            assertTrue(clientHandshakeLatch.await(5, TimeUnit.SECONDS))

            // 1. Prepare collector FIRST, then send manifest
            val manifestDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    server.incomingMessages.first()
                }
            }

            val manifest = TransferManifest(
                batchId = "batch_test_001",
                fileCount = 1,
                totalBytes = 2048L,
                files = listOf(
                    FileManifestItem(
                        fileId = "f1",
                        name = "document.pdf",
                        size = 2048L,
                        mimeType = "application/pdf"
                    )
                )
            )
            client.sendManifest(manifest)

            val receivedMessage = manifestDeferred.await()
            assertEquals(MessageType.TRANSFER_MANIFEST, receivedMessage.type)
            assertEquals("batch_test_001", receivedMessage.manifest?.batchId)
            assertEquals(1, receivedMessage.manifest?.fileCount)
            assertEquals("document.pdf", receivedMessage.manifest?.files?.firstOrNull()?.name)

            // 2. Prepare collector FIRST, then send status ACK
            val ackDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    client.incomingMessages.first()
                }
            }

            server.sendStatusAck(
                batchId = "batch_test_001",
                fileId = "f1",
                status = FileTransferStatus.COMPLETED,
                bytes = 2048L
            )

            val ackMessage = ackDeferred.await()
            assertEquals(MessageType.FILE_STATUS_ACK, ackMessage.type)
            assertEquals("f1", ackMessage.statusAck?.fileId)
            assertEquals(FileTransferStatus.COMPLETED, ackMessage.statusAck?.status)
            assertEquals(2048L, ackMessage.statusAck?.transferredBytes)
        } finally {
            server.close()
            client.close()
        }
    }
}

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

    @Test(timeout = 60000)
    fun testTransferOfferAndAcceptResponse() = runBlocking {
        val server = TransferControlChannel()
        val client = TransferControlChannel()
        val token = "SESSION_OFFER_TEST"

        val boundLatch = CountDownLatch(1)
        val serverHandshakeLatch = CountDownLatch(1)
        val clientHandshakeLatch = CountDownLatch(1)

        try {
            server.startServer(
                expectedSessionToken = token,
                myDeviceName = "ReceiverPhone",
                freeStorageBytes = 500_000_000L,
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = { serverHandshakeLatch.countDown() }
            )
            assertTrue(boundLatch.await(5, TimeUnit.SECONDS))

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "SenderPhone",
                sessionToken = token,
                freeStorageBytes = 500_000_000L,
                port = server.serverPort,
                onHandshakeSuccess = { clientHandshakeLatch.countDown() },
                onError = {}
            )
            assertTrue(serverHandshakeLatch.await(5, TimeUnit.SECONDS))
            assertTrue(clientHandshakeLatch.await(5, TimeUnit.SECONDS))

            // Sender sends TransferOffer
            val offerDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) { server.incomingMessages.first() }
            }

            val offer = com.swiftshare.nft.channel.models.TransferOffer(
                batchId = "batch_001",
                senderName = "SenderPhone",
                fileCount = 2,
                totalBytes = 10_000_000L,
                files = listOf(
                    FileManifestItem(fileId = "f1", name = "photo.jpg", size = 4_000_000L, mimeType = "image/jpeg"),
                    FileManifestItem(fileId = "f2", name = "video.mp4", size = 6_000_000L, mimeType = "video/mp4")
                )
            )
            client.sendTransferOffer(offer)

            val receivedOffer = offerDeferred.await()
            assertEquals(MessageType.TRANSFER_OFFER, receivedOffer.type)
            assertEquals("batch_001", receivedOffer.transferOffer?.batchId)
            assertEquals("SenderPhone", receivedOffer.transferOffer?.senderName)
            assertEquals(2, receivedOffer.transferOffer?.fileCount)
            assertEquals(10_000_000L, receivedOffer.transferOffer?.totalBytes)

            // Receiver responds with accepted
            val responseDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) { client.incomingMessages.first() }
            }

            val response = com.swiftshare.nft.channel.models.TransferResponse(
                batchId = "batch_001",
                accepted = true,
                reason = com.swiftshare.nft.channel.models.TransferRejectionReasons.OK
            )
            server.sendTransferResponse(response)

            val receivedResponse = responseDeferred.await()
            assertEquals(MessageType.TRANSFER_RESPONSE, receivedResponse.type)
            assertTrue(receivedResponse.transferResponse?.accepted == true)
            assertEquals("OK", receivedResponse.transferResponse?.reason)
        } finally {
            server.close()
            client.close()
        }
    }

    @Test(timeout = 60000)
    fun testTransferOfferAndRejectNotEnoughSpace() = runBlocking {
        val server = TransferControlChannel()
        val client = TransferControlChannel()
        val token = "SESSION_SPACE_TEST"

        val boundLatch = CountDownLatch(1)
        val serverHandshakeLatch = CountDownLatch(1)
        val clientHandshakeLatch = CountDownLatch(1)

        try {
            server.startServer(
                expectedSessionToken = token,
                myDeviceName = "LowSpaceReceiver",
                freeStorageBytes = 1_000_000L, // only 1 MB
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = { serverHandshakeLatch.countDown() }
            )
            assertTrue(boundLatch.await(5, TimeUnit.SECONDS))

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "SenderPhone",
                sessionToken = token,
                freeStorageBytes = 500_000_000L,
                port = server.serverPort,
                onHandshakeSuccess = { clientHandshakeLatch.countDown() },
                onError = {}
            )
            assertTrue(serverHandshakeLatch.await(5, TimeUnit.SECONDS))
            assertTrue(clientHandshakeLatch.await(5, TimeUnit.SECONDS))

            val responseDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) { client.incomingMessages.first() }
            }

            // Receiver sends rejection with NOT_ENOUGH_SPACE
            server.sendTransferResponse(
                com.swiftshare.nft.channel.models.TransferResponse(
                    batchId = "batch_huge",
                    accepted = false,
                    reason = com.swiftshare.nft.channel.models.TransferRejectionReasons.NOT_ENOUGH_SPACE
                )
            )

            val receivedResponse = responseDeferred.await()
            assertEquals(MessageType.TRANSFER_RESPONSE, receivedResponse.type)
            assertEquals(false, receivedResponse.transferResponse?.accepted)
            assertEquals(com.swiftshare.nft.channel.models.TransferRejectionReasons.NOT_ENOUGH_SPACE, receivedResponse.transferResponse?.reason)
        } finally {
            server.close()
            client.close()
        }
    }

    @Test(timeout = 60000)
    fun testCancelTransferItemAndBatch() = runBlocking {
        val server = TransferControlChannel()
        val client = TransferControlChannel()
        val token = "SESSION_CANCEL_TEST"

        val boundLatch = CountDownLatch(1)
        val serverHandshakeLatch = CountDownLatch(1)
        val clientHandshakeLatch = CountDownLatch(1)

        try {
            server.startServer(
                expectedSessionToken = token,
                myDeviceName = "Server",
                freeStorageBytes = 100_000_000L,
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = { serverHandshakeLatch.countDown() }
            )
            assertTrue(boundLatch.await(5, TimeUnit.SECONDS))

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "Client",
                sessionToken = token,
                freeStorageBytes = 100_000_000L,
                port = server.serverPort,
                onHandshakeSuccess = { clientHandshakeLatch.countDown() },
                onError = {}
            )
            assertTrue(serverHandshakeLatch.await(5, TimeUnit.SECONDS))
            assertTrue(clientHandshakeLatch.await(5, TimeUnit.SECONDS))

            val cancelDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) { server.incomingMessages.first() }
            }

            client.sendCancelTransfer(
                com.swiftshare.nft.channel.models.CancelTransfer(
                    batchId = "batch_1",
                    fileId = "f1",
                    reason = com.swiftshare.nft.channel.models.TransferRejectionReasons.CANCELLED
                )
            )

            val receivedCancel = cancelDeferred.await()
            assertEquals(MessageType.CANCEL_TRANSFER, receivedCancel.type)
            assertEquals("batch_1", receivedCancel.cancelTransfer?.batchId)
            assertEquals("f1", receivedCancel.cancelTransfer?.fileId)
            assertEquals("CANCELLED", receivedCancel.cancelTransfer?.reason)
        } finally {
            server.close()
            client.close()
        }
    }

    @Test(timeout = 60000)
    fun testPresenceHeartbeatTimeoutAfter3Misses() = runBlocking {
        val server = TransferControlChannel()
        val client = TransferControlChannel()
        val token = "SESSION_HB_TEST"

        val boundLatch = CountDownLatch(1)
        val serverHandshakeLatch = CountDownLatch(1)
        val clientHandshakeLatch = CountDownLatch(1)

        try {
            server.startServer(
                expectedSessionToken = token,
                myDeviceName = "Server",
                freeStorageBytes = 100_000_000L,
                port = 0,
                onBound = { boundLatch.countDown() },
                onHandshakeComplete = { serverHandshakeLatch.countDown() }
            )
            assertTrue(boundLatch.await(5, TimeUnit.SECONDS))

            client.connectToHost(
                hostIp = "127.0.0.1",
                myDeviceName = "Client",
                sessionToken = token,
                freeStorageBytes = 100_000_000L,
                port = server.serverPort,
                onHandshakeSuccess = { clientHandshakeLatch.countDown() },
                onError = {}
            )
            assertTrue(serverHandshakeLatch.await(5, TimeUnit.SECONDS))
            assertTrue(clientHandshakeLatch.await(5, TimeUnit.SECONDS))

            val peerLostLatch = CountDownLatch(1)

            // Start heartbeat monitoring on server with onPeerLost callback
            server.startHeartbeat("Server", onPeerLost = {
                peerLostLatch.countDown()
            })

            // Client sends 1 heartbeat
            client.sendHeartbeat("Client", sequence = 0L)

            // Client stops sending (disconnects / closes)
            client.close()

            // After 3 misses (> 9 seconds), server should trigger onPeerLost
            val peerLost = peerLostLatch.await(13, TimeUnit.SECONDS)
            assertTrue("Peer presence monitor must trigger onPeerLost after 3 missed heartbeats (>9s)", peerLost)
        } finally {
            server.close()
            client.close()
        }
    }
}

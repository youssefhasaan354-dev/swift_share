package com.swiftshare.common

import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.TransferControlChannel
import com.swiftshare.webshare.EmbeddedWebShareServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DistinctPortTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(timeout = 60000)
    fun testDefaultPortsAllStartConcurrentlyWithoutBindException() {
        val controlPort = Constants.PORT_CONTROL_HTTP // 2999
        val dataPort = Constants.PORT_DATA            // 2998
        val webPort = Constants.PORT_WEB_PC           // 8080

        // Assert constant values are distinct
        assertTrue("Control and Data ports must differ", controlPort != dataPort)
        assertTrue("Control and Web ports must differ", controlPort != webPort)
        assertTrue("Data and Web ports must differ", dataPort != webPort)

        val controlChannel = TransferControlChannel()
        val dataChannel = HighSpeedDataChannel()
        val webServer = EmbeddedWebShareServer()
        val rxDir = tempFolder.newFolder("distinct_ports_rx")

        val controlLatch = CountDownLatch(1)
        val dataLatch = CountDownLatch(1)

        try {
            // 1. Start Control Channel (2999)
            controlChannel.startServer(
                expectedSessionToken = "TOK_CONTROL",
                myDeviceName = "TestHost",
                freeStorageBytes = 1000L,
                port = controlPort,
                onBound = { controlLatch.countDown() },
                onHandshakeComplete = {}
            )

            // 2. Start HighSpeedDataChannel (2998)
            dataChannel.startReceiverServer(
                downloadDir = rxDir,
                port = dataPort,
                expectedSessionToken = "TOK_DATA",
                onBound = { dataLatch.countDown() }
            )

            // 3. Start EmbeddedWebShareServer (8080)
            webServer.start(port = webPort, sessionToken = "TOK_WEB")

            assertTrue("Control channel must bind successfully", controlLatch.await(5, TimeUnit.SECONDS))
            assertTrue("Data channel must bind successfully", dataLatch.await(5, TimeUnit.SECONDS))

            // Verify Control socket accepts TCP connections
            Socket("127.0.0.1", controlPort).use { sock ->
                sock.soTimeout = 5000
                assertTrue("Control socket must be connected", sock.isConnected)
            }

            // Verify Data socket accepts TCP connections
            Socket("127.0.0.1", dataPort).use { sock ->
                sock.soTimeout = 5000
                assertTrue("Data socket must be connected", sock.isConnected)
            }

            // Verify Web server responds over HTTP
            val url = URL("http://127.0.0.1:$webPort/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            assertEquals("Web server must return 200 on /login", 200, conn.responseCode)
            conn.disconnect()

        } finally {
            controlChannel.close()
            dataChannel.cancel()
            webServer.stop()
        }
    }

    @Test(timeout = 60000)
    fun testParameterizedEphemeralPortsAllStartWithoutConflict() {
        val controlChannel = TransferControlChannel()
        val dataChannel = HighSpeedDataChannel()
        val rxDir = tempFolder.newFolder("ephemeral_rx")

        val controlLatch = CountDownLatch(1)
        val dataLatch = CountDownLatch(1)

        try {
            // Port 0 picks ephemeral port
            controlChannel.startServer(
                expectedSessionToken = "TOK_CONTROL_EPH",
                myDeviceName = "TestHost",
                freeStorageBytes = 1000L,
                port = 0,
                onBound = { controlLatch.countDown() },
                onHandshakeComplete = {}
            )

            dataChannel.startReceiverServer(
                downloadDir = rxDir,
                port = 0,
                expectedSessionToken = "TOK_DATA_EPH",
                onBound = { dataLatch.countDown() }
            )

            assertTrue("Control channel must bind ephemeral port", controlLatch.await(5, TimeUnit.SECONDS))
            assertTrue("Data channel must bind ephemeral port", dataLatch.await(5, TimeUnit.SECONDS))

            val cPort = controlChannel.serverPort
            val dPort = dataChannel.serverPort

            assertTrue("Control port must be > 0", cPort > 0)
            assertTrue("Data port must be > 0", dPort > 0)
            assertTrue("Ports must be distinct", cPort != dPort)

            Socket("127.0.0.1", cPort).use { it.close() }
            Socket("127.0.0.1", dPort).use { it.close() }
        } finally {
            controlChannel.close()
            dataChannel.cancel()
        }
    }
}

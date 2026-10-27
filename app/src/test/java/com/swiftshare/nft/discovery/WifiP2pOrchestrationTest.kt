package com.swiftshare.nft.discovery

import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiP2pOrchestrationTest {

    @Test(timeout = 60000)
    fun testWifiP2pFailureReasonMapping() {
        val unsupportedMsg = WifiP2pDiscovery.mapFailureReason(WifiP2pManager.P2P_UNSUPPORTED)
        assertTrue(unsupportedMsg.contains("unsupported", ignoreCase = true))

        val busyMsg = WifiP2pDiscovery.mapFailureReason(WifiP2pManager.BUSY)
        assertTrue(busyMsg.contains("busy", ignoreCase = true))

        val errorMsg = WifiP2pDiscovery.mapFailureReason(WifiP2pManager.ERROR)
        assertTrue(errorMsg.contains("Failed", ignoreCase = true))

        val customMsg = WifiP2pDiscovery.mapFailureReason(999)
        assertTrue(customMsg.contains("999"))
    }

    @Test(timeout = 60000)
    fun testResolveHostIpWithNullOrUnformedGroup() {
        assertNull(WifiP2pDiscovery.resolveHostIp(null))
    }
}

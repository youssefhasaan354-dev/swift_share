package com.swiftshare.nft.discovery

import android.content.Context
import android.os.Build
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeviceDiscoveryManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    val wifiP2p = WifiP2pDiscovery(context)
    val hotspotManager = LocalHotspotManager(context)
    val nsdManager = NsdDiscoveryManager(context)
    val bleManager = BleDiscoveryManager(context)

    private val _activePayload = MutableStateFlow<ConnectionPayload?>(null)
    val activePayload: StateFlow<ConnectionPayload?> = _activePayload.asStateFlow()

    private val _allDiscoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val allDiscoveredPeers: StateFlow<List<PeerDevice>> = _allDiscoveredPeers.asStateFlow()

    init {
        // Combine discovery results from Wi-Fi Direct, NSD, and BLE concurrently
        scope.launch {
            combine(
                wifiP2p.discoveredPeers,
                nsdManager.discoveredPeers,
                bleManager.discoveredPeers
            ) { p2pList, nsdMap, bleMap ->
                val combined = mutableMapOf<String, PeerDevice>()
                p2pList.forEach { combined[it.id] = it }
                nsdMap.values.forEach { combined[it.id] = it }
                bleMap.values.forEach { combined[it.id] = it }
                combined.values.toList()
            }.collect { mergedList ->
                _allDiscoveredPeers.value = mergedList
            }
        }

        // Periodic stale peer pruning (remove peers after 3 missed heartbeats/beacons = 9s)
        scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(3000)
                val now = System.currentTimeMillis()
                val current = _allDiscoveredPeers.value
                val active = current.filter {
                    it.connectionType == com.swiftshare.nft.discovery.models.PeerConnectionType.WIFI_DIRECT || (now - it.lastSeenTimestamp) < 9000L
                }
                if (active.size != current.size) {
                    _allDiscoveredPeers.value = active
                }
            }
        }
    }

    fun startReceiverMode(
        deviceName: String,
        avatarId: Int = 1,
        onReady: (ConnectionPayload) -> Unit,
        onError: (String) -> Unit
    ) {
        val sessionToken = NetworkUtils.generateSessionToken()
        val psk = NetworkUtils.generatePreSharedKey()

        val rawLocalIp = NetworkUtils.getLocalIpAddress(context)
        val validLanIp = if (rawLocalIp.isNotBlank() && rawLocalIp != "0.0.0.0" && !rawLocalIp.startsWith("192.168.49")) rawLocalIp else null
        val isOnLan = validLanIp != null

        val currentEmoji = com.swiftshare.common.LocaleManager.avatarEmoji.value
        val currentPhoto = com.swiftshare.common.LocaleManager.customAvatarBase64.value

        // ── Step 1: Always advertise on Same-LAN (NSD + UDP 55283) concurrently
        scope.launch { nsdManager.registerService(Constants.PORT_CONTROL, deviceName) }
        scope.launch { nsdManager.startUdpBroadcasting(deviceName, Constants.PORT_CONTROL, sessionToken, "Android", currentEmoji) }
        scope.launch { bleManager.startAdvertising(deviceName, sessionToken) }

        // ── Step 2: Attempt Wi-Fi Direct Group / LocalOnlyHotspot concurrently
        wifiP2p.createGroup(
            onSuccess = { group ->
                val ssid = group?.networkName ?: "DIRECT-SS-SwiftShare"
                val pass = group?.passphrase ?: psk
                val payload = ConnectionPayload(
                    ssid = ssid,
                    preSharedKey = pass,
                    ip = "192.168.49.1",
                    port = Constants.PORT_CONTROL,
                    sessionToken = sessionToken,
                    deviceName = deviceName,
                    avatarId = avatarId,
                    avatarEmoji = currentEmoji,
                    customAvatarBase64 = currentPhoto,
                    deviceType = "Android",
                    lanIp = validLanIp
                )
                _activePayload.value = payload
                onReady(payload)
            },
            onFailure = {
                // LocalOnlyHotspot fallback
                hotspotManager.startLocalOnlyHotspot(
                    onSuccess = { config ->
                        val payload = ConnectionPayload(
                            ssid = config.ssid,
                            preSharedKey = config.preSharedKey,
                            ip = config.ip,
                            port = Constants.PORT_CONTROL,
                            sessionToken = sessionToken,
                            deviceName = deviceName,
                            avatarId = avatarId,
                            avatarEmoji = currentEmoji,
                            customAvatarBase64 = currentPhoto,
                            deviceType = "Android",
                            lanIp = validLanIp
                        )
                        _activePayload.value = payload
                        onReady(payload)
                    },
                    onFailure = { _ ->
                        // Fallback to pure LAN connection if hotspot could not be created
                        val fallbackIp = validLanIp ?: "192.168.49.1"
                        val payload = ConnectionPayload(
                            ssid = "LAN-WIFI",
                            preSharedKey = "",
                            ip = fallbackIp,
                            port = Constants.PORT_CONTROL,
                            sessionToken = sessionToken,
                            deviceName = deviceName,
                            avatarId = avatarId,
                            avatarEmoji = currentEmoji,
                            customAvatarBase64 = currentPhoto,
                            deviceType = "Android",
                            lanIp = validLanIp
                        )
                        _activePayload.value = payload
                        onReady(payload)
                    }
                )
            }
        )
    }

    fun startSenderMode() {
        // Launch all discovery methods concurrently in parallel
        scope.launch { wifiP2p.startDiscovery() }
        scope.launch { nsdManager.startDiscovery() }
        scope.launch { bleManager.startScanning() }
    }

    fun stop() {
        wifiP2p.cleanup()
        hotspotManager.stopHotspot()
        nsdManager.stop()
        bleManager.stop()
        QrPayloadManager.unbindProcessNetwork(context)
        _activePayload.value = null
        _allDiscoveredPeers.value = emptyList()
    }
}

package com.swiftshare.nft.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.swiftshare.common.Constants
import com.swiftshare.nft.discovery.models.PeerConnectionType
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class NsdDiscoveryManager(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _discoveredPeers = MutableStateFlow<Map<String, PeerDevice>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, PeerDevice>> = _discoveredPeers.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var udpBroadcastJob: Job? = null
    private var udpListenJob: Job? = null

    fun registerService(port: Int, deviceName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$deviceName-SwiftShare"
            serviceType = Constants.NSD_SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo) {}
            override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(service: NsdServiceInfo) {}
            override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("swiftshare")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val current = _discoveredPeers.value.toMutableMap()
                current.remove(service.serviceName)
                _discoveredPeers.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager?.discoverServices(Constants.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        startUdpBeaconDiscovery()
    }

    private fun resolveService(service: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val peer = PeerDevice(
                    id = "$host:$port",
                    deviceName = serviceInfo.serviceName.removeSuffix("-SwiftShare"),
                    ip = host,
                    port = port,
                    connectionType = PeerConnectionType.SAME_LAN_NSD
                )
                val current = _discoveredPeers.value.toMutableMap()
                current[peer.id] = peer
                _discoveredPeers.value = current
            }
        }
        nsdManager?.resolveService(service, resolveListener)
    }

    private fun startUdpBeaconDiscovery() {
        // UDP Broadcast listener on port 55283
        udpListenJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(Constants.PORT_UDP_BROADCAST)
                socket.broadcast = true
                val buffer = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length).trim()
                    // Format: SWIFTSHARE_BEACON|<deviceName>|<port>|<token>|<deviceType>|<avatarEmoji>
                    if (message.startsWith("SWIFTSHARE_BEACON|")) {
                        val parts = message.split("|")
                        if (parts.size >= 4) {
                            val senderName = parts[1]
                            val senderPort = parts[2].toIntOrNull() ?: Constants.PORT_CONTROL_HTTP
                            val token = parts[3]
                            val devType = if (parts.size >= 5 && parts[4].isNotBlank()) parts[4] else "Android"
                            val emoji = if (parts.size >= 6 && parts[5].isNotBlank()) parts[5] else null
                            val senderIp = packet.address.hostAddress ?: continue
                            val peer = PeerDevice(
                                id = "$senderIp:$senderPort",
                                deviceName = senderName,
                                ip = senderIp,
                                port = senderPort,
                                sessionToken = token,
                                deviceType = devType,
                                avatarEmoji = emoji,
                                connectionType = PeerConnectionType.SAME_LAN_NSD,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            val current = _discoveredPeers.value.toMutableMap()
                            current[peer.id] = peer
                            _discoveredPeers.value = current
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }

    fun startUdpBroadcasting(
        deviceName: String,
        port: Int,
        sessionToken: String,
        deviceType: String = "Android",
        avatarEmoji: String? = null
    ) {
        udpBroadcastJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val emojiStr = avatarEmoji ?: ""
                val payload = "SWIFTSHARE_BEACON|$deviceName|$port|$sessionToken|$deviceType|$emojiStr".toByteArray()
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(payload, payload.size, broadcastAddress, Constants.PORT_UDP_BROADCAST)

                while (isActive) {
                    socket.send(packet)
                    delay(2000)
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }

    fun stop() {
        udpBroadcastJob?.cancel()
        udpListenJob?.cancel()
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Exception) {}
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        registrationListener = null
        discoveryListener = null
        _discoveredPeers.value = emptyMap()
    }
}

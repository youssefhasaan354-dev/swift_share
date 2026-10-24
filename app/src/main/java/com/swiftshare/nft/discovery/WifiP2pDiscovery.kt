package com.swiftshare.nft.discovery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import com.swiftshare.nft.discovery.models.PeerConnectionType
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiP2pDiscovery(private val context: Context) {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _groupInfo = MutableStateFlow<WifiP2pGroup?>(null)
    val groupInfo: StateFlow<WifiP2pGroup?> = _groupInfo.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager?.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                            peers?.deviceList?.let { deviceList ->
                                val list = deviceList.map { device ->
                                    PeerDevice(
                                        id = device.deviceAddress,
                                        deviceName = device.deviceName.ifBlank { "SwiftShare Peer" },
                                        ip = "192.168.49.1",
                                        port = 2999,
                                        connectionType = PeerConnectionType.WIFI_DIRECT
                                    )
                                }
                                _discoveredPeers.value = list
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (networkInfo?.isConnected == true) {
                            manager?.requestConnectionInfo(channel) { info ->
                                _connectionInfo.value = info
                            }
                            manager?.requestGroupInfo(channel) { group ->
                                _groupInfo.value = group
                            }
                        } else {
                            _connectionInfo.value = null
                            _groupInfo.value = null
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, intentFilter)
    }

    @SuppressLint("MissingPermission")
    fun createGroup(onSuccess: (WifiP2pGroup?) -> Unit, onFailure: (Int) -> Unit) {
        manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.requestGroupInfo(channel) { group ->
                    _groupInfo.value = group
                    onSuccess(group)
                }
            }

            override fun onFailure(reason: Int) {
                onFailure(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure(reason)
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure(reason)
        })
    }

    fun removeGroup() {
        manager?.removeGroup(channel, null)
        _groupInfo.value = null
        _connectionInfo.value = null
    }

    fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, null)
    }

    fun cleanup() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        stopDiscovery()
        removeGroup()
    }
}

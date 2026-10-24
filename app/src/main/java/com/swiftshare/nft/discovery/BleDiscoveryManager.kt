package com.swiftshare.nft.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.swiftshare.common.Constants
import com.swiftshare.nft.discovery.models.PeerConnectionType
import com.swiftshare.nft.discovery.models.PeerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleDiscoveryManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val _discoveredPeers = MutableStateFlow<Map<String, PeerDevice>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, PeerDevice>> = _discoveredPeers.asStateFlow()

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val serviceUuid = UUID.fromString(Constants.BLE_SERVICE_UUID)
    private val parcelUuid = ParcelUuid(serviceUuid)

    @SuppressLint("MissingPermission")
    fun startAdvertising(deviceName: String, payloadData: String) {
        if (adapter?.isEnabled != true) return
        advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // BLE service data max 24 bytes in advertisement packet
        val dataBytes = payloadData.take(20).toByteArray(StandardCharsets.UTF_8)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(parcelUuid)
            .addServiceData(parcelUuid, dataBytes)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {}
            override fun onStartFailure(errorCode: Int) {}
        }

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (adapter?.isEnabled != true) return
        scanner = adapter.bluetoothLeScanner ?: return

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(parcelUuid)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val res = result ?: return
                val serviceData = res.scanRecord?.getServiceData(parcelUuid)
                val rawPayload = serviceData?.let { String(it, StandardCharsets.UTF_8) } ?: ""
                
                val peer = PeerDevice(
                    id = res.device.address,
                    deviceName = res.device.name ?: "Nearby BLE Peer",
                    ip = "192.168.49.1",
                    port = 2999,
                    rssi = res.rssi,
                    connectionType = PeerConnectionType.BLE,
                    sessionToken = rawPayload
                )

                val current = _discoveredPeers.value.toMutableMap()
                current[peer.id] = peer
                _discoveredPeers.value = current
            }

            override fun onScanFailed(errorCode: Int) {}
        }

        scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Exception) {}
        advertiseCallback = null
        scanCallback = null
        _discoveredPeers.value = emptyMap()
    }
}

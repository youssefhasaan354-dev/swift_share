package com.swiftshare.nft.discovery

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotConfig(
    val ssid: String,
    val preSharedKey: String,
    val ip: String = "192.168.43.1"
)

class LocalHotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _hotspotState = MutableStateFlow<HotspotConfig?>(null)
    val hotspotState: StateFlow<HotspotConfig?> = _hotspotState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLocalOnlyHotspot(onSuccess: (HotspotConfig) -> Unit, onFailure: (String) -> Unit) {
        if (wifiManager == null) {
            onFailure("WifiManager not available")
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation?) {
                    super.onStarted(res)
                    reservation = res

                    val (ssid, pass) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val config = res?.softApConfiguration
                        Pair(config?.ssid ?: "SwiftShare-Hotspot", config?.passphrase ?: "swiftshare123")
                    } else {
                        @Suppress("DEPRECATION")
                        val config = res?.wifiConfiguration
                        Pair(config?.SSID ?: "SwiftShare-Hotspot", config?.preSharedKey ?: "swiftshare123")
                    }

                    val hotspotConfig = HotspotConfig(
                        ssid = ssid,
                        preSharedKey = pass,
                        ip = "192.168.43.1"
                    )
                    _hotspotState.value = hotspotConfig
                    onSuccess(hotspotConfig)
                }

                override fun onStopped() {
                    super.onStopped()
                    _hotspotState.value = null
                    reservation = null
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    val errorMsg = when (reason) {
                        ERROR_NO_CHANNEL -> "Hotspot failed: No channel available"
                        ERROR_GENERIC -> "Hotspot failed: Generic error"
                        ERROR_INCOMPATIBLE_MODE -> "Hotspot failed: Incompatible mode"
                        ERROR_TETHERING_DISALLOWED -> "Hotspot failed: Tethering disallowed"
                        else -> "Hotspot failed with code $reason"
                    }
                    _errorState.value = errorMsg
                    onFailure(errorMsg)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            _errorState.value = e.message
            onFailure(e.message ?: "Failed to start LocalOnlyHotspot")
        }
    }

    fun stopHotspot() {
        try {
            reservation?.close()
        } catch (_: Exception) {}
        reservation = null
        _hotspotState.value = null
    }
}

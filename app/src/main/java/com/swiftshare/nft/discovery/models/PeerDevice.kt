package com.swiftshare.nft.discovery.models

import kotlinx.serialization.Serializable

enum class PeerConnectionType {
    WIFI_DIRECT,
    HOTSPOT,
    SAME_LAN_NSD,
    BLE
}

@Serializable
data class PeerDevice(
    val id: String,
    val deviceName: String,
    val ip: String,
    val port: Int = 2999,
    val avatarId: Int = 1,
    val avatarEmoji: String? = null,
    val customAvatarBase64: String? = null,
    val deviceType: String = "Android",
    val rssi: Int = -50,
    val connectionType: PeerConnectionType = PeerConnectionType.WIFI_DIRECT,
    val sessionToken: String? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)


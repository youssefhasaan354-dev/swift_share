package com.swiftshare.nft.discovery.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ConnectionPayload(
    val ssid: String,
    val bssid: String = "02:00:00:00:00:00",
    val preSharedKey: String,
    val ip: String = "192.168.49.1",
    val port: Int = 2999,
    val sessionToken: String,
    val deviceName: String,
    val avatarId: Int = 1,
    val lanIp: String? = null
) {
    fun toJson(): String {
        return Json { ignoreUnknownKeys = true; prettyPrint = false }.encodeToString(this)
    }

    companion object {
        fun fromJson(jsonStr: String): ConnectionPayload? {
            return try {
                Json { ignoreUnknownKeys = true }.decodeFromString<ConnectionPayload>(jsonStr)
            } catch (e: Exception) {
                null
            }
        }
    }
}

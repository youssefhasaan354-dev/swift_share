package com.swiftshare.common

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String {
        try {
            // First try Wi-Fi Direct interface (p2p) or Hotspot (wlan0 / ap0)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        // Prioritize Wi-Fi P2P subnet 192.168.49.x
                        if (host.startsWith("192.168.49.")) return host
                        // Local hotspot subnet 192.168.43.x
                        if (host.startsWith("192.168.43.")) return host
                        if (host.startsWith("192.168.")) return host
                        if (host.startsWith("10.") || host.startsWith("172.")) return host
                    }
                }
            }

            // Fallback to WifiManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "192.168.49.1" // Standard Android Wi-Fi Direct GO default
    }

    fun generateSessionToken(): String {
        return UUID.randomUUID().toString()
    }

    fun generatePreSharedKey(): String {
        val chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..10)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    fun formatSpeed(bytesPerSec: Long): String {
        val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) {
            String.format("%.1f MB/s", mbPerSec)
        } else {
            val kbPerSec = bytesPerSec / 1024.0
            String.format("%.1f KB/s", kbPerSec)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.2f %s", formatted, units[digitGroups.coerceAtMost(units.size - 1)])
    }
}

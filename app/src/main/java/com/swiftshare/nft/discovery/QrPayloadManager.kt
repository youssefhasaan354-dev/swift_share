package com.swiftshare.nft.discovery

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrPayloadManager {

    fun generateQrCode(content: String, size: Int = 600): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val primaryColor = 0xFF0066FF.toInt()
        val accentColor = 0xFF00E5FF.toInt()
        val bgColor = 0xFFFFFFFF.toInt()

        for (x in 0 until size) {
            for (y in 0 until size) {
                if (bitMatrix.get(x, y)) {
                    // Linear gradient across QR code pixels for sleek styling
                    val ratio = (x + y).toFloat() / (2 * size)
                    val r = ((1 - ratio) * Color.red(primaryColor) + ratio * Color.red(accentColor)).toInt()
                    val g = ((1 - ratio) * Color.green(primaryColor) + ratio * Color.green(accentColor)).toInt()
                    val b = ((1 - ratio) * Color.blue(primaryColor) + ratio * Color.blue(accentColor)).toInt()
                    bitmap.setPixel(x, y, Color.rgb(r, g, b))
                } else {
                    bitmap.setPixel(x, y, bgColor)
                }
            }
        }
        return bitmap
    }

    /**
     * Parses any of the 3 QR formats:
     * 1. SwiftShare JSON ConnectionPayload
     * 2. Standard Wi-Fi QR: WIFI:T:WPA;S:ssid;P:pass;;
     * 3. WebShare URL: http://ip:port
     */
    fun parseQrString(qrStr: String): com.swiftshare.nft.discovery.models.ConnectionPayload? {
        val trimmed = qrStr.trim()

        // 1. JSON ConnectionPayload
        val jsonPayload = com.swiftshare.nft.discovery.models.ConnectionPayload.fromJson(trimmed)
        if (jsonPayload != null) return jsonPayload

        // 2. Standard Wi-Fi QR format
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val parts = trimmed.substring(5).split(";")
            var ssid = ""
            var pass = ""
            for (part in parts) {
                val keyVal = part.split(":", limit = 2)
                if (keyVal.size == 2) {
                    when (keyVal[0].uppercase()) {
                        "S" -> ssid = keyVal[1]
                        "P" -> pass = keyVal[1]
                    }
                }
            }
            if (ssid.isNotBlank()) {
                return com.swiftshare.nft.discovery.models.ConnectionPayload(
                    ssid = ssid,
                    preSharedKey = pass,
                    ip = "192.168.49.1",
                    port = com.swiftshare.common.Constants.PORT_CONTROL,
                    sessionToken = "WIFI_QR",
                    deviceName = ssid
                )
            }
        }

        // 3. WebShare URL format
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            try {
                val uri = android.net.Uri.parse(trimmed)
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else com.swiftshare.common.Constants.PORT_WEB_PC
                if (host.isNotBlank()) {
                    return com.swiftshare.nft.discovery.models.ConnectionPayload(
                        ssid = "LAN-WIFI",
                        preSharedKey = "",
                        ip = host,
                        port = port,
                        sessionToken = "WEB_QR",
                        deviceName = "WebShare ($host)"
                    )
                }
            } catch (_: Exception) {}
        }

        return null
    }

    class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { rawValue ->
                                onBarcodeDetected(rawValue)
                                return@addOnSuccessListener
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    fun unbindProcessNetwork(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.bindProcessToNetwork(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("QrPayloadManager", "Failed to bindProcessToNetwork(null): ${e.message}")
        }

        try {
            activeNetworkCallback?.let { callback ->
                connectivityManager.unregisterNetworkCallback(callback)
                activeNetworkCallback = null
            }
        } catch (e: Exception) {
            android.util.Log.e("QrPayloadManager", "Failed to unregisterNetworkCallback: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun autoConnectWifi(
        context: Context,
        ssid: String,
        preSharedKey: String,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return onError("ConnectivityManager unavailable")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            unbindProcessNetwork(context)

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(preSharedKey)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(network)
                    }
                    onConnected()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    unbindProcessNetwork(context)
                    onError("Failed to connect to Wi-Fi hotspot: Timed out or rejected")
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    unbindProcessNetwork(context)
                }
            }

            activeNetworkCallback = networkCallback
            connectivityManager.requestNetwork(request, networkCallback)
        } else {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                onError("WifiManager unavailable")
                return
            }

            @Suppress("DEPRECATION")
            val wifiConfig = WifiConfiguration().apply {
                this.SSID = "\"$ssid\""
                this.preSharedKey = "\"$preSharedKey\""
            }

            @Suppress("DEPRECATION")
            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                onConnected()
            } else {
                onError("Failed to configure Wi-Fi network")
            }
        }
    }
}

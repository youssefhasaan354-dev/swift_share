package com.swiftshare.ui.screens

import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.nft.discovery.DeviceDiscoveryManager
import com.swiftshare.nft.discovery.QrPayloadManager
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.nft.discovery.models.PeerDevice
import com.swiftshare.ui.components.QrCodeDialog
import com.swiftshare.ui.components.RadarScanView
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import java.util.concurrent.Executors

@Composable
fun RadarScanScreen(
    isReceiverMode: Boolean,
    onPeerConnected: (PeerDevice) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val discoveryManager = remember { DeviceDiscoveryManager(context) }
    val discoveredPeers by discoveryManager.allDiscoveredPeers.collectAsState()
    val activePayload by discoveryManager.activePayload.collectAsState()

    var showCameraScanner by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isReceiverMode) {
        if (isReceiverMode) {
            discoveryManager.startReceiverMode(
                deviceName = Build.MODEL,
                avatarId = 1,
                onReady = { showQrDialog = true },
                onError = {}
            )
        } else {
            discoveryManager.startSenderMode()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            discoveryManager.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "←",
                        fontSize = 24.sp,
                        color = TextPrimary,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(end = 16.dp)
                    )
                    Column {
                        Text(
                            text = if (isReceiverMode) strings.waitingToReceive else strings.searchingForPeers,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isReceiverMode) strings.readyForConnection else strings.tapAvatarOrScan,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (!isReceiverMode) {
                    Button(
                        onClick = { showCameraScanner = !showCameraScanner },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (showCameraScanner) strings.radarView else "📷 ${strings.scanQr}", color = AccentCyan)
                    }
                } else {
                    Button(
                        onClick = { showQrDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(strings.showQr, color = TextPrimary)
                    }
                }
            }

            // Radar Canvas or Camera Scanner
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showCameraScanner && !isReceiverMode) {
                    // CameraX QR Scanner View
                    CameraScannerPreview(
                        onBarcodeDetected = { barcodeStr ->
                            val payload = QrPayloadManager.parseQrString(barcodeStr)
                            if (payload != null) {
                                showCameraScanner = false
                                if (payload.ssid == "LAN-WIFI" || (payload.lanIp != null && payload.lanIp == payload.ip)) {
                                    // Direct Same-LAN connect without switching Wi-Fi
                                    val peer = PeerDevice(
                                        id = payload.lanIp ?: payload.ip,
                                        deviceName = payload.deviceName,
                                        ip = payload.lanIp ?: payload.ip,
                                        port = payload.port,
                                        sessionToken = payload.sessionToken
                                    )
                                    onPeerConnected(peer)
                                } else {
                                    QrPayloadManager.autoConnectWifi(
                                        context = context,
                                        ssid = payload.ssid,
                                        preSharedKey = payload.preSharedKey,
                                        onConnected = {
                                            val peer = PeerDevice(
                                                id = payload.ssid,
                                                deviceName = payload.deviceName,
                                                ip = payload.ip,
                                                port = payload.port,
                                                sessionToken = payload.sessionToken
                                            )
                                            onPeerConnected(peer)
                                        },
                                        onError = {
                                            // Fallback to LAN IP if present
                                            if (payload.lanIp != null) {
                                                val peer = PeerDevice(
                                                    id = payload.lanIp,
                                                    deviceName = payload.deviceName,
                                                    ip = payload.lanIp,
                                                    port = payload.port,
                                                    sessionToken = payload.sessionToken
                                                )
                                                onPeerConnected(peer)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                } else {
                    RadarScanView(
                        peers = discoveredPeers,
                        myDeviceName = Build.MODEL,
                        onPeerSelected = { peer ->
                            onPeerConnected(peer)
                        }
                    )
                }
            }

            // Bottom Instructions Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isReceiverMode) "Keep this screen open while sender connects" else "Found ${discoveredPeers.size} nearby device(s)",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        // Receiver QR Code Dialog
        if (showQrDialog && activePayload != null) {
            QrCodeDialog(
                payload = activePayload!!,
                onDismiss = { showQrDialog = false }
            )
        }
    }
}

@Composable
fun CameraScannerPreview(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QrPayloadManager.BarcodeAnalyzer { result ->
                            onBarcodeDetected(result)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        ctx as androidx.lifecycle.LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

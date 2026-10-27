package com.swiftshare.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.swiftshare.common.AppLogger
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.NetworkUtils
import com.swiftshare.nft.discovery.LocalHotspotManager
import com.swiftshare.ui.components.QrCodeDialog
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.ElectricPurple
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import com.swiftshare.webshare.EmbeddedWebShareServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun InviteScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var isPreparingBluetoothApk by remember { mutableStateOf(false) }
    var isHotspotActive by remember { mutableStateOf(false) }
    var hotspotSsid by remember { mutableStateOf("") }
    var hotspotPassword by remember { mutableStateOf("") }
    var inviteUrl by remember { mutableStateOf("") }
    var showInviteQrDialog by remember { mutableStateOf(false) }

    val hotspotManager = remember { LocalHotspotManager(context) }
    val webServer = remember { EmbeddedWebShareServer(context) }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // After user handles bluetooth enable prompt, initiate send
        scope.launch(Dispatchers.IO) {
            shareApkViaBluetooth(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isHotspotActive) {
                hotspotManager.stopHotspot()
                webServer.stop()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    text = strings.inviteFriendsTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Share SwiftShare installer without requiring internet",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Option 1: Bluetooth Direct APK Share
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📡", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.inviteBluetooth,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = strings.inviteBluetoothDesc,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val btAdapter = BluetoothAdapter.getDefaultAdapter()
                        if (btAdapter == null) {
                            Toast.makeText(context, strings.bluetoothNotSupported, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (!btAdapter.isEnabled) {
                            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } else {
                            isPreparingBluetoothApk = true
                            scope.launch(Dispatchers.IO) {
                                shareApkViaBluetooth(context)
                                withContext(Dispatchers.Main) {
                                    isPreparingBluetoothApk = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isPreparingBluetoothApk
                ) {
                    if (isPreparingBluetoothApk) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.preparingApk, fontSize = 13.sp)
                    } else {
                        Text("Send APK via Bluetooth", fontSize = 13.sp, color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Option 2: Zero-Data Hotspot Invite Web Portal
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isHotspotActive) AccentCyan else SurfaceBorderDark,
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricPurple, AccentCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.inviteZeroData,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = strings.inviteZeroDataDesc,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isHotspotActive) {
                    Button(
                        onClick = {
                            hotspotManager.startLocalOnlyHotspot(
                                onSuccess = { config ->
                                    isHotspotActive = true
                                    hotspotSsid = config.ssid
                                    hotspotPassword = config.preSharedKey
                                    inviteUrl = "http://${config.ip}:8080/invite"
                                    webServer.start(port = 8080)
                                },
                                onFailure = { errorMsg ->
                                    // Fallback to local Wi-Fi IP
                                    val localIp = NetworkUtils.getLocalIpAddress(context).ifBlank { "192.168.49.1" }
                                    isHotspotActive = true
                                    hotspotSsid = "Same Wi-Fi Network"
                                    hotspotPassword = "(Already connected)"
                                    inviteUrl = "http://$localIp:8080/invite"
                                    webServer.start(port = 8080)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Zero-Data Invite Portal", fontSize = 13.sp, color = TextPrimary)
                    }
                } else {
                    // Hotspot Active Card Info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .padding(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Network (SSID):", fontSize = 11.sp, color = TextSecondary)
                            Text(hotspotSsid, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Password:", fontSize = 11.sp, color = TextSecondary)
                            Text(hotspotPassword, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Invite URL:", fontSize = 11.sp, color = TextSecondary)
                            Text(inviteUrl, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showInviteQrDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("📷 Show QR", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                hotspotManager.stopHotspot()
                                webServer.stop()
                                isHotspotActive = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Stop Portal", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Option 3: System Share-Sheet Fallback
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(SurfaceBorderDark, AccentCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↗", fontSize = 20.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.inviteShareSheet,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = strings.inviteShareSheetDesc,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        shareApkViaSystemChooser(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open System Share Sheet", fontSize = 13.sp, color = TextPrimary)
                }
            }
        }
    }

    // QR Code Dialog for Zero-Data Web Portal
    if (showInviteQrDialog) {
        val payload = com.swiftshare.nft.discovery.models.ConnectionPayload(
            ssid = hotspotSsid,
            preSharedKey = hotspotPassword,
            ip = inviteUrl,
            port = 8080,
            sessionToken = "zero_data_public_invite",
            deviceName = "SwiftShare Invite Portal"
        )
        QrCodeDialog(
            payload = payload,
            onDismiss = { showInviteQrDialog = false }
        )
    }
}

/**
 * Copies the installed APK to cache and dispatches via ACTION_SEND targeted at system Bluetooth.
 */
private fun shareApkViaBluetooth(context: Context) {
    try {
        val apkFile = copyInstalledApkToCache(context)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Target Bluetooth package directly
        val pm = context.packageManager
        val matches = pm.queryIntentActivities(intent, 0)
        var targeted = false
        for (info in matches) {
            val pkg = info.activityInfo.packageName
            if (pkg.contains("bluetooth", ignoreCase = true)) {
                intent.setPackage(pkg)
                targeted = true
                break
            }
        }

        if (targeted) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val chooser = Intent.createChooser(intent, "Send SwiftShare via Bluetooth").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        AppLogger.e("InviteScreen", "Failed to share APK via Bluetooth: ${e.message}", e, notifyUser = true)
    }
}

/**
 * Copies installed APK and opens the system chooser.
 */
private fun shareApkViaSystemChooser(context: Context) {
    try {
        val apkFile = copyInstalledApkToCache(context)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_TEXT, "Install SwiftShare for super-fast offline file sharing!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Share SwiftShare").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        AppLogger.e("InviteScreen", "Failed to share APK via chooser: ${e.message}", e, notifyUser = true)
    }
}

/**
 * Helper to copy the currently running application APK to the cache directory as SwiftShare.apk.
 */
private fun copyInstalledApkToCache(context: Context): File {
    val cacheApk = File(context.cacheDir, "SwiftShare.apk")
    val sourcePath = context.applicationInfo.sourceDir
    if (sourcePath != null) {
        val sourceFile = File(sourcePath)
        if (sourceFile.exists()) {
            if (!cacheApk.exists() || cacheApk.length() != sourceFile.length()) {
                sourceFile.copyTo(cacheApk, overwrite = true)
            }
            return cacheApk
        }
    }
    if (!cacheApk.exists()) {
        cacheApk.writeText("SwiftShare APK Payload Placeholder")
    }
    return cacheApk
}

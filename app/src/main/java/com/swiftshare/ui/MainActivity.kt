package com.swiftshare.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swiftshare.common.AppLanguage
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.PermissionManager
import com.swiftshare.explorer.MediaItem
import com.swiftshare.service.TransferForegroundService
import com.swiftshare.ui.screens.CleanerScreen
import com.swiftshare.ui.screens.FilePickerScreen
import com.swiftshare.ui.screens.GroupShareScreen
import com.swiftshare.ui.screens.HomeScreen
import com.swiftshare.ui.screens.InviteScreen
import com.swiftshare.ui.screens.PermissionGuideScreen
import com.swiftshare.ui.screens.MediaPlayerScreen
import com.swiftshare.ui.screens.PcConnectScreen
import com.swiftshare.ui.screens.PhoneCloneScreen
import com.swiftshare.ui.screens.RadarScanScreen
import com.swiftshare.ui.screens.SafeBoxScreen
import com.swiftshare.ui.screens.StatusSaverScreen
import com.swiftshare.ui.screens.TransferHistoryScreen
import com.swiftshare.ui.screens.TransferProgressScreen
import com.swiftshare.ui.screens.VideoConverterScreen
import com.swiftshare.ui.screens.WebShareScreen
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.SwiftShareTheme
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : FragmentActivity() {

    private val viewModel: SwiftShareViewModel by viewModels()
    private val pendingRoute = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val missing = PermissionManager.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        handleIncomingShareIntent(intent)

        setContent {
            val currentLang by LocaleManager.currentLanguage.collectAsState()
            val layoutDirection = if (currentLang == AppLanguage.AR) {
                androidx.compose.ui.unit.LayoutDirection.Rtl
            } else {
                androidx.compose.ui.unit.LayoutDirection.Ltr
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalAppStrings provides LocaleManager.getStrings()
            ) {
                SwiftShareTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = BackgroundDark
                    ) {
                        SwiftShareNavHost(viewModel, pendingRoute.value) {
                            pendingRoute.value = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action ?: return
        val uris = mutableListOf<Uri>()

        if (Intent.ACTION_SEND == action) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            streamUri?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            streamUris?.let { uris.addAll(it) }
        }

        if (uris.isNotEmpty()) {
            val items = mutableListOf<MediaItem>()
            for (uri in uris) {
                var fileName = "shared_file_${System.currentTimeMillis()}"
                var fileSize = 0L
                val mimeType = contentResolver.getType(uri) ?: "*/*"

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                            if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                } catch (_: Exception) {}

                // Copy stream to cache file for fast NIO binary stream transfer
                val cacheFile = File(cacheDir, "share_intent_$fileName")
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (fileSize == 0L) fileSize = cacheFile.length()
                } catch (_: Exception) {}

                items.add(
                    MediaItem(
                        id = UUID.randomUUID().toString(),
                        name = fileName,
                        path = cacheFile.absolutePath,
                        size = fileSize,
                        mimeType = mimeType,
                        category = "docs",
                        thumbnailUri = uri.toString()
                    )
                )
            }

            viewModel.setSelectedFiles(items)
            pendingRoute.value = "radar_send"
        }
    }

    @Composable
    private fun SwiftShareNavHost(
        vm: SwiftShareViewModel,
        initialRoute: String?,
        onRouteConsumed: () -> Unit
    ) {
        val navController = rememberNavController()
        var activeMediaUri by remember { mutableStateOf<Uri?>(null) }
        val activePeer by vm.activePeer.collectAsState()
        val strings = LocalAppStrings.current

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Handle incoming system share intent route
        LaunchedEffect(initialRoute) {
            if (initialRoute != null) {
                navController.navigate(initialRoute)
                onRouteConsumed()
            }
        }

        val mainTabs = listOf(
            NavTabItem("home", strings.navTransfer, "⚡"),
            NavTabItem("file_picker", strings.navFiles, "📂"),
            NavTabItem("media_player", strings.navMedia, "🎬"),
            NavTabItem("transfer_history", strings.navHistory, "📋")
        )

        val showBottomNav = currentRoute in setOf("home", "file_picker", "media_player", "transfer_history")

        Scaffold(
            bottomBar = {
                if (showBottomNav) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(SurfaceDark)
                            .border(1.dp, SurfaceBorderDark)
                            .padding(vertical = 6.dp, horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            mainTabs.forEach { tab ->
                                val isSelected = currentRoute == tab.route
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (currentRoute != tab.route) {
                                                navController.navigate(tab.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tab.icon,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            val startDest = if (!PermissionManager.hasCompletedGuide(this@MainActivity)) "permission_guide" else "home"
            NavHost(
                navController = navController,
                startDestination = startDest,
                modifier = Modifier.padding(innerPadding)
            ) {
                // 0. Permission Onboarding Guide
                composable("permission_guide") {
                    PermissionGuideScreen(
                        onFinish = {
                            navController.navigate("home") {
                                popUpTo("permission_guide") { inclusive = true }
                            }
                        }
                    )
                }

                // 1. Home Dashboard
                composable("home") {
                    HomeScreen(
                        viewModel = vm,
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }

                // 2. 6-Tab Content Picker & Explorer
                composable("file_picker") {
                    FilePickerScreen(
                        onFilesSelected = { selected ->
                            vm.setSelectedFiles(selected)
                            navController.navigate("radar_send")
                        },
                        onBack = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate("home")
                            }
                        }
                    )
                }

                // 3. Radar Sender Mode
                composable("radar_send") {
                    RadarScanScreen(
                        viewModel = vm,
                        isReceiverMode = false,
                        onPeerConnected = { peer ->
                            vm.connectAndSendFiles(peer)
                            navController.navigate("transfer_progress")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 4. Radar Receiver Mode (Hotspot / Group Owner + QR)
                composable("radar_receive") {
                    RadarScanScreen(
                        viewModel = vm,
                        isReceiverMode = true,
                        onPeerConnected = { peer ->
                            vm.activePeer.value = peer
                            TransferForegroundService.startService(this@MainActivity, "Receiving files...", 0L)
                            navController.navigate("transfer_progress")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 5. Real-Time Transfer Telemetry Dashboard
                composable("transfer_progress") {
                    TransferProgressScreen(
                        dataChannel = vm.dataChannel,
                        peerDeviceName = activePeer?.deviceName ?: "Peer Device",
                        onFinish = {
                            TransferForegroundService.stopService(this@MainActivity)
                            navController.navigate("transfer_result") {
                                popUpTo("transfer_progress") { inclusive = true }
                            }
                        }
                    )
                }

                // 5b. Transfer Result Screen (Retry, Open, Send more, Done)
                composable("transfer_result") {
                    val result = vm.lastTransferResult.collectAsState().value
                    val progress = vm.dataChannel.transferProgress.collectAsState().value
                    val selectedFiles = vm.selectedSendFiles.collectAsState().value
                    val isSuccess = result?.isSuccess ?: (progress.status == com.swiftshare.nft.channel.models.FileTransferStatus.COMPLETED)
                    val filesCount = result?.fileCount ?: selectedFiles.size.coerceAtLeast(1)
                    val totalBytes = result?.totalBytes ?: progress.batchTotalBytes
                    val peerName = result?.peerName ?: activePeer?.deviceName ?: "Peer Device"

                    com.swiftshare.ui.screens.TransferResultScreen(
                        batchId = result?.batchId ?: "session_${System.currentTimeMillis()}",
                        isSuccess = isSuccess,
                        fileCount = filesCount,
                        totalBytes = totalBytes,
                        peerName = peerName,
                        onRetry = {
                            navController.popBackStack()
                        },
                        onOpen = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + "/SwiftShare"),
                                        "resource/folder"
                                    )
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        onSendMore = {
                            navController.navigate("file_picker") {
                                popUpTo("home")
                            }
                        },
                        onDone = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                }

                // 6. Phone-to-Browser WebShare
                composable("webshare") {
                    WebShareScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 7. Connect to PC Desktop Suite & Remote View
                composable("pc_connect") {
                    PcConnectScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 8. Multi-Peer Group Share Room
                composable("group_share") {
                    GroupShareScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 9. 1-Click Phone Clone Migration
                composable("phone_clone") {
                    PhoneCloneScreen(
                        onStartClone = { items, isOldPhone ->
                            if (isOldPhone) {
                                vm.setSelectedFiles(items)
                                navController.navigate("radar_send")
                            } else {
                                navController.navigate("radar_receive")
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 10. Hardware-Backed AES-256-GCM SafeBox Vault
                composable("safebox") {
                    SafeBoxScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 11. Junk & Cache Cleaner (CleanIt)
                composable("cleaner") {
                    CleanerScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 12. WhatsApp Status Saver
                composable("status_saver") {
                    StatusSaverScreen(
                        onShareStatus = { status ->
                            navController.navigate("radar_send")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 13. Video-to-Audio Extractor
                composable("video_converter") {
                    VideoConverterScreen(
                        onPlayAudio = { audioUri ->
                            activeMediaUri = audioUri
                            navController.navigate("media_player")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 14. Transfer History & Me Profile
                composable("transfer_history") {
                    TransferHistoryScreen(
                        viewModel = vm,
                        onNavigate = { route -> navController.navigate(route) },
                        onBack = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate("home")
                            }
                        }
                    )
                }

                // 15. Built-in Media Player & Media Hub
                composable("media_player") {
                    MediaPlayerScreen(
                        mediaUri = activeMediaUri,
                        onConvertToMp3 = { vidUri ->
                            activeMediaUri = vidUri
                            navController.navigate("video_converter")
                        },
                        onBack = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate("home")
                            }
                        }
                    )
                }

                // 16. Invite Friends (Bluetooth, Zero-Data LOHS, Share Sheet)
                composable("invite") {
                    InviteScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private data class NavTabItem(
    val route: String,
    val title: String,
    val icon: String
)

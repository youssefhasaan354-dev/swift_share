package com.swiftshare.ui.screens

import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.swiftshare.common.Constants
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.LocaleManager
import com.swiftshare.common.NetworkUtils
import com.swiftshare.groupshare.GroupShareEngine
import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.discovery.DeviceDiscoveryManager
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.nft.discovery.models.PeerDevice
import com.swiftshare.ui.components.QrCodeDialog
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun GroupShareScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val myDeviceName by LocaleManager.deviceName.collectAsState()
    val scope = rememberCoroutineScope()

    val dataChannel = remember { HighSpeedDataChannel() }
    val groupEngine = remember { GroupShareEngine(dataChannel) }
    val currentRoom by groupEngine.currentRoom.collectAsState()
    val broadcastStatus by groupEngine.broadcastStatus.collectAsState()

    val discoveryManager = remember { DeviceDiscoveryManager(context) }
    val discoveredPeers by discoveryManager.allDiscoveredPeers.collectAsState()
    val activePayload by discoveryManager.activePayload.collectAsState()

    var showQrDialog by remember { mutableStateOf(false) }
    var selectedModeTab by remember { mutableIntStateOf(0) } // 0: Host Room, 1: Join Room

    val myDevice = remember(myDeviceName) {
        PeerDevice(
            id = "group_node_" + System.currentTimeMillis(),
            deviceName = myDeviceName,
            ip = NetworkUtils.getLocalIpAddress(context).ifBlank { "192.168.49.1" }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            discoveryManager.stop()
            groupEngine.leaveRoom()
        }
    }

    // File picker for room broadcast
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                for (uri in uris) {
                    var fileName = "shared_file"
                    var fileSize = 0L
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx != -1) fileName = c.getString(nameIdx) ?: "shared_file"
                            if (sizeIdx != -1) fileSize = c.getLong(sizeIdx)
                        }
                    }

                    val cacheFile = File(context.cacheDir, "broadcast_${System.currentTimeMillis()}_$fileName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (fileSize == 0L) fileSize = cacheFile.length()

                    val manifestItem = FileManifestItem(
                        fileId = UUID.randomUUID().toString(),
                        name = fileName,
                        size = fileSize,
                        mimeType = context.contentResolver.getType(uri) ?: "*/*",
                        relativePath = fileName,
                        sha256 = ""
                    )

                    groupEngine.broadcastFileToAllMembers(cacheFile, manifestItem)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(18.dp)
    ) {
        // Top Bar
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
            Text(
                text = strings.groupShareTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentRoom == null) {
            // Mode Selector Tabs (Host vs Join)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .padding(4.dp)
            ) {
                listOf(strings.groupCreate, strings.groupJoin).forEachIndexed { index, title ->
                    val isSelected = selectedModeTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrimaryBlue else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                selectedModeTab = index
                                if (index == 1) {
                                    discoveryManager.startSenderMode()
                                } else {
                                    discoveryManager.stop()
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedModeTab == 0) {
                // Mode 1: Host a Room
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👑", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Host a Group Share Room",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.groupDesc,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                groupEngine.createRoom(myDevice, "${myDevice.deviceName}'s Room")
                                discoveryManager.startReceiverMode(
                                    deviceName = myDevice.deviceName,
                                    avatarId = 1,
                                    onReady = { showQrDialog = true },
                                    onError = {}
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Create & Open Room", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Mode 2: Join a Room (Discovery)
                Text(
                    text = "Nearby Active Rooms & Hosts",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (discoveredPeers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Searching for room hosts on Wi-Fi/Hotspot…",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(discoveredPeers) { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceCard)
                                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
                                    .clickable {
                                        groupEngine.joinRoom(peer, myDevice)
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🏠", fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        peer.deviceName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "IP: ${peer.ip}:${peer.port}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Button(
                                    onClick = { groupEngine.joinRoom(peer, myDevice) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Join", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val room = currentRoom!!
            // Active Room View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(room.roomName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("${room.members.size} connected member(s)", fontSize = 12.sp, color = AccentCyan)
                    }
                    Row {
                        if (room.isHost) {
                            Button(
                                onClick = { showQrDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).padding(end = 6.dp)
                            ) {
                                Text("Room QR", fontSize = 11.sp)
                            }
                        }
                        Button(
                            onClick = {
                                groupEngine.leaveRoom()
                                discoveryManager.stop()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(strings.groupLeave, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (broadcastStatus != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(broadcastStatus!!, fontSize = 11.sp, color = AccentCyan)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(strings.groupMembers, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(room.members) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member.deviceName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(member.deviceName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text(
                                if (member.id == room.hostDevice.id) "Portal Host (Group Owner)" else "Room Member",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (room.sharedFiles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Shared in Room (${room.sharedFiles.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    items(room.sharedFiles) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📄", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(file.name, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(NetworkUtils.formatBytes(file.size), fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button: Select Files to Broadcast
            Button(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(strings.groupBroadcastBtn, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showQrDialog && activePayload != null) {
        QrCodeDialog(
            payload = activePayload!!,
            onDismiss = { showQrDialog = false }
        )
    }
}

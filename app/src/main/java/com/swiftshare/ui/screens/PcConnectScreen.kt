package com.swiftshare.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.nft.discovery.QrPayloadManager
import com.swiftshare.pc.PcRemoteServer
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun PcConnectScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepo = remember { MediaStoreRepository(context) }
    val pcServer = remember { PcRemoteServer(context, mediaRepo) }
    val token = remember { NetworkUtils.generateSessionToken() }
    val localIp = remember { NetworkUtils.getLocalIpAddress(context) }
    val pcUrl = "http://$localIp:${Constants.PORT_WEB_PC}/pc?token=$token"

    val events = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        pcServer.start(sessionToken = token)
        pcServer.pptEvents.collect { evt ->
            events.add(0, "Event: ${evt.action} ${if (evt.action == "LASER_MOVE") "(X:${"%.2f".format(evt.laserX)}, Y:${"%.2f".format(evt.laserY)})" else ""}")
            if (events.size > 8) events.removeAt(events.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pcServer.stop()
        }
    }

    val qrBitmap = remember(pcUrl) {
        QrPayloadManager.generateQrCode(pcUrl, size = 480)
    }

    val strings = com.swiftshare.common.LocalAppStrings.current
    val isArabic = strings.appName.contains("سويفت")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
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
                text = if (isArabic) "الاتصال بالحاسوب (Connect to PC)" else "Connect to PC",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PC Instructions Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Open this link on your Desktop / Laptop browser:",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = pcUrl,
                    fontSize = 13.sp,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Features: Remote View, PlayTo screen cast, Drag & drop file transfer, PPT Remote controller",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // QR Code Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "PC Connect QR Code",
                    modifier = Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scan with phone or open browser directly on PC",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Live Telemetry / Remote Event Log
        if (events.isNotEmpty()) {
            Text("Live PC Remote Events", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                events.take(3).forEach { ev ->
                    Text(ev, fontSize = 11.sp, color = AccentCyan)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Disconnect PC Suite", fontWeight = FontWeight.Bold)
        }
    }
}

package com.swiftshare.ui.screens

import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.nft.discovery.QrPayloadManager
import com.swiftshare.webshare.EmbeddedWebShareServer
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun WebShareScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val webServer = remember { EmbeddedWebShareServer(context) }
    val sessionToken by webServer.activeSessionToken.collectAsState()
    val localIp = remember { NetworkUtils.getLocalIpAddress(context) }
    val webUrl = "http://$localIp:${Constants.PORT_WEB_PC}/webshare?token=$sessionToken"

    LaunchedEffect(Unit) {
        webServer.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            webServer.stop()
        }
    }

    val qrBitmap = remember(webUrl) {
        QrPayloadManager.generateQrCode(webUrl, size = 480)
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
                text = if (isArabic) "مشاركة الويب عبر المتصفح (WebShare)" else "WebShare Browser Portal",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Steps instructions card
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
                    text = if (isArabic) "1. اتصل بنقطة الاتصال الخاصة بهذا الهاتف" else "1. Connect receiver device to this phone's Wi-Fi hotspot",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isArabic) "2. افتح أي متصفح وامسح الرمز أو ادخل الرابط:" else "2. Open any web browser and scan the QR code or visit:",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = webUrl,
                    fontSize = 13.sp,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // QR Code Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "WebShare QR Code",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No installation required on receiver",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Zero-Data APK Invite Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📦", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Zero-Data APK Invite Active", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                    Text("Friends can download SwiftShare APK at /invite", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Stop WebShare Portal", fontWeight = FontWeight.Bold)
        }
    }
}

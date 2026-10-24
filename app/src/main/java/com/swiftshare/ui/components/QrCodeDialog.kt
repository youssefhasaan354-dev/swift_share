package com.swiftshare.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swiftshare.nft.discovery.QrPayloadManager
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun QrCodeDialog(
    payload: ConnectionPayload,
    onDismiss: () -> Unit
) {
    var selectedQrType by remember { mutableIntStateOf(0) } // 0: App QR, 1: Wi-Fi QR, 2: WebShare URL

    val qrContent = remember(payload, selectedQrType) {
        when (selectedQrType) {
            0 -> payload.toJson()
            1 -> "WIFI:T:WPA;S:${payload.ssid};P:${payload.preSharedKey};;"
            2 -> "http://${payload.lanIp ?: payload.ip}:${payload.port}"
            else -> payload.toJson()
        }
    }

    val qrBitmap = remember(qrContent) {
        QrPayloadManager.generateQrCode(qrContent, size = 512)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Dual-QR Connect",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Point sender's camera or standard camera at QR",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // QR Type Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("App QR", "Wi-Fi QR", "WebShare").forEachIndexed { index, title ->
                    val isSelected = selectedQrType == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryBlue else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedQrType = index }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Connection QR Code",
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            val infoText = when (selectedQrType) {
                0 -> "SwiftShare Protocol (Instant Direct P2P)\nHotspot: ${payload.ip} | LAN: ${payload.lanIp ?: "N/A"}"
                1 -> "Standard Wi-Fi QR for iOS / Android Camera\nSSID: ${payload.ssid} | Pass: ${payload.preSharedKey}"
                2 -> "Browser Direct Link\nhttp://${payload.lanIp ?: payload.ip}:${payload.port}"
                else -> ""
            }

            Text(
                text = infoText,
                fontSize = 11.sp,
                color = AccentCyan,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

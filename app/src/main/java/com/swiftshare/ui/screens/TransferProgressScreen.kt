package com.swiftshare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.NetworkUtils
import com.swiftshare.nft.channel.HighSpeedDataChannel
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.ui.components.SpeedGraphView
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.DangerRed
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SuccessGreen
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun TransferProgressScreen(
    dataChannel: HighSpeedDataChannel,
    peerDeviceName: String,
    onFinish: () -> Unit
) {
    val progress by dataChannel.transferProgress.collectAsState()
    val speedHistory = remember { mutableStateListOf<Long>() }

    LaunchedEffect(progress.speedBytesPerSec) {
        if (progress.speedBytesPerSec > 0) {
            speedHistory.add(progress.speedBytesPerSec)
            if (speedHistory.size > 30) {
                speedHistory.removeAt(0)
            }
        }
    }

    val strings = com.swiftshare.common.LocalAppStrings.current
    val isArabic = strings.appName.contains("سويفت")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (progress.status == FileTransferStatus.COMPLETED) {
                        if (isArabic) "اكتمل النقل بنجاح" else "Transfer Completed"
                    } else {
                        if (isArabic) "جارٍ نقل الملفات…" else "Transferring Files"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (isArabic) "متصل بـ $peerDeviceName" else "Connected to $peerDeviceName",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (progress.status == FileTransferStatus.COMPLETED) SuccessGreen else AccentCyan)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Big Speed & ETA Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(SurfaceDark, SurfaceCard)))
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "CURRENT SPEED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = NetworkUtils.formatSpeed(progress.speedBytesPerSec),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentCyan
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ESTIMATED TIME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        val etaStr = if (progress.etaSeconds > 0) "${progress.etaSeconds}s remaining" else "--"
                        Text(
                            text = etaStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress.overallProgressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = AccentCyan,
                    trackColor = SurfaceBorderDark,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progress.overallProgressPercent * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${NetworkUtils.formatBytes(progress.totalBytesTransferred)} / ${NetworkUtils.formatBytes(progress.batchTotalBytes)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Real-Time Canvas Speed Graph
        Text(
            text = "Throughput Telemetry",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SpeedGraphView(
            speedHistory = speedHistory,
            currentSpeed = progress.speedBytesPerSec
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Current Active File Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceBorderDark),
                    contentAlignment = Alignment.Center
                ) {
                    val liveThumbBitmap = remember(progress.thumbnailBase64) {
                        progress.thumbnailBase64?.let { b64 ->
                            try {
                                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }

                    if (liveThumbBitmap != null) {
                        Image(
                            bitmap = liveThumbBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Text("📄", fontSize = 22.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.currentFileName.ifBlank { "Preparing file..." },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (progress.status == FileTransferStatus.COMPLETED) "Finished" else "Transferring via NIO zero-copy channel",
                        fontSize = 11.sp,
                        color = if (progress.status == FileTransferStatus.COMPLETED) SuccessGreen else AccentCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Action Button
        Button(
            onClick = {
                if (progress.status != FileTransferStatus.COMPLETED) {
                    dataChannel.cancel()
                }
                onFinish()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (progress.status == FileTransferStatus.COMPLETED) PrimaryBlue else DangerRed.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = if (progress.status == FileTransferStatus.COMPLETED) {
                    if (isArabic) "تم" else "Done"
                } else {
                    if (isArabic) "إلغاء النقل" else "Cancel Transfer"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

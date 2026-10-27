package com.swiftshare.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.NetworkUtils
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
fun TransferResultScreen(
    batchId: String,
    isSuccess: Boolean,
    fileCount: Int,
    totalBytes: Long,
    peerName: String,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onSendMore: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status Icon Circle
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (isSuccess) SuccessGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f))
                .border(2.dp, if (isSuccess) SuccessGreen else DangerRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSuccess) "✓" else "✕",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) SuccessGreen else DangerRed
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = if (isSuccess) "Transfer Completed" else "Transfer Interrupted",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSuccess) "Files successfully transferred with $peerName" else "Transfer with $peerName did not complete",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(SurfaceDark, SurfaceCard)))
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Files", fontSize = 13.sp, color = TextSecondary)
                    Text("$fileCount files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Size", fontSize = 13.sp, color = TextSecondary)
                    Text(NetworkUtils.formatBytes(totalBytes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Session ID", fontSize = 13.sp, color = TextSecondary)
                    Text(batchId.take(16), fontSize = 12.sp, color = TextSecondary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Status", fontSize = 13.sp, color = TextSecondary)
                    Text(
                        text = if (isSuccess) "COMPLETED" else "FAILED",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccess) SuccessGreen else DangerRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons: Open, Retry, Send More, Done
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Open Button (Primary action on success)
            if (isSuccess) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Open Folder / Files", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Retry Button (Primary action on failure)
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Retry Transfer", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Send More Button
            OutlinedButton(
                onClick = onSendMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AccentCyan, PrimaryBlue)))
            ) {
                Text("Send More Files", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
            }

            // Done / Home Button
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
            ) {
                Text("Done", fontSize = 14.sp, color = TextSecondary)
            }
        }
    }
}

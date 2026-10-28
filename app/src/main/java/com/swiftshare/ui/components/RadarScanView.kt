package com.swiftshare.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.nft.discovery.models.PeerDevice
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScanView(
    modifier: Modifier = Modifier,
    peers: List<PeerDevice>,
    myDeviceName: String = "You",
    onPeerSelected: (PeerDevice) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarAnimation")

    // Continuous 360-degree sweep angle
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    // Pulsing radar ripple expansion
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleScale"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Custom 60fps Radar Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (size.minDimension / 2f) * 0.85f

            // 1. Concentric circles
            val circleSteps = 4
            for (i in 1..circleSteps) {
                val radius = maxRadius * (i.toFloat() / circleSteps)
                drawCircle(
                    color = SurfaceBorderDark.copy(alpha = 0.6f),
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Animated Expanding Ripple Ring
            val rippleRadius = maxRadius * rippleScale
            drawCircle(
                color = AccentCyan.copy(alpha = (1f - rippleScale) * 0.5f),
                radius = rippleRadius,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Rotating Gradient Sweep Arm
            rotate(degrees = sweepAngle, pivot = centerOffset) {
                val sweepBrush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.7f to Color.Transparent,
                    0.95f to AccentCyan.copy(alpha = 0.25f),
                    1.0f to PrimaryBlue.copy(alpha = 0.6f),
                    center = centerOffset
                )
                drawCircle(
                    brush = sweepBrush,
                    radius = maxRadius,
                    center = centerOffset
                )
                // Leading sweep beam line
                drawLine(
                    color = AccentCyan,
                    start = centerOffset,
                    end = Offset(centerOffset.x + maxRadius, centerOffset.y),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Center Avatar (Current User Device)
        val myAvatarEmoji by com.swiftshare.common.LocaleManager.avatarEmoji.collectAsState()
        val myCustomAvatar by com.swiftshare.common.LocaleManager.customAvatarBase64.collectAsState()
        val myCustomBitmap = remember(myCustomAvatar) {
            myCustomAvatar?.let { com.swiftshare.common.AvatarUtils.decodeBase64ToBitmap(it) }
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PrimaryBlue, AccentCyan)))
                .border(2.dp, TextPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (myCustomBitmap != null) {
                Image(
                    bitmap = myCustomBitmap.asImageBitmap(),
                    contentDescription = "My Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = myAvatarEmoji,
                    fontSize = 24.sp
                )
            }
        }

        // Orbiting Discovered Peers
        peers.forEachIndexed { index, peer ->
            val angleDeg = (index * (360f / peers.size.coerceAtLeast(1)) + 45f)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val orbitRadius = 130.dp // distance from center
            val peerBitmap = remember(peer.customAvatarBase64) {
                peer.customAvatarBase64?.let { com.swiftshare.common.AvatarUtils.decodeBase64ToBitmap(it) }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (orbitRadius.toPx() * cos(angleRad)).toInt(),
                            y = (orbitRadius.toPx() * sin(angleRad)).toInt()
                        )
                    }
                    .clickable { onPeerSelected(peer) }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.9f))
                        .border(2.dp, AccentCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (peerBitmap != null) {
                        Image(
                            bitmap = peerBitmap.asImageBitmap(),
                            contentDescription = peer.deviceName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!peer.avatarEmoji.isNullOrBlank()) {
                        Text(
                            text = peer.avatarEmoji,
                            fontSize = 22.sp
                        )
                    } else {
                        Text(
                            text = peer.deviceName.take(2).uppercase(),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Device type badge (Android / iOS / PC / Web)
                    val devIcon = when (peer.deviceType.lowercase()) {
                        "ios" -> "🍏"
                        "pc" -> "💻"
                        "web" -> "🌐"
                        else -> "📱"
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, AccentCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = devIcon, fontSize = 9.sp)
                    }
                }

                Text(
                    text = peer.deviceName.take(8),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

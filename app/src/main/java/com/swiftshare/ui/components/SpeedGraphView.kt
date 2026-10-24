package com.swiftshare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftshare.common.NetworkUtils
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextSecondary

@Composable
fun SpeedGraphView(
    modifier: Modifier = Modifier,
    speedHistory: List<Long>, // bytes per second samples
    currentSpeed: Long
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        val maxSpeed = (speedHistory.maxOrNull() ?: 1L).coerceAtLeast(1024 * 1024L) // at least 1 MB/s scale

        Canvas(modifier = Modifier.matchParentSize().padding(top = 20.dp, bottom = 4.dp)) {
            if (speedHistory.size < 2) return@Canvas

            val width = size.width
            val height = size.height
            val stepX = width / (speedHistory.size - 1)

            val path = Path()
            val fillPath = Path()

            speedHistory.forEachIndexed { i, speed ->
                val x = i * stepX
                val normalizedY = (speed.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                val y = height - (normalizedY * height)

                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw gradient fill under graph
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AccentCyan.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw main speed stroke
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(PrimaryBlue, AccentCyan)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Speed Labels
        Text(
            text = "Peak: " + NetworkUtils.formatSpeed(maxSpeed),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = NetworkUtils.formatSpeed(currentSpeed),
            color = AccentCyan,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

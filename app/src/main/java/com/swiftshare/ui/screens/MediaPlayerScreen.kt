package com.swiftshare.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swiftshare.common.LocalAppStrings
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaItem
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.siplayer.SwiftMediaPlayer
import com.swiftshare.ui.theme.AccentCyan
import com.swiftshare.ui.theme.BackgroundDark
import com.swiftshare.ui.theme.ElectricPurple
import com.swiftshare.ui.theme.PrimaryBlue
import com.swiftshare.ui.theme.SurfaceBorderDark
import com.swiftshare.ui.theme.SurfaceCard
import com.swiftshare.ui.theme.SurfaceDark
import com.swiftshare.ui.theme.TextPrimary
import com.swiftshare.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaPlayerScreen(
    mediaUri: Uri?,
    mediaTitle: String = "Media Playback",
    onConvertToMp3: ((Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val repo = remember { MediaStoreRepository(context) }
    val player = remember { SwiftMediaPlayer(context) }

    val isPlaying by player.isPlaying.collectAsState()
    val position by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()

    var activeUri by remember { mutableStateOf(mediaUri) }
    var currentTitle by remember { mutableStateOf(mediaTitle) }
    var selectedMediaTab by remember { mutableIntStateOf(0) } // 0: Videos, 1: Music

    val videoItems = remember { mutableStateListOf<MediaItem>() }
    val musicItems = remember { mutableStateListOf<MediaItem>() }

    // Load actual library videos & music
    LaunchedEffect(Unit) {
        val vids = withContext(Dispatchers.IO) { repo.getVideos() }
        videoItems.clear()
        videoItems.addAll(vids)

        val music = withContext(Dispatchers.IO) { repo.getAudio() }
        musicItems.clear()
        musicItems.addAll(music)
    }

    LaunchedEffect(activeUri) {
        if (activeUri != null) {
            player.play(activeUri!!)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
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
                    .clickable {
                        if (activeUri != null && mediaUri == null) {
                            player.pause()
                            activeUri = null
                        } else {
                            onBack()
                        }
                    }
                    .padding(end = 16.dp)
            )
            Text(
                text = if (activeUri != null) currentTitle else strings.mediaHubTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeUri == null) {
            // ── Media Library Browser Mode ──────────────────────────────────
            TabRow(
                selectedTabIndex = selectedMediaTab,
                containerColor = BackgroundDark,
                contentColor = AccentCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedMediaTab]),
                        color = AccentCyan,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedMediaTab == 0,
                    onClick = { selectedMediaTab = 0 },
                    text = {
                        Text(
                            "🎬 ${strings.tabVideos} (${videoItems.size})",
                            fontWeight = if (selectedMediaTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedMediaTab == 0) AccentCyan else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedMediaTab == 1,
                    onClick = { selectedMediaTab = 1 },
                    text = {
                        Text(
                            "🎵 ${strings.tabMusic} (${musicItems.size})",
                            fontWeight = if (selectedMediaTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedMediaTab == 1) AccentCyan else TextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val currentList = if (selectedMediaTab == 0) videoItems else musicItems

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedMediaTab == 0) "No local videos found." else "No local music found.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                                .clickable {
                                    currentTitle = item.name
                                    activeUri = if (item.thumbnailUri != null) Uri.parse(item.thumbnailUri) else Uri.fromFile(File(item.path))
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedMediaTab == 0 && item.thumbnailUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(item.thumbnailUri).crossfade(true).build(),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(if (selectedMediaTab == 0) "🎬" else "🎵", fontSize = 24.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = NetworkUtils.formatBytes(item.size),
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }

                            // If video: Convert to MP3 button
                            if (selectedMediaTab == 0 && onConvertToMp3 != null) {
                                Button(
                                    onClick = {
                                        val u = if (item.thumbnailUri != null) Uri.parse(item.thumbnailUri) else Uri.fromFile(File(item.path))
                                        onConvertToMp3(u)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).padding(end = 6.dp)
                                ) {
                                    Text("MP3", fontSize = 10.sp, color = ElectricPurple)
                                }
                            }

                            Button(
                                onClick = {
                                    currentTitle = item.name
                                    activeUri = if (item.thumbnailUri != null) Uri.parse(item.thumbnailUri) else Uri.fromFile(File(item.path))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Play", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // ── Active ExoPlayer View Mode ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player.initialize()
                            useController = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seek Bar
            Slider(
                value = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { ratio ->
                    player.seekTo((ratio * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = SurfaceDark
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), fontSize = 11.sp, color = TextSecondary)
                Text(formatTime(duration), fontSize = 11.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { player.seekTo((position - 10000L).coerceAtLeast(0L)) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⏪ 10s", fontSize = 12.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        if (isPlaying) player.pause() else player.resume()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp).width(88.dp)
                ) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { player.seekTo((position + 10000L).coerceAtMost(duration)) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("10s ⏩", fontSize = 12.sp, color = TextPrimary)
                }
            }

            if (onConvertToMp3 != null && activeUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onConvertToMp3(activeUri!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("🎵 Convert Video to MP3", fontSize = 12.sp, color = AccentCyan)
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

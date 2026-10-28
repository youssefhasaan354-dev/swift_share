package com.swiftshare.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.swiftshare.siplayer.QueueItem
import com.swiftshare.siplayer.RepeatMode
import com.swiftshare.siplayer.SwiftMediaPlayer
import com.swiftshare.siplayer.equalizer.EqualizerBand
import com.swiftshare.siplayer.folder.AudioFolder
import com.swiftshare.siplayer.folder.FolderScanner
import com.swiftshare.siplayer.playlist.Playlist
import com.swiftshare.siplayer.playlist.PlaylistItem
import com.swiftshare.siplayer.playlist.PlaylistManager
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
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val repo = remember { MediaStoreRepository(context) }
    val player = remember { SwiftMediaPlayer(context) }
    val playlistManager = remember { PlaylistManager(context) }

    val isPlaying by player.isPlaying.collectAsState()
    val position by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    val queue by player.currentQueue.collectAsState()
    val queueIndex by player.currentIndex.collectAsState()
    val lyrics by player.currentLyrics.collectAsState()
    val currentLyricLine by player.currentLyricLine.collectAsState()
    val isShuffle by player.isShuffle.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val sleepTimerSeconds by player.sleepTimer.remainingSeconds.collectAsState()
    val isSleepTimerRunning by player.sleepTimer.isTimerRunning.collectAsState()

    var activeUri by remember { mutableStateOf(mediaUri) }
    var currentTitle by remember { mutableStateOf(mediaTitle) }
    var currentArtist by remember { mutableStateOf("") }
    var isVideoMode by remember { mutableStateOf(false) }

    var selectedMediaTab by remember { mutableIntStateOf(1) } // 0: Videos, 1: Music
    var selectedMusicSubTab by remember { mutableIntStateOf(0) } // 0: All Songs, 1: Folders, 2: Playlists

    val videoItems = remember { mutableStateListOf<MediaItem>() }
    val musicItems = remember { mutableStateListOf<MediaItem>() }
    val audioFolders = remember { mutableStateListOf<AudioFolder>() }
    val playlists = remember { mutableStateListOf<Playlist>() }

    // Dialog states
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<MediaItem?>(null) }
    var selectedFolderView by remember { mutableStateOf<AudioFolder?>(null) }
    var selectedPlaylistView by remember { mutableStateOf<Playlist?>(null) }
    var showLyricsView by remember { mutableStateOf(false) }

    val lyricsListState = rememberLazyListState()

    // Auto-scroll lyrics
    LaunchedEffect(currentLyricLine) {
        if (currentLyricLine >= 0 && lyrics?.lines?.isNotEmpty() == true) {
            lyricsListState.animateScrollToItem(
                (currentLyricLine - 2).coerceAtLeast(0)
            )
        }
    }

    // Load actual library videos, music, folders & playlists
    LaunchedEffect(Unit) {
        val vids = withContext(Dispatchers.IO) { repo.getVideos() }
        videoItems.clear()
        videoItems.addAll(vids)

        val music = withContext(Dispatchers.IO) { repo.getAudio() }
        musicItems.clear()
        musicItems.addAll(music)

        val folders = withContext(Dispatchers.IO) { FolderScanner.groupMediaItemsByFolder(music) }
        audioFolders.clear()
        audioFolders.addAll(folders)

        playlists.clear()
        playlists.addAll(playlistManager.getPlaylists())
    }

    LaunchedEffect(activeUri) {
        if (activeUri != null && queue.isEmpty()) {
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
                        if (selectedFolderView != null) {
                            selectedFolderView = null
                        } else if (selectedPlaylistView != null) {
                            selectedPlaylistView = null
                        } else if (activeUri != null && mediaUri == null) {
                            player.pause()
                            activeUri = null
                        } else {
                            onBack()
                        }
                    }
                    .padding(end = 16.dp)
            )
            Text(
                text = when {
                    selectedFolderView != null -> selectedFolderView!!.name
                    selectedPlaylistView != null -> selectedPlaylistView!!.name
                    activeUri != null -> currentTitle
                    else -> strings.mediaHubTitle
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Sleep timer indicator in top bar
            if (isSleepTimerRunning) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⏱ %02d:%02d".format(sleepTimerSeconds / 60, sleepTimerSeconds % 60),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
            }
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedMediaTab == 1) {
                // Music Sub Tabs: Songs / Folders / Playlists
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Tracks", strings.tabFolders, strings.tabPlaylists).forEachIndexed { idx, label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedMusicSubTab == idx) PrimaryBlue else SurfaceCard)
                                .clickable {
                                    selectedMusicSubTab = idx
                                    selectedFolderView = null
                                    selectedPlaylistView = null
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedMusicSubTab == idx) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedMusicSubTab == idx) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (selectedMediaTab == 0) {
                // Videos list
                VideoListView(
                    videos = videoItems,
                    onPlay = { item ->
                        isVideoMode = true
                        currentTitle = item.name
                        activeUri = if (item.thumbnailUri != null) Uri.parse(item.thumbnailUri) else Uri.fromFile(File(item.path))
                    },
                    onConvertToMp3 = onConvertToMp3
                )
            } else {
                when (selectedMusicSubTab) {
                    0 -> {
                        // All Songs list
                        SongListView(
                            songs = musicItems,
                            onPlayIndex = { index ->
                                isVideoMode = false
                                val qItems = musicItems.map { m ->
                                    QueueItem(
                                        uri = if (m.thumbnailUri != null) Uri.parse(m.thumbnailUri) else Uri.fromFile(File(m.path)),
                                        title = m.name.substringBeforeLast('.'),
                                        artist = "",
                                        filePath = m.path
                                    )
                                }
                                val item = musicItems[index]
                                currentTitle = item.name.substringBeforeLast('.')
                                activeUri = if (item.thumbnailUri != null) Uri.parse(item.thumbnailUri) else Uri.fromFile(File(item.path))
                                player.setQueue(qItems, index)
                            },
                            onAddToPlaylist = { song ->
                                songToAddToPlaylist = song
                                showAddToPlaylistDialog = true
                            }
                        )
                    }
                    1 -> {
                        // Folders list
                        if (selectedFolderView == null) {
                            FolderListView(
                                folders = audioFolders,
                                onSelectFolder = { folder -> selectedFolderView = folder }
                            )
                        } else {
                            // Songs in selected folder
                            Column {
                                Text(
                                    text = "📂 ${selectedFolderView!!.name} (${selectedFolderView!!.songCount} tracks)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(selectedFolderView!!.songs) { audio ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SurfaceCard)
                                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    isVideoMode = false
                                                    currentTitle = audio.title
                                                    val q = selectedFolderView!!.songs.map { s ->
                                                        QueueItem(
                                                            uri = Uri.fromFile(File(s.path)),
                                                            title = s.title,
                                                            artist = s.artist,
                                                            filePath = s.path
                                                        )
                                                    }
                                                    val idx = selectedFolderView!!.songs.indexOf(audio)
                                                    activeUri = Uri.fromFile(File(audio.path))
                                                    player.setQueue(q, idx)
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🎵", fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(audio.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                                                Text(NetworkUtils.formatBytes(audio.size), fontSize = 11.sp, color = TextSecondary)
                                            }
                                            Text("▶", fontSize = 14.sp, color = AccentCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Playlists list
                        if (selectedPlaylistView == null) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Playlists (${playlists.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                    Button(
                                        onClick = { showNewPlaylistDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("+ ${strings.createPlaylist}", fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                if (playlists.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("No playlists created yet.", color = TextSecondary, fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(playlists) { pl ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(SurfaceCard)
                                                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                                                    .clickable { selectedPlaylistView = pl }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("🎶", fontSize = 22.sp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(pl.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                                    Text("${pl.items.size} tracks", fontSize = 11.sp, color = TextSecondary)
                                                }
                                                Text("🗑️", fontSize = 14.sp, modifier = Modifier.clickable {
                                                    playlistManager.deletePlaylist(pl.id)
                                                    playlists.clear()
                                                    playlists.addAll(playlistManager.getPlaylists())
                                                }.padding(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Selected playlist songs view
                            Column {
                                Text(
                                    text = "🎶 ${selectedPlaylistView!!.name} (${selectedPlaylistView!!.items.size} tracks)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (selectedPlaylistView!!.items.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("This playlist is empty. Add songs from Tracks tab.", color = TextSecondary, fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(selectedPlaylistView!!.items) { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(SurfaceCard)
                                                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        isVideoMode = false
                                                        currentTitle = item.title
                                                        val q = selectedPlaylistView!!.items.map { s ->
                                                            QueueItem(
                                                                uri = Uri.fromFile(File(s.path)),
                                                                title = s.title,
                                                                artist = s.artist,
                                                                filePath = s.path
                                                            )
                                                        }
                                                        val idx = selectedPlaylistView!!.items.indexOf(item)
                                                        activeUri = Uri.fromFile(File(item.path))
                                                        player.setQueue(q, idx)
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("🎵", fontSize = 20.sp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                                Text("🗑️", fontSize = 14.sp, modifier = Modifier.clickable {
                                                    playlistManager.removeSongFromPlaylist(selectedPlaylistView!!.id, item.path)
                                                    selectedPlaylistView = playlistManager.getPlaylist(selectedPlaylistView!!.id)
                                                    playlists.clear()
                                                    playlists.addAll(playlistManager.getPlaylists())
                                                }.padding(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── Active Player View Mode ─────────────────────────────────────
            if (isVideoMode) {
                // Video Player Mode
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
            } else {
                // Music Player Mode with Album Art & Synchronized Lyrics View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyricsView) {
                        // Scrolling synchronized lyrics view
                        if (lyrics == null || lyrics!!.lines.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("📜", fontSize = 42.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(strings.noLyricsFound, color = TextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Place a .lrc file in the same folder to see synced lyrics", color = TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(lyrics!!.lines.size) { index ->
                                    val line = lyrics!!.lines[index]
                                    val isActive = index == currentLyricLine
                                    Text(
                                        text = line.text,
                                        fontSize = if (isActive) 17.sp else 13.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { player.seekTo(line.timestampMs) }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Album Art / Visualizer Mode
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceBorderDark)
                                    .border(2.dp, AccentCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isPlaying) "🎶" else "🎵", fontSize = 64.sp)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(currentTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (currentArtist.isNotEmpty()) {
                                Text(currentArtist, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Pills Row: Lyrics / Equalizer / Sleep Timer / Playlist
            if (!isVideoMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PillButton(
                        text = if (showLyricsView) "🎵 Art" else "📜 ${strings.lyrics}",
                        onClick = { showLyricsView = !showLyricsView },
                        isActive = showLyricsView
                    )
                    PillButton(
                        text = "🎛️ ${strings.equalizer}",
                        onClick = { showEqualizerDialog = true },
                        isActive = player.equalizer.isEnabled
                    )
                    PillButton(
                        text = if (isSleepTimerRunning) "⏱ %02d:%02d".format(sleepTimerSeconds / 60, sleepTimerSeconds % 60) else "⏱ ${strings.sleepTimer}",
                        onClick = { showSleepTimerDialog = true },
                        isActive = isSleepTimerRunning
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

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

            Spacer(modifier = Modifier.height(8.dp))

            // Main Player Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle toggle
                Text(
                    text = "🔀",
                    fontSize = 18.sp,
                    color = if (isShuffle) AccentCyan else TextSecondary,
                    modifier = Modifier.clickable { player.toggleShuffle() }.padding(8.dp)
                )

                // Previous
                Button(
                    onClick = { player.playPrevious() },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Text("⏮", fontSize = 16.sp, color = TextPrimary)
                }

                // Play / Pause
                Button(
                    onClick = {
                        if (isPlaying) player.pause() else player.resume()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp).width(72.dp)
                ) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 22.sp)
                }

                // Next
                Button(
                    onClick = { player.playNext() },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Text("⏭", fontSize = 16.sp, color = TextPrimary)
                }

                // Repeat toggle
                Text(
                    text = when (repeatMode) {
                        RepeatMode.OFF -> "🔁"
                        RepeatMode.ALL -> "🔁"
                        RepeatMode.ONE -> "🔂"
                    },
                    fontSize = 18.sp,
                    color = if (repeatMode != RepeatMode.OFF) AccentCyan else TextSecondary,
                    modifier = Modifier.clickable { player.cycleRepeatMode() }.padding(8.dp)
                )
            }

            if (isVideoMode && onConvertToMp3 != null && activeUri != null) {
                Spacer(modifier = Modifier.height(10.dp))
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

    // ── Dialogs ─────────────────────────────────────────────────────────────

    // Equalizer Dialog
    if (showEqualizerDialog) {
        EqualizerDialog(
            equalizer = player.equalizer,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isTimerRunning = isSleepTimerRunning,
            remainingSeconds = sleepTimerSeconds,
            onStartTimer = { mins ->
                player.sleepTimer.start(mins) {
                    player.pause()
                }
                showSleepTimerDialog = false
            },
            onStartEndOfSong = {
                player.sleepTimer.startEndOfSong {
                    player.pause()
                }
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                player.sleepTimer.cancel()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    // New Playlist Dialog
    if (showNewPlaylistDialog) {
        var playlistNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text(strings.createPlaylist, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Playlist name…", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = SurfaceBorderDark
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            playlistManager.createPlaylist(playlistNameInput)
                            playlists.clear()
                            playlists.addAll(playlistManager.getPlaylists())
                        }
                        showNewPlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text(strings.cancel, color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Add To Playlist Dialog
    if (showAddToPlaylistDialog && songToAddToPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text(strings.addToPlaylist, color = TextPrimary) },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create a playlist first.", color = TextSecondary)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(playlists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceCard)
                                    .clickable {
                                        val song = songToAddToPlaylist!!
                                        playlistManager.addSongToPlaylist(
                                            pl.id,
                                            PlaylistItem(
                                                path = song.path,
                                                title = song.name.substringBeforeLast('.'),
                                                durationMs = song.durationMs ?: 0L
                                            )
                                        )
                                        playlists.clear()
                                        playlists.addAll(playlistManager.getPlaylists())
                                        showAddToPlaylistDialog = false
                                    }
                                    .padding(10.dp)
                            ) {
                                Text("🎶 ${pl.name} (${pl.items.size})", color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text(strings.cancel, color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, isActive: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) AccentCyan.copy(alpha = 0.2f) else SurfaceCard)
            .border(1.dp, if (isActive) AccentCyan else SurfaceBorderDark, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) AccentCyan else TextSecondary
        )
    }
}

@Composable
private fun EqualizerDialog(
    equalizer: com.swiftshare.siplayer.equalizer.EqualizerController,
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(equalizer.isEnabled) }
    var bands by remember { mutableStateOf(equalizer.getBands()) }
    val presets = remember { equalizer.getPresets() }
    var selectedPresetIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎛️ Equalizer", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        equalizer.setEnabled(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = PrimaryBlue)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Presets scroll row
                Text("Presets", fontSize = 12.sp, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(4).forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedPresetIndex == preset.index) AccentCyan else SurfaceCard)
                                .clickable(enabled = isEnabled) {
                                    selectedPresetIndex = preset.index
                                    equalizer.usePreset(preset.index)
                                    bands = equalizer.getBands()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                preset.name,
                                fontSize = 10.sp,
                                color = if (selectedPresetIndex == preset.index) BackgroundDark else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bands sliders
                bands.forEach { band ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.width(36.dp)
                        )
                        Slider(
                            value = band.currentLevelMb.toFloat(),
                            onValueChange = { newVal ->
                                val lvl = newVal.toInt().toShort()
                                equalizer.setBandLevel(band.index, lvl)
                                band.currentLevelMb = lvl
                            },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = PrimaryBlue),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${band.currentLevelMb / 100}dB",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            modifier = Modifier.width(42.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                Text("Done")
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
private fun SleepTimerDialog(
    isTimerRunning: Boolean,
    remainingSeconds: Int,
    onStartTimer: (Int) -> Unit,
    onStartEndOfSong: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏱ Sleep Timer", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTimerRunning) {
                    Text(
                        text = "Timer active: %02d:%02d remaining".format(remainingSeconds / 60, remainingSeconds % 60),
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = onCancelTimer,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Turn Off Sleep Timer", color = TextPrimary, fontSize = 12.sp)
                    }
                } else {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Button(
                            onClick = { onStartTimer(mins) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("$mins Minutes", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onStartEndOfSong,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("End of Current Song", color = AccentCyan, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        },
        containerColor = SurfaceDark
    )
}

@Composable
private fun VideoListView(
    videos: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onConvertToMp3: ((Uri) -> Unit)?
) {
    val context = LocalContext.current
    if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No local videos found.", color = TextSecondary, fontSize = 13.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(videos) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                        .clickable { onPlay(item) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.thumbnailUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(item.thumbnailUri).crossfade(true).build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("🎬", fontSize = 24.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(NetworkUtils.formatBytes(item.size), fontSize = 10.sp, color = TextSecondary)
                    }
                    if (onConvertToMp3 != null) {
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
                        onClick = { onPlay(item) },
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
}

@Composable
private fun SongListView(
    songs: List<MediaItem>,
    onPlayIndex: (Int) -> Unit,
    onAddToPlaylist: (MediaItem) -> Unit
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No local music found.", color = TextSecondary, fontSize = 13.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs.size) { idx ->
                val item = songs[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                        .clickable { onPlayIndex(idx) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name.substringBeforeLast('.'), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(NetworkUtils.formatBytes(item.size), fontSize = 10.sp, color = TextSecondary)
                    }
                    Text(
                        text = "➕",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clickable { onAddToPlaylist(item) }
                            .padding(8.dp)
                    )
                    Button(
                        onClick = { onPlayIndex(idx) },
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
}

@Composable
private fun FolderListView(
    folders: List<AudioFolder>,
    onSelectFolder: (AudioFolder) -> Unit
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No audio folders found.", color = TextSecondary, fontSize = 13.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                        .clickable { onSelectFolder(folder) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                        Text("${folder.songCount} songs • ${folder.path}", fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("›", fontSize = 20.sp, color = TextSecondary)
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

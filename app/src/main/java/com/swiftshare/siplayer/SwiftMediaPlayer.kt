package com.swiftshare.siplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.swiftshare.siplayer.equalizer.EqualizerController
import com.swiftshare.siplayer.lyrics.LrcLyrics
import com.swiftshare.siplayer.lyrics.LrcParser
import com.swiftshare.siplayer.timer.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class RepeatMode {
    OFF, ALL, ONE
}

data class QueueItem(
    val uri: Uri,
    val title: String,
    val artist: String = "",
    val filePath: String? = null
)

class SwiftMediaPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressTrackerJob: Job? = null

    val equalizer = EqualizerController()
    val sleepTimer = SleepTimer(scope)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<QueueItem>>(emptyList())
    val currentQueue: StateFlow<List<QueueItem>> = _currentQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentLyrics = MutableStateFlow<LrcLyrics?>(null)
    val currentLyrics: StateFlow<LrcLyrics?> = _currentLyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val mediaControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(MusicPlaybackService.EXTRA_COMMAND) ?: return
            when (command) {
                MusicPlaybackService.ACTION_PLAY -> resume()
                MusicPlaybackService.ACTION_PAUSE -> pause()
                MusicPlaybackService.ACTION_NEXT -> playNext()
                MusicPlaybackService.ACTION_PREV -> playPrevious()
                MusicPlaybackService.ACTION_SEEK -> {
                    val pos = intent.getLongExtra(MusicPlaybackService.EXTRA_SEEK_POSITION, 0L)
                    seekTo(pos)
                }
            }
        }
    }

    init {
        val filter = IntentFilter(MusicPlaybackService.BROADCAST_MEDIA_CONTROL)
        ContextCompat.registerReceiver(
            context,
            mediaControlReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @OptIn(UnstableApi::class)
    fun initialize(): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!

        val player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) startTrackingProgress() else stopTrackingProgress()
                    updateNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = duration.coerceAtLeast(0L)
                        try {
                            equalizer.attach(audioSessionId)
                        } catch (_: Exception) {}
                    } else if (playbackState == Player.STATE_ENDED) {
                        sleepTimer.notifySongCompleted()
                        onTrackEnded()
                    }
                }
            })
        }
        exoPlayer = player
        return player
    }

    fun setQueue(items: List<QueueItem>, startIndex: Int = 0) {
        _currentQueue.value = items
        if (items.isNotEmpty() && startIndex in items.indices) {
            playIndex(startIndex)
        }
    }

    fun playIndex(index: Int) {
        val items = _currentQueue.value
        if (index !in items.indices) return
        _currentIndex.value = index
        val item = items[index]
        playItem(item)
    }

    fun playNext() {
        val items = _currentQueue.value
        if (items.isEmpty()) return
        val current = _currentIndex.value

        val nextIndex = when {
            _isShuffle.value -> (items.indices - current).randomOrNull() ?: current
            current + 1 < items.size -> current + 1
            _repeatMode.value == RepeatMode.ALL -> 0
            else -> -1
        }

        if (nextIndex in items.indices) {
            playIndex(nextIndex)
        }
    }

    fun playPrevious() {
        val items = _currentQueue.value
        if (items.isEmpty()) return
        val current = _currentIndex.value

        val prevIndex = when {
            _currentPosition.value > 3000L -> current // restart current song if played > 3s
            current - 1 >= 0 -> current - 1
            _repeatMode.value == RepeatMode.ALL -> items.size - 1
            else -> 0
        }

        if (prevIndex in items.indices) {
            playIndex(prevIndex)
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private fun onTrackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                resume()
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                playNext()
            }
        }
    }

    fun play(uri: Uri) {
        val path = if (uri.scheme == "file") uri.path else null
        val title = path?.let { File(it).nameWithoutExtension } ?: "Audio"
        val item = QueueItem(uri = uri, title = title, artist = "", filePath = path)
        _currentQueue.value = listOf(item)
        _currentIndex.value = 0
        playItem(item)
    }

    private fun playItem(item: QueueItem) {
        val player = exoPlayer ?: initialize()
        loadLyrics(item.filePath)

        val mediaItem = MediaItem.fromUri(item.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        updateNotification()
    }

    private fun loadLyrics(filePath: String?) {
        _currentLyrics.value = null
        _currentLyricLine.value = -1
        if (filePath == null) return

        scope.launch(Dispatchers.IO) {
            val lrcFile = LrcParser.findLrcFileForAudio(filePath)
            if (lrcFile != null && lrcFile.exists()) {
                try {
                    val content = lrcFile.readText(Charsets.UTF_8)
                    val lyrics = LrcParser.parse(content)
                    _currentLyrics.value = lyrics
                } catch (_: Exception) {}
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        updateNotification()
    }

    fun resume() {
        exoPlayer?.play()
        updateNotification()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPosition.value = positionMs
        updateLyricSync(positionMs)
    }

    private fun updateNotification() {
        val current = _currentQueue.value.getOrNull(_currentIndex.value)
        if (current != null) {
            MusicPlaybackService.start(
                context,
                title = current.title,
                artist = current.artist,
                isPlaying = _isPlaying.value
            )
        }
    }

    private fun startTrackingProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition
                    _currentPosition.value = pos
                    _duration.value = player.duration.coerceAtLeast(0L)
                    updateLyricSync(pos)
                }
                delay(250)
            }
        }
    }

    private fun updateLyricSync(positionMs: Long) {
        val lyrics = _currentLyrics.value ?: return
        val activeLineIdx = LrcParser.getLineIndexAt(lyrics, positionMs)
        _currentLyricLine.value = activeLineIdx
    }

    private fun stopTrackingProgress() {
        progressTrackerJob?.cancel()
    }

    fun release() {
        stopTrackingProgress()
        try {
            context.unregisterReceiver(mediaControlReceiver)
        } catch (_: Exception) {}
        MusicPlaybackService.stop(context)
        equalizer.release()
        sleepTimer.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}

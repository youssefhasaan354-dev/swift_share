package com.swiftshare.siplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.swiftshare.R
import com.swiftshare.ui.MainActivity

class MusicPlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private var currentTitle: String = "SwiftShare Music"
    private var currentArtist: String = ""
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSession(this, "SwiftShareMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    sendServiceAction(ACTION_PLAY)
                }

                override fun onPause() {
                    sendServiceAction(ACTION_PAUSE)
                }

                override fun onSkipToNext() {
                    sendServiceAction(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    sendServiceAction(ACTION_PREV)
                }

                override fun onSeekTo(pos: Long) {
                    val intent = Intent(BROADCAST_MEDIA_CONTROL).apply {
                        putExtra(EXTRA_COMMAND, ACTION_SEEK)
                        putExtra(EXTRA_SEEK_POSITION, pos)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }

                override fun onStop() {
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
        val artist = intent?.getStringExtra(EXTRA_ARTIST) ?: currentArtist
        val playing = intent?.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying) ?: isPlaying

        currentTitle = title
        currentArtist = artist
        isPlaying = playing

        when (action) {
            ACTION_PLAY -> {
                isPlaying = true
                sendServiceAction(ACTION_PLAY)
            }
            ACTION_PAUSE -> {
                isPlaying = false
                sendServiceAction(ACTION_PAUSE)
            }
            ACTION_NEXT -> sendServiceAction(ACTION_NEXT)
            ACTION_PREV -> sendServiceAction(ACTION_PREV)
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateMediaSession()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(BROADCAST_MEDIA_CONTROL).apply {
            putExtra(EXTRA_COMMAND, action)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun updateMediaSession() {
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1.0f
            )

        mediaSession?.setPlaybackState(stateBuilder.build())

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .build()

        mediaSession?.setMetadata(metadata)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = createActionPendingIntent(ACTION_PREV, 1)
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPausePendingIntent = createActionPendingIntent(playPauseAction, 2)
        val nextPendingIntent = createActionPendingIntent(ACTION_NEXT, 3)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist.ifEmpty { "SwiftShare Music Player" })
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Previous", prevPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next", nextPendingIntent
                ).build()
            )

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let {
            mediaStyle.setMediaSession(it)
        }
        builder.style = mediaStyle

        return builder.build()
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SwiftShare Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media controls on lockscreen and notification drawer"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "swiftshare_music_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_PLAY = "com.swiftshare.siplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.swiftshare.siplayer.ACTION_PAUSE"
        const val ACTION_NEXT = "com.swiftshare.siplayer.ACTION_NEXT"
        const val ACTION_PREV = "com.swiftshare.siplayer.ACTION_PREV"
        const val ACTION_STOP = "com.swiftshare.siplayer.ACTION_STOP"
        const val ACTION_SEEK = "com.swiftshare.siplayer.ACTION_SEEK"

        const val BROADCAST_MEDIA_CONTROL = "com.swiftshare.siplayer.MEDIA_CONTROL"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_SEEK_POSITION = "extra_seek_position"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun start(context: Context, title: String, artist: String, isPlaying: Boolean) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun update(context: Context, title: String, artist: String, isPlaying: Boolean) {
            start(context, title, artist, isPlaying)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}

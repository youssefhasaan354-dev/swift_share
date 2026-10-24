package com.swiftshare.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.swiftshare.R
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.ui.MainActivity

class TransferForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        fun startService(context: Context, fileName: String, totalBytes: Long) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = Constants.ACTION_START_TRANSFER
                    putExtra("file_name", fileName)
                    putExtra("total_bytes", totalBytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("TransferService", "Cannot start foreground service (Android 14+ background restriction): ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = Constants.ACTION_CANCEL_TRANSFER
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("TransferService", "Failed to stop service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SwiftShare:TransferWakeLock").apply {
            acquire(3 * 60 * 60 * 1000L) // 3 hours timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_TRANSFER -> {
                val fileName = intent.getStringExtra("file_name") ?: "Files"
                val totalBytes = intent.getLongExtra("total_bytes", 0L)
                val notification = buildProgressNotification(fileName, 0, 100, 0)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        }
                        startForeground(Constants.NOTIFICATION_ID_TRANSFER, notification, serviceType)
                    } else {
                        startForeground(Constants.NOTIFICATION_ID_TRANSFER, notification)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TransferService", "Failed to startForeground: ${e.message}")
                }
            }
            Constants.ACTION_CANCEL_TRANSFER -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun updateProgress(fileName: String, transferred: Long, total: Long, speedBytesSec: Long) {
        val percent = if (total > 0) ((transferred * 100) / total).toInt() else 0
        val notification = buildProgressNotification(fileName, percent, 100, speedBytesSec)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID_TRANSFER, notification)
    }

    private fun buildProgressNotification(
        fileName: String,
        progress: Int,
        maxProgress: Int,
        speedBytesSec: Long
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TransferForegroundService::class.java).apply { action = Constants.ACTION_CANCEL_TRANSFER },
            PendingIntent.FLAG_IMMUTABLE
        )

        val speedText = if (speedBytesSec > 0) " • " + NetworkUtils.formatSpeed(speedBytesSec) else ""

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_TRANSFER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SwiftShare: $fileName")
            .setContentText("$progress%$speedText")
            .setProgress(maxProgress, progress, false)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

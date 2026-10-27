package com.swiftshare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.swiftshare.common.Constants
import com.swiftshare.data.db.AppDatabase

class SwiftShareApp : Application() {

    companion object {
        lateinit var instance: SwiftShareApp
            private set
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "swiftshare.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2)
         .fallbackToDestructiveMigration()
         .build()

        createNotificationChannels()
        com.swiftshare.common.LocaleManager.init(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val transferChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_TRANSFER,
                "File Transfer Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress and speed for active P2P transfers"
                enableVibration(false)
                setShowBadge(false)
            }

            val serviceChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_SERVICE,
                "SwiftShare Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps connection alive during background hotspot and transfers"
            }

            notificationManager.createNotificationChannel(transferChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }
}

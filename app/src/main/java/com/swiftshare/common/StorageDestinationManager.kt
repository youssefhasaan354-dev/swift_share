package com.swiftshare.common

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class StorageVolumeType(val key: String) {
    INTERNAL("internal"),
    SD_CARD("sd_card")
}

data class StorageVolumeInfo(
    val type: StorageVolumeType,
    val title: String,
    val rootDir: File,
    val downloadDir: File,
    val freeBytes: Long,
    val totalBytes: Long,
    val isRemovable: Boolean,
    val isMounted: Boolean
)

object StorageDestinationManager {
    private const val PREFS_NAME = "swiftshare_storage_prefs"
    private const val KEY_VOLUME_TYPE = "selected_volume_type"

    private lateinit var prefs: SharedPreferences
    private val _selectedVolume = MutableStateFlow(StorageVolumeType.INTERNAL)
    val selectedVolume: StateFlow<StorageVolumeType> = _selectedVolume.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_VOLUME_TYPE, StorageVolumeType.INTERNAL.key)
        _selectedVolume.value = if (saved == StorageVolumeType.SD_CARD.key) {
            StorageVolumeType.SD_CARD
        } else {
            StorageVolumeType.INTERNAL
        }
    }

    fun setVolumeType(context: Context, type: StorageVolumeType) {
        _selectedVolume.value = type
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        prefs.edit().putString(KEY_VOLUME_TYPE, type.key).apply()
    }

    /**
     * Enumerates internal storage and secondary SD card storage volumes.
     */
    fun getAvailableVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()

        // 1. Primary Internal Storage
        val internalDownloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SwiftShare"
        )
        val internalRoot = Environment.getExternalStorageDirectory() ?: Environment.getDataDirectory()
        val internalFree = internalRoot.freeSpace
        val internalTotal = internalRoot.totalSpace

        volumes.add(
            StorageVolumeInfo(
                type = StorageVolumeType.INTERNAL,
                title = "Internal Storage",
                rootDir = internalRoot,
                downloadDir = internalDownloadDir,
                freeBytes = internalFree,
                totalBytes = internalTotal,
                isRemovable = false,
                isMounted = true
            )
        )

        // 2. Secondary External Storage (SD Card)
        try {
            @Suppress("DEPRECATION")
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            if (externalDirs.size > 1 && externalDirs[1] != null) {
                val sdCardAppDir = externalDirs[1]!!
                val isMounted = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState(sdCardAppDir)
                if (isMounted) {
                    val sdDownloadDir = File(sdCardAppDir, "SwiftShare")
                    val sdFree = sdCardAppDir.freeSpace
                    val sdTotal = sdCardAppDir.totalSpace
                    volumes.add(
                        StorageVolumeInfo(
                            type = StorageVolumeType.SD_CARD,
                            title = "SD Card",
                            rootDir = sdCardAppDir,
                            downloadDir = sdDownloadDir,
                            freeBytes = sdFree,
                            totalBytes = sdTotal,
                            isRemovable = true,
                            isMounted = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e("StorageDestManager", "Failed to inspect secondary storage: ${e.message}", e)
        }

        return volumes
    }

    /**
     * Resolves the active download directory based on selected volume type.
     * Falls back to internal storage if SD card is selected but unmounted.
     */
    fun getDestinationDirectory(context: Context): File {
        val volumes = getAvailableVolumes(context)
        val selected = _selectedVolume.value

        if (selected == StorageVolumeType.SD_CARD) {
            val sdVolume = volumes.find { it.type == StorageVolumeType.SD_CARD }
            if (sdVolume != null && sdVolume.isMounted) {
                if (!sdVolume.downloadDir.exists()) {
                    sdVolume.downloadDir.mkdirs()
                }
                return sdVolume.downloadDir
            }
        }

        // Default: Internal Storage
        val internalVolume = volumes.firstOrNull { it.type == StorageVolumeType.INTERNAL }
        val target = internalVolume?.downloadDir ?: File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SwiftShare"
        )
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    /**
     * Gets available free bytes on the currently selected storage volume.
     */
    fun getFreeSpace(context: Context): Long {
        return try {
            val dir = getDestinationDirectory(context)
            dir.freeSpace
        } catch (_: Exception) {
            10L * 1024L * 1024L * 1024L // 10 GB safe fallback
        }
    }

    /**
     * Gets total bytes on the currently selected storage volume.
     */
    fun getTotalSpace(context: Context): Long {
        return try {
            val dir = getDestinationDirectory(context)
            dir.totalSpace
        } catch (_: Exception) {
            64L * 1024L * 1024L * 1024L // 64 GB fallback
        }
    }
}


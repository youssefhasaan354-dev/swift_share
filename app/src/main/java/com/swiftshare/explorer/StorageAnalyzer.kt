package com.swiftshare.explorer

import android.os.Environment
import android.os.StatFs

data class StorageBreakdown(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val photosBytes: Long,
    val videosBytes: Long,
    val musicBytes: Long,
    val appsBytes: Long,
    val docsBytes: Long,
    val otherBytes: Long
)

class StorageAnalyzer(private val mediaRepo: MediaStoreRepository) {

    fun analyzeStorage(): StorageBreakdown {
        val path = Environment.getExternalStorageDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - freeBytes

        val photosBytes = mediaRepo.getPhotos().sumOf { it.size }
        val videosBytes = mediaRepo.getVideos().sumOf { it.size }
        val musicBytes = mediaRepo.getAudio().sumOf { it.size }
        val appsBytes = mediaRepo.getInstalledApps().sumOf { it.size }
        val docsBytes = mediaRepo.getDocuments().sumOf { it.size }

        val knownUsed = photosBytes + videosBytes + musicBytes + appsBytes + docsBytes
        val otherBytes = (usedBytes - knownUsed).coerceAtLeast(0)

        return StorageBreakdown(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            photosBytes = photosBytes,
            videosBytes = videosBytes,
            musicBytes = musicBytes,
            appsBytes = appsBytes,
            docsBytes = docsBytes,
            otherBytes = otherBytes
        )
    }
}

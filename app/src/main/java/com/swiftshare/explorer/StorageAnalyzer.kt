package com.swiftshare.explorer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.File

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

enum class StorageCategory(val key: String, val titleEn: String, val titleAr: String, val icon: String) {
    PHOTOS("photos", "Photos", "الصور", "🖼️"),
    VIDEOS("videos", "Videos", "الفيديوهات", "🎬"),
    MUSIC("music", "Music", "الموسيقى", "🎵"),
    APPS("apps", "Apps", "التطبيقات", "📱"),
    DOCS("docs", "Documents", "المستندات", "📄"),
    OTHER("other", "Other Files", "ملفات أخرى", "📦")
}

data class StorageDeleteResult(
    val requestedCount: Int,
    val deletedCount: Int,
    val freedBytes: Long,
    val failedItems: List<MediaItem> = emptyList()
)

class StorageAnalyzer(
    private val mediaRepo: MediaStoreRepository? = null,
    private val context: Context? = null,
    private val customCategoryProvider: ((String) -> List<MediaItem>)? = null,
    private val customDiskStats: (() -> Pair<Long, Long>)? = null,
    private val customFileDeleter: ((MediaItem) -> Boolean)? = null
) {

    fun analyzeStorage(): StorageBreakdown {
        val (totalBytes, freeBytes) = getDiskStats()
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        val photos = getCategoryItems(StorageCategory.PHOTOS.key)
        val videos = getCategoryItems(StorageCategory.VIDEOS.key)
        val music = getCategoryItems(StorageCategory.MUSIC.key)
        val apps = getCategoryItems(StorageCategory.APPS.key)
        val docs = getCategoryItems(StorageCategory.DOCS.key)

        val photosBytes = photos.sumOf { it.size }
        val videosBytes = videos.sumOf { it.size }
        val musicBytes = music.sumOf { it.size }
        val appsBytes = apps.sumOf { it.size }
        val docsBytes = docs.sumOf { it.size }

        val knownUsed = photosBytes + videosBytes + musicBytes + appsBytes + docsBytes
        val otherBytes = (usedBytes - knownUsed).coerceAtLeast(0L)

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

    fun getCategoryItems(categoryKey: String): List<MediaItem> {
        customCategoryProvider?.let { provider ->
            return provider(categoryKey)
        }

        val repo = mediaRepo ?: return emptyList()
        return try {
            when (categoryKey) {
                StorageCategory.PHOTOS.key -> repo.getPhotos()
                StorageCategory.VIDEOS.key -> repo.getVideos()
                StorageCategory.MUSIC.key -> repo.getAudio()
                StorageCategory.APPS.key -> repo.getInstalledApps()
                StorageCategory.DOCS.key -> repo.getDocuments()
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteItems(items: List<MediaItem>): StorageDeleteResult {
        var deletedCount = 0
        var freedBytes = 0L
        val failed = mutableListOf<MediaItem>()

        for (item in items) {
            val isSuccess = customFileDeleter?.invoke(item) ?: defaultDeleteItem(item)
            if (isSuccess) {
                deletedCount++
                freedBytes += item.size
            } else {
                failed.add(item)
            }
        }

        return StorageDeleteResult(
            requestedCount = items.size,
            deletedCount = deletedCount,
            freedBytes = freedBytes,
            failedItems = failed
        )
    }

    private fun defaultDeleteItem(item: MediaItem): Boolean {
        // Try deleting direct File if exists
        val f = File(item.path)
        if (f.exists()) {
            val deleted = try {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } catch (e: Exception) {
                false
            }
            if (deleted) return true
        }

        // Try content resolver if content Uri is provided
        if (context != null && item.thumbnailUri?.startsWith("content://") == true) {
            return try {
                val uri = Uri.parse(item.thumbnailUri)
                val rows = context.contentResolver.delete(uri, null, null)
                rows > 0
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    private fun getDiskStats(): Pair<Long, Long> {
        customDiskStats?.let { return it() }
        return try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            Pair(total, free)
        } catch (e: Exception) {
            Pair(64L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024)
        }
    }
}

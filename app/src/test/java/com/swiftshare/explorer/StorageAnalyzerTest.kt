package com.swiftshare.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAnalyzerTest {

    private val samplePhotos = listOf(
        MediaItem("p1", "photo1.jpg", "/storage/photo1.jpg", 20 * 1024 * 1024L, "image/jpeg", "photos"),
        MediaItem("p2", "photo2.jpg", "/storage/photo2.jpg", 30 * 1024 * 1024L, "image/jpeg", "photos")
    )

    private val sampleVideos = listOf(
        MediaItem("v1", "clip.mp4", "/storage/clip.mp4", 200 * 1024 * 1024L, "video/mp4", "videos")
    )

    private val sampleMusic = listOf(
        MediaItem("m1", "track.mp3", "/storage/track.mp3", 30 * 1024 * 1024L, "audio/mpeg", "music")
    )

    private val sampleApps = listOf(
        MediaItem("a1", "app.apk", "/data/app/app.apk", 100 * 1024 * 1024L, "application/vnd.android.package-archive", "apps")
    )

    private val sampleDocs = listOf(
        MediaItem("d1", "manual.pdf", "/storage/manual.pdf", 20 * 1024 * 1024L, "application/pdf", "docs")
    )

    private fun createAnalyzer(
        totalBytes: Long = 1000 * 1024 * 1024L,
        freeBytes: Long = 400 * 1024 * 1024L,
        failingItemIds: Set<String> = emptySet()
    ): StorageAnalyzer {
        return StorageAnalyzer(
            customCategoryProvider = { cat ->
                when (cat) {
                    StorageCategory.PHOTOS.key -> samplePhotos
                    StorageCategory.VIDEOS.key -> sampleVideos
                    StorageCategory.MUSIC.key -> sampleMusic
                    StorageCategory.APPS.key -> sampleApps
                    StorageCategory.DOCS.key -> sampleDocs
                    else -> emptyList()
                }
            },
            customDiskStats = {
                Pair(totalBytes, freeBytes)
            },
            customFileDeleter = { item ->
                !failingItemIds.contains(item.id)
            }
        )
    }

    @Test(timeout = 60000)
    fun testStorageBreakdownCalculation() {
        val analyzer = createAnalyzer()
        val breakdown = analyzer.analyzeStorage()

        val expectedTotal = 1000 * 1024 * 1024L
        val expectedFree = 400 * 1024 * 1024L
        val expectedUsed = 600 * 1024 * 1024L
        val expectedPhotos = 50 * 1024 * 1024L
        val expectedVideos = 200 * 1024 * 1024L
        val expectedMusic = 30 * 1024 * 1024L
        val expectedApps = 100 * 1024 * 1024L
        val expectedDocs = 20 * 1024 * 1024L
        val expectedOther = (600 - (50 + 200 + 30 + 100 + 20)) * 1024 * 1024L // 200MB

        assertEquals(expectedTotal, breakdown.totalBytes)
        assertEquals(expectedFree, breakdown.freeBytes)
        assertEquals(expectedUsed, breakdown.usedBytes)
        assertEquals(expectedPhotos, breakdown.photosBytes)
        assertEquals(expectedVideos, breakdown.videosBytes)
        assertEquals(expectedMusic, breakdown.musicBytes)
        assertEquals(expectedApps, breakdown.appsBytes)
        assertEquals(expectedDocs, breakdown.docsBytes)
        assertEquals(expectedOther, breakdown.otherBytes)
    }

    @Test(timeout = 60000)
    fun testCategoryDrillDownItems() {
        val analyzer = createAnalyzer()

        val photos = analyzer.getCategoryItems(StorageCategory.PHOTOS.key)
        assertEquals(2, photos.size)
        assertEquals("photo1.jpg", photos[0].name)
        assertEquals("photo2.jpg", photos[1].name)

        val videos = analyzer.getCategoryItems(StorageCategory.VIDEOS.key)
        assertEquals(1, videos.size)
        assertEquals("clip.mp4", videos[0].name)

        val docs = analyzer.getCategoryItems(StorageCategory.DOCS.key)
        assertEquals(1, docs.size)
        assertEquals("manual.pdf", docs[0].name)
    }

    @Test(timeout = 60000)
    fun testMultiSelectDeleteSuccess() {
        val analyzer = createAnalyzer()
        val itemsToDelete = listOf(samplePhotos[0], samplePhotos[1], sampleVideos[0])

        val result = analyzer.deleteItems(itemsToDelete)

        assertEquals(3, result.requestedCount)
        assertEquals(3, result.deletedCount)
        assertEquals(250 * 1024 * 1024L, result.freedBytes)
        assertTrue(result.failedItems.isEmpty())
    }

    @Test(timeout = 60000)
    fun testMultiSelectDeleteWithFailures() {
        // Failing photo2.jpg
        val analyzer = createAnalyzer(failingItemIds = setOf("p2"))
        val itemsToDelete = listOf(samplePhotos[0], samplePhotos[1], sampleMusic[0])

        val result = analyzer.deleteItems(itemsToDelete)

        assertEquals(3, result.requestedCount)
        assertEquals(2, result.deletedCount)
        // photo1 (20MB) + music (30MB) = 50MB freed
        assertEquals(50 * 1024 * 1024L, result.freedBytes)
        assertEquals(1, result.failedItems.size)
        assertEquals("p2", result.failedItems[0].id)
    }
}


package com.swiftshare.explorer

import com.swiftshare.explorer.search.GlobalSearchEngine
import com.swiftshare.explorer.search.SearchCategory
import com.swiftshare.explorer.search.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {

    private val sampleFiles = listOf(
        SearchResultItem("1", "vacation_beach.jpg", "/storage/emulated/0/DCIM/vacation_beach.jpg", 2048576L, SearchCategory.PHOTOS),
        SearchResultItem("2", "family_video.mp4", "/storage/emulated/0/Movies/family_video.mp4", 52428800L, SearchCategory.VIDEOS),
        SearchResultItem("3", "summer_anthem.mp3", "/storage/emulated/0/Music/summer_anthem.mp3", 4194304L, SearchCategory.AUDIO),
        SearchResultItem("4", "report_q3_vacation.pdf", "/storage/emulated/0/Download/report_q3_vacation.pdf", 1048576L, SearchCategory.DOCUMENTS),
        SearchResultItem("5", "BeachResortGuide.apk", "/data/app/BeachResortGuide.apk", 15728640L, SearchCategory.APPS, extraInfo = "com.resort.guide")
    )

    private val sampleHistory = listOf(
        SearchResultItem("h1", "beach_sunset.png", "content://media/external/images/media/100", 1024000L, SearchCategory.HISTORY, extraInfo = "Transfer • COMPLETED"),
        SearchResultItem("h2", "notes.txt", "content://media/external/files/101", 512L, SearchCategory.HISTORY, extraInfo = "Transfer • FAILED")
    )

    private fun createSearchEngine(): GlobalSearchEngine {
        return GlobalSearchEngine(
            customFileProvider = { cat ->
                sampleFiles.filter { it.category == cat }
            },
            customHistoryProvider = {
                sampleHistory
            },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test(timeout = 60000)
    fun testEmptyQueryReturnsEmptyList() = runBlocking {
        val engine = createSearchEngine()
        val results = engine.search("   ")
        assertTrue("Empty query must return empty results", results.isEmpty())
    }

    @Test(timeout = 60000)
    fun testConcurrentMultiSourceQuery() = runBlocking {
        val engine = createSearchEngine()
        // "beach" matches: vacation_beach.jpg (photos), BeachResortGuide.apk (apps), beach_sunset.png (history)
        val results = engine.search("beach")
        assertEquals(3, results.size)
        assertTrue(results.any { it.name == "vacation_beach.jpg" && it.category == SearchCategory.PHOTOS })
        assertTrue(results.any { it.name == "BeachResortGuide.apk" && it.category == SearchCategory.APPS })
        assertTrue(results.any { it.name == "beach_sunset.png" && it.category == SearchCategory.HISTORY })
    }

    @Test(timeout = 60000)
    fun testCategoryFiltering() = runBlocking {
        val engine = createSearchEngine()
        // Only photos
        val photoResults = engine.search("beach", SearchCategory.PHOTOS)
        assertEquals(1, photoResults.size)
        assertEquals("vacation_beach.jpg", photoResults[0].name)
        assertEquals(SearchCategory.PHOTOS, photoResults[0].category)

        // Only history
        val historyResults = engine.search("beach", SearchCategory.HISTORY)
        assertEquals(1, historyResults.size)
        assertEquals("beach_sunset.png", historyResults[0].name)
        assertEquals(SearchCategory.HISTORY, historyResults[0].category)

        // Only audio (which has no "beach")
        val audioResults = engine.search("beach", SearchCategory.AUDIO)
        assertTrue(audioResults.isEmpty())
    }

    @Test(timeout = 60000)
    fun testCaseInsensitiveAndPrefixRanking() = runBlocking {
        val engine = createSearchEngine()
        // "vacation" matches: vacation_beach.jpg (starts with vacation) and report_q3_vacation.pdf (contains vacation)
        val results = engine.search("VACATION")
        assertEquals(2, results.size)
        // vacation_beach.jpg should be ranked first because it starts with "vacation"
        assertEquals("vacation_beach.jpg", results[0].name)
        assertEquals("report_q3_vacation.pdf", results[1].name)
    }

    @Test(timeout = 60000)
    fun testGroupedSearchResults() = runBlocking {
        val engine = createSearchEngine()
        val grouped = engine.searchGrouped("vacation")
        assertTrue(grouped.containsKey(SearchCategory.PHOTOS))
        assertTrue(grouped.containsKey(SearchCategory.DOCUMENTS))
        assertEquals(1, grouped[SearchCategory.PHOTOS]?.size)
        assertEquals(1, grouped[SearchCategory.DOCUMENTS]?.size)
    }

    @Test(timeout = 60000)
    fun testHistoryStatusMatching() = runBlocking {
        val engine = createSearchEngine()
        // "failed" matches extraInfo "Transfer • FAILED"
        val results = engine.search("failed")
        assertEquals(1, results.size)
        assertEquals("notes.txt", results[0].name)
        assertEquals(SearchCategory.HISTORY, results[0].category)
    }

    @Test(timeout = 60000)
    fun testSearchFlowCreation() = runBlocking {
        val engine = createSearchEngine()
        val flow = engine.createSearchFlow(
            queryFlow = flowOf("anthem"),
            categoryFlow = flowOf(SearchCategory.ALL),
            debounceMs = 0L
        )
        val result = flow.first()
        assertEquals(1, result.size)
        assertEquals("summer_anthem.mp3", result[0].name)
        assertEquals(SearchCategory.AUDIO, result[0].category)
    }
}


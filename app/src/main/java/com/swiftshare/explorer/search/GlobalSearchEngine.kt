package com.swiftshare.explorer.search

import com.swiftshare.data.db.SessionDao
import com.swiftshare.data.entities.TransferItemEntity
import com.swiftshare.explorer.MediaItem
import com.swiftshare.explorer.MediaStoreRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

enum class SearchCategory(val titleEn: String, val titleAr: String, val icon: String) {
    ALL("All", "الكل", "🔍"),
    PHOTOS("Photos", "الصور", "🖼️"),
    VIDEOS("Videos", "الفيديوهات", "🎬"),
    AUDIO("Audio", "الموسيقى", "🎵"),
    DOCUMENTS("Documents", "المستندات", "📄"),
    APPS("Apps", "التطبيقات", "📱"),
    HISTORY("History", "السجل", "📋")
}

data class SearchResultItem(
    val id: String,
    val name: String,
    val pathOrUri: String,
    val size: Long,
    val category: SearchCategory,
    val mimeType: String? = null,
    val dateModified: Long? = null,
    val extraInfo: String? = null
)

class GlobalSearchEngine(
    private val mediaRepo: MediaStoreRepository? = null,
    private val sessionDao: SessionDao? = null,
    private val customFileProvider: (suspend (SearchCategory) -> List<SearchResultItem>)? = null,
    private val customHistoryProvider: (suspend () -> List<SearchResultItem>)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Executes parallel search across all categories and history using [Dispatchers.IO].
     */
    suspend fun search(
        query: String,
        categoryFilter: SearchCategory = SearchCategory.ALL
    ): List<SearchResultItem> = coroutineScope {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val deferredResults = mutableListOf<kotlinx.coroutines.Deferred<List<SearchResultItem>>>()

        // 1. Files & Media Categories
        val fileCategories = when (categoryFilter) {
            SearchCategory.ALL -> listOf(
                SearchCategory.PHOTOS,
                SearchCategory.VIDEOS,
                SearchCategory.AUDIO,
                SearchCategory.DOCUMENTS,
                SearchCategory.APPS
            )
            SearchCategory.HISTORY -> emptyList()
            else -> listOf(categoryFilter)
        }

        for (cat in fileCategories) {
            deferredResults.add(async(ioDispatcher) {
                fetchCategoryItems(cat)
            })
        }

        // 2. Transfer History
        if (categoryFilter == SearchCategory.ALL || categoryFilter == SearchCategory.HISTORY) {
            deferredResults.add(async(ioDispatcher) {
                fetchHistoryItems()
            })
        }

        val allItems = deferredResults.flatMap { it.await() }

        // Filter by query substring (case-insensitive) on name, extraInfo, or path
        val normalizedQuery = trimmedQuery.lowercase()
        allItems.filter { item ->
            item.name.lowercase().contains(normalizedQuery) ||
                (item.extraInfo?.lowercase()?.contains(normalizedQuery) == true) ||
                item.pathOrUri.lowercase().contains(normalizedQuery)
        }.sortedWith(
            compareByDescending<SearchResultItem> {
                // Exact name prefix matches ranked higher
                it.name.lowercase().startsWith(normalizedQuery)
            }.thenBy { it.name.lowercase() }
        )
    }

    /**
     * Groups the search results by category.
     */
    suspend fun searchGrouped(
        query: String,
        categoryFilter: SearchCategory = SearchCategory.ALL
    ): Map<SearchCategory, List<SearchResultItem>> {
        val flatResults = search(query, categoryFilter)
        return flatResults.groupBy { it.category }
    }

    /**
     * Creates a reactive debounced search flow.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun createSearchFlow(
        queryFlow: Flow<String>,
        categoryFlow: Flow<SearchCategory>,
        debounceMs: Long = 250L
    ): Flow<List<SearchResultItem>> {
        return kotlinx.coroutines.flow.combine(
            queryFlow.debounce(debounceMs).distinctUntilChanged(),
            categoryFlow.distinctUntilChanged()
        ) { q, cat ->
            search(q, cat)
        }
    }

    private suspend fun fetchCategoryItems(cat: SearchCategory): List<SearchResultItem> {
        customFileProvider?.let { provider ->
            return provider(cat)
        }

        val repo = mediaRepo ?: return emptyList()
        return try {
            val mediaItems = when (cat) {
                SearchCategory.PHOTOS -> repo.getPhotos()
                SearchCategory.VIDEOS -> repo.getVideos()
                SearchCategory.AUDIO -> repo.getAudio()
                SearchCategory.DOCUMENTS -> repo.getDocuments()
                SearchCategory.APPS -> repo.getInstalledApps()
                else -> emptyList()
            }
            mediaItems.map { it.toSearchResultItem(cat) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchHistoryItems(): List<SearchResultItem> {
        customHistoryProvider?.let { provider ->
            return provider()
        }

        val dao = sessionDao ?: return emptyList()
        return try {
            val historyItems = dao.getAllTransferItemsSync()
            historyItems.map { it.toSearchResultItem() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun MediaItem.toSearchResultItem(cat: SearchCategory): SearchResultItem {
        return SearchResultItem(
            id = id,
            name = name,
            pathOrUri = path,
            size = size,
            category = cat,
            mimeType = mimeType,
            extraInfo = packageName ?: (if (cat == SearchCategory.AUDIO && durationMs != null && durationMs > 0) "${durationMs / 1000}s" else null)
        )
    }

    private fun TransferItemEntity.toSearchResultItem(): SearchResultItem {
        return SearchResultItem(
            id = fileId.ifEmpty { id.toString() },
            name = name,
            pathOrUri = uriOrPath,
            size = size,
            category = SearchCategory.HISTORY,
            mimeType = mimeType,
            extraInfo = "Transfer • $status"
        )
    }
}


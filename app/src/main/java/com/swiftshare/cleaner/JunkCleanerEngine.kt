package com.swiftshare.cleaner

import android.content.Context
import android.os.Environment
import com.swiftshare.nft.channel.ChunkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class JunkCategory {
    TEMP_FILES,
    OBSOLETE_APKS,
    LARGE_FILES,
    DUPLICATES
}

data class JunkItem(
    val file: File,
    val name: String,
    val size: Long,
    val category: JunkCategory,
    var isSelected: Boolean = true
)

data class CleanerScanState(
    val isScanning: Boolean = false,
    val currentScannedPath: String = "",
    val items: List<JunkItem> = emptyList(),
    val totalBytesFound: Long = 0L,
    val isCleanFinished: Boolean = false,
    val bytesFreed: Long = 0L
)

class JunkCleanerEngine(private val context: Context) {

    private val _scanState = MutableStateFlow(CleanerScanState())
    val scanState: StateFlow<CleanerScanState> = _scanState.asStateFlow()

    suspend fun startScan() = withContext(Dispatchers.IO) {
        _scanState.value = CleanerScanState(isScanning = true)

        val junkList = mutableListOf<JunkItem>()
        val sizeMap = mutableMapOf<Long, MutableList<File>>()

        val rootDirs = listOf(
            Environment.getExternalStorageDirectory(),
            context.cacheDir,
            context.externalCacheDir
        ).filterNotNull()

        for (root in rootDirs) {
            scanDirectory(root, junkList, sizeMap)
        }

        // Find duplicates among files with matching size
        for ((size, files) in sizeMap) {
            if (files.size > 1 && size > 100 * 1024L) { // files > 100KB with same size
                val hashMap = mutableMapOf<String, File>()
                for (f in files) {
                    val hash = ChunkManager.calculateSha256(f, bufferSize = 32 * 1024)
                    if (hash.isNotBlank()) {
                        if (hashMap.containsKey(hash)) {
                            junkList.add(
                                JunkItem(
                                    file = f,
                                    name = f.name,
                                    size = f.length(),
                                    category = JunkCategory.DUPLICATES
                                )
                            )
                        } else {
                            hashMap[hash] = f
                        }
                    }
                }
            }
        }

        val totalBytes = junkList.sumOf { it.size }
        _scanState.value = CleanerScanState(
            isScanning = false,
            items = junkList,
            totalBytesFound = totalBytes
        )
    }

    private fun scanDirectory(dir: File, result: MutableList<JunkItem>, sizeMap: MutableMap<Long, MutableList<File>>) {
        if (!dir.exists() || !dir.canRead()) return

        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                // Check for empty folders
                val children = f.list()
                if (children != null && children.isEmpty() && !f.name.startsWith(".")) {
                    result.add(JunkItem(f, f.name, 0L, JunkCategory.TEMP_FILES))
                } else if (!f.name.startsWith(".") && f.name != "Android") {
                    scanDirectory(f, result, sizeMap)
                }
            } else {
                val name = f.name.lowercase()
                val len = f.length()

                // (a) Temp & residual files
                if (name.endsWith(".tmp") || name.endsWith(".log") || name.endsWith(".bak") || name.endsWith(".cache") || name.endsWith(".thumb")) {
                    result.add(JunkItem(f, f.name, len, JunkCategory.TEMP_FILES))
                }
                // (b) Obsolete APK installers
                else if (name.endsWith(".apk") && f.parent?.contains("Download", ignoreCase = true) == true) {
                    result.add(JunkItem(f, f.name, len, JunkCategory.OBSOLETE_APKS))
                }
                // (c) Large files (> 50 MB)
                else if (len >= 50 * 1024 * 1024L) {
                    result.add(JunkItem(f, f.name, len, JunkCategory.LARGE_FILES, isSelected = false))
                }

                // Track sizes for duplicate detection
                if (len in (100 * 1024L)..(50 * 1024 * 1024L)) {
                    sizeMap.getOrPut(len) { mutableListOf() }.add(f)
                }
            }
        }
    }

    suspend fun cleanSelected(itemsToClean: List<JunkItem>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (item in itemsToClean) {
            val len = item.file.length()
            val deleted = try {
                if (item.file.isDirectory) item.file.deleteRecursively() else item.file.delete()
            } catch (_: Exception) {
                false
            }
            if (deleted) {
                freed += len
            }
        }

        _scanState.value = _scanState.value.copy(
            isCleanFinished = true,
            bytesFreed = freed,
            items = _scanState.value.items.filterNot { itemsToClean.contains(it) }
        )
        freed
    }
}

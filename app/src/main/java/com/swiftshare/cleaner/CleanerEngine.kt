package com.swiftshare.cleaner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class CleanerCategory(val titleEn: String, val titleAr: String, val icon: String) {
    CACHE_TEMP("Cache & Temp Files", "ملفات التخزين المؤقت", "🧹"),
    OBSOLETE_APKS("Obsolete APKs", "حزم التثبيت القديمة", "📦"),
    APP_LEFTOVERS("App Leftovers", "بقايا التطبيقات المحذوفة", "🗑️"),
    LARGE_FILES("Large Files (>50MB)", "الملفات الكبيرة", "📁"),
    DUPLICATES("Duplicate Files", "الملفات المكررة", "👥")
}

data class CleanableItem(
    val file: File,
    val name: String,
    val size: Long,
    val category: CleanerCategory,
    var isSelected: Boolean = true,
    val detail: String? = null,
    val duplicateOriginalPath: String? = null
)

data class CleanerScanState(
    val isScanning: Boolean = false,
    val currentScannedPath: String = "",
    val items: List<CleanableItem> = emptyList(),
    val totalBytesFound: Long = 0L,
    val isCleanFinished: Boolean = false,
    val bytesFreed: Long = 0L
)

data class CleanResult(
    val freedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int
)

class CleanerEngine(
    private val context: Context? = null,
    private val rootDirsProvider: (() -> List<File>)? = null,
    private val installedPackagesProvider: (() -> Set<String>)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _scanState = MutableStateFlow(CleanerScanState())
    val scanState: StateFlow<CleanerScanState> = _scanState.asStateFlow()

    suspend fun startScan(): List<CleanableItem> = withContext(ioDispatcher) {
        _scanState.value = CleanerScanState(isScanning = true)

        val junkList = mutableListOf<CleanableItem>()
        val sizeMap = mutableMapOf<Long, MutableList<File>>()

        val rootDirs = rootDirsProvider?.invoke() ?: getProductionRootDirs()
        val installedPackages = installedPackagesProvider?.invoke() ?: getProductionInstalledPackages()

        for (root in rootDirs) {
            scanDirectory(root, junkList, sizeMap, installedPackages)
        }

        // Duplicate Detection: files > 100KB with identical sizes
        for ((size, files) in sizeMap) {
            if (files.size > 1 && size >= 100 * 1024L) {
                val hashMap = mutableMapOf<String, File>()
                val sortedFiles = files.sortedWith(compareBy({ it.lastModified() }, { it.absolutePath }))
                for (f in sortedFiles) {
                    if (isProtectedFile(f)) continue
                    val hash = calculateSha256(f)
                    if (hash.isNotBlank()) {
                        val original = hashMap[hash]
                        if (original != null) {
                            junkList.add(
                                CleanableItem(
                                    file = f,
                                    name = f.name,
                                    size = f.length(),
                                    category = CleanerCategory.DUPLICATES,
                                    isSelected = true,
                                    detail = "Duplicate of ${original.name}",
                                    duplicateOriginalPath = original.absolutePath
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
        junkList
    }

    private fun scanDirectory(
        dir: File,
        result: MutableList<CleanableItem>,
        sizeMap: MutableMap<Long, MutableList<File>>,
        installedPackages: Set<String>
    ) {
        if (!dir.exists() || !dir.canRead() || isProtectedFile(dir)) return

        val files = dir.listFiles() ?: return
        for (f in files) {
            if (isProtectedFile(f)) continue

            if (f.isDirectory) {
                val dirName = f.name
                // Check if directory is an uninstalled app leftover (e.g. in Android/data or Android/obb)
                val parentName = f.parentFile?.name?.lowercase()
                val isDataOrObb = parentName == "data" || parentName == "obb"
                val looksLikePackage = dirName.contains('.') && !dirName.startsWith('.')

                if (isDataOrObb && looksLikePackage && !installedPackages.contains(dirName)) {
                    val folderSize = calculateFolderSize(f)
                    result.add(
                        CleanableItem(
                            file = f,
                            name = dirName,
                            size = folderSize,
                            category = CleanerCategory.APP_LEFTOVERS,
                            isSelected = true,
                            detail = "Leftover of uninstalled package $dirName"
                        )
                    )
                } else {
                    // Check for empty folders
                    val children = f.list()
                    if (children != null && children.isEmpty() && !dirName.startsWith('.')) {
                        result.add(
                            CleanableItem(
                                file = f,
                                name = f.name,
                                size = 0L,
                                category = CleanerCategory.CACHE_TEMP,
                                isSelected = true,
                                detail = "Empty directory"
                            )
                        )
                    } else if (!dirName.startsWith('.')) {
                        scanDirectory(f, result, sizeMap, installedPackages)
                    }
                }
            } else {
                val name = f.name.lowercase()
                val len = f.length()

                // (a) Cache and temp files
                if (name.endsWith(".tmp") || name.endsWith(".log") || name.endsWith(".bak") ||
                    name.endsWith(".cache") || name.endsWith(".thumb") || name.endsWith(".part.meta")
                ) {
                    result.add(
                        CleanableItem(
                            file = f,
                            name = f.name,
                            size = len,
                            category = CleanerCategory.CACHE_TEMP,
                            isSelected = true,
                            detail = "Temporary residual file"
                        )
                    )
                }
                // (b) Obsolete APK installers
                else if (name.endsWith(".apk") && (f.parent?.contains("Download", ignoreCase = true) == true || f.parent?.contains("temp", ignoreCase = true) == true)) {
                    result.add(
                        CleanableItem(
                            file = f,
                            name = f.name,
                            size = len,
                            category = CleanerCategory.OBSOLETE_APKS,
                            isSelected = true,
                            detail = "Standalone package installer"
                        )
                    )
                }
                // (c) Large files (>= 50 MB) - UNSELECTED by default to avoid accidental data loss
                else if (len >= 50 * 1024 * 1024L) {
                    result.add(
                        CleanableItem(
                            file = f,
                            name = f.name,
                            size = len,
                            category = CleanerCategory.LARGE_FILES,
                            isSelected = false,
                            detail = "Large file (${len / (1024 * 1024)} MB)"
                        )
                    )
                }

                // Track sizes for duplicate detection
                if (len in (100 * 1024L)..(100 * 1024 * 1024L)) {
                    sizeMap.getOrPut(len) { mutableListOf() }.add(f)
                }
            }
        }
    }

    suspend fun cleanSelected(itemsToClean: List<CleanableItem>): CleanResult = withContext(ioDispatcher) {
        var freed = 0L
        var deleted = 0
        var failed = 0

        for (item in itemsToClean) {
            if (isProtectedFile(item.file)) {
                failed++
                continue
            }
            val len = if (item.file.isDirectory) item.size else item.file.length()
            val success = try {
                if (item.file.isDirectory) item.file.deleteRecursively() else item.file.delete()
            } catch (e: Exception) {
                false
            }
            if (success) {
                freed += len
                deleted++
            } else {
                failed++
            }
        }

        val remaining = _scanState.value.items.filterNot { itemsToClean.contains(it) }
        _scanState.value = _scanState.value.copy(
            isCleanFinished = true,
            bytesFreed = freed,
            items = remaining,
            totalBytesFound = remaining.sumOf { it.size }
        )
        CleanResult(freed, deleted, failed)
    }

    fun isProtectedFile(file: File): Boolean {
        val path = file.absolutePath.lowercase().replace('\\', '/')
        return path.contains("/.safebox") ||
            path.contains("/safebox") ||
            path.contains(".vault") ||
            path.contains("/.git") ||
            path.contains("/dcim/camera") ||
            path.contains("/android/system") ||
            path.contains("/system/")
    }

    private fun calculateFolderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateFolderSize(f) else f.length()
        }
        return size
    }

    fun calculateSha256(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(32 * 1024)
                var bytesRead: Int
                while (input.read(buf).also { bytesRead = it } != -1) {
                    md.update(buf, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getProductionRootDirs(): List<File> {
        val list = mutableListOf<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { list.add(it) }
        } catch (_: Exception) {}
        context?.let { ctx ->
            ctx.cacheDir?.let { list.add(it) }
            ctx.externalCacheDir?.let { list.add(it) }
        }
        return list
    }

    private fun getProductionInstalledPackages(): Set<String> {
        val set = mutableSetOf<String>()
        val pm = context?.packageManager ?: return set
        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                set.add(app.packageName)
            }
        } catch (_: Exception) {}
        return set
    }
}


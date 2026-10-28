package com.swiftshare.explorer.zip

import com.swiftshare.common.AppLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ZipEntryItem(
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc: Long,
    val lastModified: Long
)

data class ZipContent(
    val file: File,
    val totalEntries: Int,
    val totalUncompressedBytes: Long,
    val entries: List<ZipEntryItem>
)

data class ExtractionResult(
    val extractedFiles: List<File>,
    val skippedSuspiciousFiles: List<String>,
    val totalBytesExtracted: Long
)

object ZipExplorer {

    fun browse(zipFile: File): ZipContent {
        if (!zipFile.exists() || !zipFile.isFile) {
            return ZipContent(zipFile, 0, 0L, emptyList())
        }

        val entriesList = mutableListOf<ZipEntryItem>()
        var totalBytes = 0L

        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val size = if (entry.size >= 0) entry.size else 0L
                val compressed = if (entry.compressedSize >= 0) entry.compressedSize else 0L
                if (!entry.isDirectory) {
                    totalBytes += size
                }
                entriesList.add(
                    ZipEntryItem(
                        name = entry.name,
                        isDirectory = entry.isDirectory,
                        compressedSize = compressed,
                        uncompressedSize = size,
                        crc = entry.crc,
                        lastModified = entry.time
                    )
                )
            }
        }

        return ZipContent(
            file = zipFile,
            totalEntries = entriesList.size,
            totalUncompressedBytes = totalBytes,
            entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        )
    }

    fun previewText(zipFile: File, entryName: String, maxBytes: Int = 128 * 1024): String? {
        if (!zipFile.exists() || !zipFile.isFile) return null

        ZipFile(zipFile).use { zf ->
            val entry = zf.getEntry(entryName) ?: return null
            if (entry.isDirectory) return null

            zf.getInputStream(entry).use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read <= 0) return ""
                return String(buffer, 0, read, Charsets.UTF_8)
            }
        }
    }

    fun previewBytes(zipFile: File, entryName: String, maxBytes: Int = 2 * 1024 * 1024): ByteArray? {
        if (!zipFile.exists() || !zipFile.isFile) return null

        ZipFile(zipFile).use { zf ->
            val entry = zf.getEntry(entryName) ?: return null
            if (entry.isDirectory) return null

            zf.getInputStream(entry).use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read <= 0) return ByteArray(0)
                return buffer.copyOf(read)
            }
        }
    }

    /**
     * Extracts entries from [zipFile] into [targetDir] with strict Zip-Slip path traversal protection.
     */
    fun extract(
        zipFile: File,
        targetDir: File,
        filter: ((ZipEntryItem) -> Boolean)? = null
    ): ExtractionResult {
        if (!zipFile.exists()) {
            throw IllegalArgumentException("Source zip file does not exist: ${zipFile.absolutePath}")
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val canonicalTargetDir = targetDir.canonicalFile
        val canonicalTargetPath = canonicalTargetDir.canonicalPath
        val targetPathWithSep = if (canonicalTargetPath.endsWith(File.separator)) {
            canonicalTargetPath
        } else {
            canonicalTargetPath + File.separator
        }

        val extracted = mutableListOf<File>()
        val skippedSuspicious = mutableListOf<String>()
        var totalBytes = 0L

        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryItem = ZipEntryItem(
                    name = entry.name,
                    isDirectory = entry.isDirectory,
                    compressedSize = entry.compressedSize,
                    uncompressedSize = entry.size,
                    crc = entry.crc,
                    lastModified = entry.time
                )

                if (filter != null && !filter(entryItem)) {
                    continue
                }

                // ── Zip-Slip Protection Check ───────────────────────────────
                // Normalize slashes (handling Windows backslashes in archive)
                val normalizedName = entry.name.replace('\\', '/')
                val destFile = File(canonicalTargetDir, normalizedName)
                val canonicalDestPath = destFile.canonicalPath

                if (!canonicalDestPath.startsWith(targetPathWithSep) && canonicalDestPath != canonicalTargetPath) {
                    AppLogger.w(
                        message = "SECURITY ALERT: Zip-Slip traversal blocked for entry '${entry.name}' escaping to '$canonicalDestPath'"
                    )
                    skippedSuspicious.add(entry.name)
                    continue
                }

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    val parent = destFile.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }

                    BufferedInputStream(zf.getInputStream(entry)).use { input ->
                        BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var count: Int
                            while (input.read(buffer).also { count = it } != -1) {
                                output.write(buffer, 0, count)
                                totalBytes += count
                            }
                            output.flush()
                        }
                    }
                    extracted.add(destFile)
                }
            }
        }

        return ExtractionResult(
            extractedFiles = extracted,
            skippedSuspiciousFiles = skippedSuspicious,
            totalBytesExtracted = totalBytes
        )
    }
}


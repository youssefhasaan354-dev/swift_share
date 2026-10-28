package com.swiftshare.common

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class PublishedFile(
    val uriString: String,
    val file: File? = null
) {
    val uri: Uri get() = try { Uri.parse(uriString) } catch (_: Throwable) { Uri.EMPTY }
}

data class ExistingFileEntry(
    val name: String,
    val size: Long,
    val uriString: String,
    val sha256: String? = null
)

interface ReceivedFilePublisher {
    /**
     * Publishes a fully verified .part file to its final storage destination.
     * On success, copies the data and MUST delete the source [partFile].
     * If publishing fails, throws an Exception and preserves [partFile] for retry.
     */
    suspend fun publish(
        partFile: File,
        finalName: String,
        mimeType: String,
        category: String = "files"
    ): PublishedFile

    /**
     * Checks if a file with the given name already exists at the destination.
     */
    fun findExisting(name: String): ExistingFileEntry? = null
}

/**
 * Default direct-file publisher for testing and legacy fallback environments.
 */
class DefaultFilePublisher(
    private val targetDir: File
) : ReceivedFilePublisher {

    override suspend fun publish(
        partFile: File,
        finalName: String,
        mimeType: String,
        category: String
    ): PublishedFile {
        if (!partFile.exists()) {
            throw IOException("Source part file does not exist: ${partFile.absolutePath}")
        }
        if (!targetDir.exists()) targetDir.mkdirs()

        val finalFile = SafeFileName.resolveUniqueFile(targetDir, finalName)
        if (!partFile.renameTo(finalFile)) {
            FileInputStream(partFile).use { input ->
                FileOutputStream(finalFile).use { output ->
                    input.copyTo(output)
                }
            }
            for (i in 0..30) {
                if (!partFile.exists() || partFile.delete()) break
                Thread.sleep(50)
            }
        }

        return PublishedFile("file://${finalFile.absolutePath}", finalFile)
    }

    override fun findExisting(name: String): ExistingFileEntry? {
        val f = File(targetDir, name)
        return if (f.exists() && f.isFile) {
            ExistingFileEntry(
                name = f.name,
                size = f.length(),
                uriString = "file://${f.absolutePath}"
            )
        } else null
    }
}

/**
 * Production publisher using Scoped Storage (MediaStore) on Android 10+ (API 29+)
 * with IS_PENDING = 1 during write, transitioning to IS_PENDING = 0 on success,
 * and falling back to direct-file / SD-card destination on legacy APIs or when selected.
 */
class ProductionReceivedFilePublisher(
    private val context: Context,
    private val destinationVolumeTypeProvider: () -> StorageVolumeType = {
        StorageDestinationManager.selectedVolume.value
    }
) : ReceivedFilePublisher {

    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun publish(
        partFile: File,
        finalName: String,
        mimeType: String,
        category: String
    ): PublishedFile {
        if (!partFile.exists()) {
            throw IOException("Source part file does not exist: ${partFile.absolutePath}")
        }

        val volumeType = destinationVolumeTypeProvider()

        // 1. Android 10+ (API 29+) MediaStore for Internal Storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volumeType == StorageVolumeType.INTERNAL) {
            try {
                val (collectionUri, relativePath) = resolveCollectionAndPath(mimeType, "SwiftShare")
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = contentResolver.insert(collectionUri, values)
                    ?: throw IOException("Failed to create MediaStore entry for $finalName")

                try {
                    contentResolver.openOutputStream(itemUri, "w").use { output ->
                        if (output == null) throw IOException("Failed to open output stream for MediaStore item $itemUri")
                        FileInputStream(partFile).use { input ->
                            input.copyTo(output)
                        }
                        output.flush()
                    }

                    // Mark file ready
                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    contentResolver.update(itemUri, publishValues, null, null)

                    // On success, delete the source .part file
                    partFile.delete()

                    return PublishedFile(itemUri.toString(), null)
                } catch (e: Exception) {
                    // Clean up incomplete pending MediaStore entry if stream copy failed
                    try { contentResolver.delete(itemUri, null, null) } catch (_: Exception) {}
                    throw e
                }
            } catch (e: Exception) {
                AppLogger.e("ReceivedFilePublisher", "MediaStore publish failed, falling back to direct file: ${e.message}", e)
                // Fall through to direct-file fallback if MediaStore fails
            }
        }

        // 2. Direct File / Legacy / SD-card fallback
        val downloadDir = StorageDestinationManager.getDestinationDirectory(context)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val finalFile = SafeFileName.resolveUniqueFile(downloadDir, finalName)

        try {
            if (!partFile.renameTo(finalFile)) {
                FileInputStream(partFile).use { input ->
                    FileOutputStream(finalFile).use { output ->
                        input.copyTo(output)
                    }
                }
                partFile.delete()
            }
            return PublishedFile("file://${finalFile.absolutePath}", finalFile)
        } catch (e: Exception) {
            AppLogger.e("ReceivedFilePublisher", "Direct file publish failed: ${e.message}", e)
            throw e
        }
    }

    override fun findExisting(name: String): ExistingFileEntry? {
        val volumeType = destinationVolumeTypeProvider()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volumeType == StorageVolumeType.INTERNAL) {
            try {
                val collectionUri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE
                )
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
                val selectionArgs = arrayOf(name)

                contentResolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                        val id = cursor.getLong(idIdx)
                        val foundName = cursor.getString(nameIdx)
                        val size = cursor.getLong(sizeIdx)
                        val itemUri = ContentUris.withAppendedId(collectionUri, id)

                        return ExistingFileEntry(
                            name = foundName,
                            size = size,
                            uriString = itemUri.toString()
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ReceivedFilePublisher", "MediaStore query failed: ${e.message}", e)
            }
        }

        // Fallback: check destination directory
        val downloadDir = StorageDestinationManager.getDestinationDirectory(context)
        val file = File(downloadDir, name)
        return if (file.exists() && file.isFile) {
            ExistingFileEntry(
                name = file.name,
                size = file.length(),
                uriString = "file://${file.absolutePath}"
            )
        } else null
    }

    private fun resolveCollectionAndPath(mimeType: String, subDirName: String): Pair<Uri, String> {
        return when {
            mimeType.startsWith("image/", ignoreCase = true) -> {
                Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/$subDirName")
            }
            mimeType.startsWith("video/", ignoreCase = true) -> {
                Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/$subDirName")
            }
            mimeType.startsWith("audio/", ignoreCase = true) -> {
                Pair(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Music/$subDirName")
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Pair(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/$subDirName")
                } else {
                    @Suppress("DEPRECATION")
                    Pair(MediaStore.Files.getContentUri("external"), "Download/$subDirName")
                }
            }
        }
    }
}

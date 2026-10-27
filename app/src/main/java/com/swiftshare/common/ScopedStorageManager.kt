package com.swiftshare.common

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Manages Scoped Storage on Android 10+ (API 29+, targetSdk 35) via MediaStore with IS_PENDING = 1,
 * random-access chunk writing via FileDescriptor / Os.pwrite, SAF fallback, and legacy File fallback.
 */
class ScopedStorageManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    data class PendingTarget(
        val uri: Uri?,
        val file: File?,
        val pfd: ParcelFileDescriptor?,
        val channel: FileChannel?,
        val isMediaStore: Boolean
    )

    /**
     * Creates a pending destination file. On API 29+, registers in MediaStore with IS_PENDING = 1.
     * On legacy Android (API < 29) or if MediaStore fails, falls back to direct File or SAF.
     */
    fun createPendingFile(
        fileName: String,
        mimeType: String,
        subDirName: String = "SwiftShare",
        safTreeUri: Uri? = null
    ): PendingTarget {
        // 1. If SAF tree URI is explicitly provided, use SAF DocumentFile
        if (safTreeUri != null) {
            try {
                val treeDoc = DocumentFile.fromTreeUri(context, safTreeUri)
                if (treeDoc != null && treeDoc.canWrite()) {
                    val subDir = treeDoc.findFile(subDirName) ?: treeDoc.createDirectory(subDirName)
                    val targetDoc = subDir?.createFile(mimeType, fileName)
                    if (targetDoc != null) {
                        val pfd = contentResolver.openFileDescriptor(targetDoc.uri, "rw")
                        val channel = pfd?.let { FileOutputStream(it.fileDescriptor).channel }
                        return PendingTarget(
                            uri = targetDoc.uri,
                            file = null,
                            pfd = pfd,
                            channel = channel,
                            isMediaStore = false
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ScopedStorage", "SAF creation failed, falling back to MediaStore: ${e.message}", e)
            }
        }

        // 2. Android 10+ (API 29+): Use MediaStore with IS_PENDING = 1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val (collectionUri, relativePath) = resolveCollectionAndPath(mimeType, subDirName)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = contentResolver.insert(collectionUri, values)
                if (itemUri != null) {
                    val pfd = contentResolver.openFileDescriptor(itemUri, "rw")
                    val channel = pfd?.let { FileOutputStream(it.fileDescriptor).channel }
                    return PendingTarget(
                        uri = itemUri,
                        file = null,
                        pfd = pfd,
                        channel = channel,
                        isMediaStore = true
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("ScopedStorage", "MediaStore creation failed, falling back to direct file: ${e.message}", e)
            }
        }

        // 3. Fallback: Direct File in Downloads/SwiftShare (Legacy API < 29 or storage permission granted)
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subDirName)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val destFile = File(downloadDir, fileName)
        val raf = RandomAccessFile(destFile, "rw")
        return PendingTarget(
            uri = Uri.fromFile(destFile),
            file = destFile,
            pfd = null,
            channel = raf.channel,
            isMediaStore = false
        )
    }

    /**
     * Writes a chunk at a given offset using the target's FileChannel or Os.pwrite.
     */
    fun writeChunk(target: PendingTarget, offset: Long, data: ByteArray, length: Int) {
        val channel = target.channel
        if (channel != null) {
            val buf = ByteBuffer.wrap(data, 0, length)
            channel.write(buf, offset)
        } else if (target.pfd != null) {
            // Low-level Os.pwrite fallback if channel is unavailable
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    android.system.Os.pwrite(target.pfd.fileDescriptor, data, 0, length, offset)
                }
            } catch (e: Exception) {
                throw java.io.IOException("Os.pwrite failed at offset $offset: ${e.message}", e)
            }
        }
    }

    /**
     * Publishes a completed and verified file.
     * Sets IS_PENDING = 0 on Android 10+ MediaStore, closes file descriptors, and returns public URI.
     */
    fun publishFile(target: PendingTarget): Uri? {
        try {
            target.channel?.force(true)
            target.channel?.close()
        } catch (_: Exception) {}
        try {
            target.pfd?.close()
        } catch (_: Exception) {}

        if (target.isMediaStore && target.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(target.uri, publishValues, null, null)
        }

        return target.uri
    }

    /**
     * Aborts an incomplete or corrupted transfer.
     * Closes file descriptors and removes the pending MediaStore item or partial file.
     */
    fun abortFile(target: PendingTarget) {
        try {
            target.channel?.close()
        } catch (_: Exception) {}
        try {
            target.pfd?.close()
        } catch (_: Exception) {}

        if (target.isMediaStore && target.uri != null) {
            try {
                contentResolver.delete(target.uri, null, null)
            } catch (_: Exception) {}
        } else if (target.file != null && target.file.exists()) {
            try {
                target.file.delete()
            } catch (_: Exception) {}
        }
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
                    Pair(MediaStore.Files.getContentUri("external"), "Download/$subDirName")
                }
            }
        }
    }
}

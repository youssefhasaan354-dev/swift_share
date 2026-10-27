package com.swiftshare.statussaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val size: Long,
    val file: File? = null
)

class StatusSaverManager(private val context: Context) {

    private val legacyStatusPaths = listOf(
        File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses"),
        File(Environment.getExternalStorageDirectory(), "WhatsApp Business/Media/.Statuses"),
        File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
        File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses")
    )

    suspend fun getStatuses(treeUri: Uri? = null): List<StatusItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StatusItem>()

        // 1. Try legacy direct file paths first
        for (dir in legacyStatusPaths) {
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.endsWith(".nomedia")) {
                        val isVid = file.name.endsWith(".mp4", ignoreCase = true)
                        val isImg = file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".jpeg", ignoreCase = true) || file.name.endsWith(".png", ignoreCase = true)
                        if (isVid || isImg) {
                            list.add(
                                StatusItem(
                                    uri = Uri.fromFile(file),
                                    name = file.name,
                                    isVideo = isVid,
                                    size = file.length(),
                                    file = file
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Try Scoped Storage SAF DocumentTree if provided
        if (treeUri != null) {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                documentFile?.listFiles()?.forEach { doc ->
                    val name = doc.name ?: ""
                    val isVid = name.endsWith(".mp4", ignoreCase = true)
                    val isImg = name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true)
                    if (isVid || isImg) {
                        list.add(
                            StatusItem(
                                uri = doc.uri,
                                name = name,
                                isVideo = isVid,
                                size = doc.length()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                com.swiftshare.common.AppLogger.e("StatusSaver", "Failed to query status folder: ${e.message}", e)
            }
        }

        list.distinctBy { it.name }
    }

    suspend fun saveToGallery(status: StatusItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val contentUri = if (status.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Status_" + status.name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (status.isVideo) "video/mp4" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (status.isVideo) Environment.DIRECTORY_MOVIES + "/SwiftShare" else Environment.DIRECTORY_PICTURES + "/SwiftShare")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val insertedUri = contentResolver.insert(contentUri, values) ?: return@withContext false

            contentResolver.openInputStream(status.uri)?.use { input ->
                contentResolver.openOutputStream(insertedUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(insertedUri, values, null, null)
            }
            true
        } catch (e: Exception) {
            com.swiftshare.common.AppLogger.e("StatusSaver", "Failed to save status to gallery: ${e.message}", e, notifyUser = true)
            false
        }
    }
}

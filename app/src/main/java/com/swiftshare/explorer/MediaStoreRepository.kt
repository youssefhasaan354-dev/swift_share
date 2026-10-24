package com.swiftshare.explorer

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

data class MediaItem(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val category: String, // photos, videos, music, apps, docs
    val thumbnailUri: String? = null,
    val packageName: String? = null,
    val isSelected: Boolean = false,
    val durationMs: Long? = null
)

class MediaStoreRepository(private val context: Context) {

    // LruCache for app icons (keyed by packageName or apkPath)
    private val iconCache = LruCache<String, ImageBitmap>(128)

    // --------------- App Icon Loader ---------------

    fun loadAppIconBitmap(packageName: String?, apkPath: String?): ImageBitmap? {
        val cacheKey = packageName ?: apkPath ?: return null
        iconCache.get(cacheKey)?.let { return it }

        return try {
            val drawable: Drawable? = when {
                packageName != null -> {
                    try {
                        context.packageManager.getApplicationIcon(packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        apkPath?.let { loadApkIcon(it) }
                    }
                }
                apkPath != null -> loadApkIcon(apkPath)
                else -> null
            }
            drawable?.let { d ->
                val bmp = drawableToBitmap(d)
                val imgBitmap = bmp.asImageBitmap()
                iconCache.put(cacheKey, imgBitmap)
                imgBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadApkIcon(apkPath: String): Drawable? {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkPath, 0)
            }
            pi?.applicationInfo?.let { ai ->
                ai.sourceDir = apkPath
                ai.publicSourceDir = apkPath
                ai.loadIcon(context.packageManager)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    // --------------- Photo Thumbnail ---------------

    fun loadPhotoThumbnail(contentUri: Uri): ImageBitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bmp = context.contentResolver.loadThumbnail(contentUri, Size(256, 256), null)
                bmp.asImageBitmap()
            } else {
                val id = ContentUris.parseId(contentUri)
                @Suppress("DEPRECATION")
                val bmp = MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver, id,
                    MediaStore.Images.Thumbnails.MINI_KIND, null
                )
                bmp?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    // --------------- Video Thumbnail ---------------

    fun loadVideoThumbnail(path: String, contentUri: Uri): ImageBitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bmp = context.contentResolver.loadThumbnail(contentUri, Size(256, 256), null)
                bmp.asImageBitmap()
            } else {
                @Suppress("DEPRECATION")
                val bmp = ThumbnailUtils.createVideoThumbnail(
                    path,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
                bmp?.asImageBitmap()
            }
        } catch (e: Exception) {
            // Fallback: MediaMetadataRetriever
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                val scaled = frame?.let {
                    Bitmap.createScaledBitmap(it, 256, 256, true)
                }
                scaled?.asImageBitmap()
            } catch (e2: Exception) {
                null
            }
        }
    }

    // --------------- Audio Album Art ---------------

    fun loadAudioAlbumArt(path: String): ImageBitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val artBytes = retriever.embeddedPicture
            retriever.release()
            artBytes?.let {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                val scaled = Bitmap.createScaledBitmap(bmp, 256, 256, true)
                scaled.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    // --------------- Get video duration ms ---------------

    fun getVideoDurationMs(path: String): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            dur
        } catch (e: Exception) {
            null
        }
    }

    // --------------- MediaStore Queries ---------------

    fun getPhotos(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Photo"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: ""
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/jpeg"
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

            list.add(MediaItem(id.toString(), name, path, size, mime, "photos", contentUri.toString()))
        }
        return list
    }

    fun getVideos(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "Video"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: ""
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/mp4"
            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            val durationMs = try {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
            } catch (e: Exception) { 0L }

            list.add(MediaItem(id.toString(), name, path, size, mime, "videos", contentUri.toString(), durationMs = durationMs))
        }
        return list
    }

    fun getAudio(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)) ?: "Song"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: "audio/mpeg"
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

            list.add(MediaItem(id.toString(), name, path, size, mime, "music", contentUri.toString()))
        }
        return list
    }

    fun getInstalledApps(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in installedApps) {
            // Filter out system apps unless updated
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (!isSystem || isUpdatedSystem) {
                val name = pm.getApplicationLabel(app).toString()
                val file = File(app.sourceDir)
                if (file.exists()) {
                    list.add(
                        MediaItem(
                            id = app.packageName,
                            name = name,
                            path = app.sourceDir,
                            size = file.length(),
                            mimeType = "application/vnd.android.package-archive",
                            category = "apps",
                            packageName = app.packageName
                        )
                    )
                }
            }
        }
        return list.sortedBy { it.name.lowercase() }
    }

    fun getDocuments(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val extUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%pdf%", "%text%", "%.zip", "%.docx")

        queryMediaStore(extUri, projection, selection, selectionArgs, null) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: "Document"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)) ?: ""
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: "application/octet-stream"

            list.add(MediaItem(id.toString(), name, path, size, mime, "docs"))
        }
        return list
    }

    fun getFileById(id: String?): File? {
        if (id == null) return null
        // Check if id is a path directly
        val directFile = File(id)
        if (directFile.exists()) return directFile

        // Search in all categories
        val all = getPhotos() + getVideos() + getAudio() + getInstalledApps() + getDocuments()
        val found = all.find { it.id == id } ?: return null
        val f = File(found.path)
        return if (f.exists()) f else null
    }

    private inline fun queryMediaStore(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        block: (Cursor) -> Unit
    ) {
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    block(cursor)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

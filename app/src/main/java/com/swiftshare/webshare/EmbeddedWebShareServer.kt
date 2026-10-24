package com.swiftshare.webshare

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.nft.channel.models.FileManifestItem
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmbeddedWebShareServer(private val context: Context) {

    private var engine: ApplicationEngine? = null
    private val mediaRepo = MediaStoreRepository(context)

    private val _queuedFiles = MutableStateFlow<List<Pair<FileManifestItem, File>>>(emptyList())
    val queuedFiles: StateFlow<List<Pair<FileManifestItem, File>>> = _queuedFiles.asStateFlow()

    private val _activeSessionToken = MutableStateFlow("")
    val activeSessionToken: StateFlow<String> = _activeSessionToken.asStateFlow()

    fun setQueuedFiles(files: List<Pair<FileManifestItem, File>>) {
        _queuedFiles.value = files
    }

    fun start(port: Int = Constants.PORT_CONTROL_HTTP, sessionToken: String = NetworkUtils.generateSessionToken()) {
        _activeSessionToken.value = sessionToken

        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(PartialContent)
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Range)
            }

            routing {
                // Landing page & WebShare UI
                get("/") {
                    call.respondText(loadAsset("web/webshare.html"), ContentType.Text.Html)
                }
                get("/webshare") {
                    call.respondText(loadAsset("web/webshare.html"), ContentType.Text.Html)
                }

                // iOS-optimized PWA portal
                get("/ios") {
                    call.respondText(loadAsset("web/webshare.html"), ContentType.Text.Html)
                }

                // Zero-Data APK Invite
                get("/invite") {
                    call.respondText(loadAsset("web/invite/home.html"), ContentType.Text.Html)
                }
                get("/apps/app-release.apk") {
                    val appSource = File(this@EmbeddedWebShareServer.context.applicationInfo.sourceDir)
                    if (appSource.exists()) {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "SwiftShare.apk").toString()
                        )
                        call.respondFile(appSource)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "APK not found")
                    }
                }

                // Manifest of queued files
                get("/api/manifest") {
                    val token = call.request.queryParameters["token"]
                    val freeSpace = Environment.getExternalStorageDirectory().freeSpace
                    val items = _queuedFiles.value.map { it.first }

                    val responseJson = buildJsonObject {
                        put("deviceName", android.os.Build.MODEL)
                        put("freeSpace", freeSpace)
                        put("tokenValid", token == _activeSessionToken.value)
                        put("files", Json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(FileManifestItem.serializer()), items))
                    }
                    call.respondText(responseJson.toString(), ContentType.Application.Json)
                }

                // Real thumbnail endpoint — serves PNG/JPEG thumbnail for any file ID
                get("/api/thumbnail/{id}") {
                    val fileId = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    // Look in queued files first
                    val pair = _queuedFiles.value.find { it.first.fileId == fileId }
                    if (pair != null) {
                        val manifestItem = pair.first
                        val file = pair.second
                        val thumbBytes = generateThumbnailBytes(manifestItem, file)
                        if (thumbBytes != null) {
                            call.response.header(HttpHeaders.CacheControl, "max-age=3600")
                            call.respondBytes(thumbBytes, ContentType.Image.PNG)
                            return@get
                        }
                    }
                    // Search by file path from MediaStore
                    val allItems = mediaRepo.getPhotos() + mediaRepo.getVideos() +
                            mediaRepo.getAudio() + mediaRepo.getInstalledApps()
                    val found = allItems.find { it.id == fileId }
                    if (found != null) {
                        val thumbBytes = generateMediaItemThumbnailBytes(found)
                        if (thumbBytes != null) {
                            call.response.header(HttpHeaders.CacheControl, "max-age=3600")
                            call.respondBytes(thumbBytes, ContentType.Image.PNG)
                            return@get
                        }
                    }
                    call.respond(HttpStatusCode.NotFound, "Thumbnail not available")
                }

                // Download individual file (supports HTTP Range)
                get("/api/download/{id}") {
                    val fileId = call.parameters["id"]
                    val pair = _queuedFiles.value.find { it.first.fileId == fileId }
                    if (pair != null && pair.second.exists()) {
                        val file = pair.second
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, pair.first.name).toString()
                        )
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File ID $fileId not found")
                    }
                }

                // Batch Download All as ZIP
                get("/api/download/all") {
                    val filesToZip = _queuedFiles.value.filter { it.second.exists() }
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "SwiftShare_Bundle.zip").toString()
                    )
                    call.respondOutputStream(ContentType.Application.Zip) {
                        ZipOutputStream(this).use { zipOut ->
                            val buffer = ByteArray(64 * 1024)
                            filesToZip.forEach { (_, file) ->
                                zipOut.putNextEntry(ZipEntry(file.name))
                                file.inputStream().use { input ->
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        zipOut.write(buffer, 0, read)
                                    }
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }

                // Upload files from browser to phone
                post("/api/upload") {
                    val multipart = call.receiveMultipart()
                    val uploadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftShare")
                    if (!uploadDir.exists()) uploadDir.mkdirs()

                    var uploadedCount = 0
                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val destination = com.swiftshare.common.SafeFileName.resolveUniqueFile(uploadDir, part.originalFileName)
                            part.streamProvider().use { input ->
                                destination.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            uploadedCount++
                        }
                        part.dispose()
                    }
                    call.respondText("{\"status\":\"success\",\"uploadedCount\":$uploadedCount}", ContentType.Application.Json)
                }
            }
        }.start(wait = false)
    }

    private fun generateThumbnailBytes(item: FileManifestItem, file: File): ByteArray? {
        return try {
            // Try base64 thumbnail from manifest first
            item.thumbnailBase64?.let { b64 ->
                if (b64.isNotBlank()) {
                    return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                }
            }
            // Generate from file
            val mime = item.mimeType
            val bmp: Bitmap? = when {
                mime.startsWith("image/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(Uri.fromFile(file), Size(256, 256), null)
                    } else {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.let {
                            Bitmap.createScaledBitmap(it, 256, 256, true)
                        }
                    }
                }
                mime.startsWith("video/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(Uri.fromFile(file), Size(256, 256), null)
                    } else {
                        @Suppress("DEPRECATION")
                        android.media.ThumbnailUtils.createVideoThumbnail(
                            file.absolutePath,
                            android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                        )
                    }
                }
                mime == "application/vnd.android.package-archive" -> {
                    mediaRepo.loadAppIconBitmap(item.name, file.absolutePath)?.let { imgBmp ->
                        // Convert ImageBitmap to Bitmap via canvas
                        val bmpResult = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                        bmpResult
                    }
                }
                else -> null
            }
            bmp?.let {
                val baos = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.PNG, 90, baos)
                baos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateMediaItemThumbnailBytes(item: com.swiftshare.explorer.MediaItem): ByteArray? {
        return try {
            val imgBitmap = when (item.category) {
                "photos" -> item.thumbnailUri?.let { mediaRepo.loadPhotoThumbnail(Uri.parse(it)) }
                "videos" -> item.thumbnailUri?.let { mediaRepo.loadVideoThumbnail(item.path, Uri.parse(it)) }
                "music" -> mediaRepo.loadAudioAlbumArt(item.path)
                "apps" -> mediaRepo.loadAppIconBitmap(item.packageName, item.path)
                else -> null
            } ?: return null

            // Convert ImageBitmap to ByteArray
            val androidBitmap = imgBitmap.asAndroidBitmap()
            val baos = ByteArrayOutputStream()
            androidBitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAsset(path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>SwiftShare WebPortal</h1><p>Asset error: ${e.message}</p></body></html>"
        }
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }
}

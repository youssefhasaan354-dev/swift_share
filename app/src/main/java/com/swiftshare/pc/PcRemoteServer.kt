package com.swiftshare.pc

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import com.swiftshare.common.Constants
import com.swiftshare.common.NetworkUtils
import com.swiftshare.explorer.MediaStoreRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File

data class RemotePptEvent(
    val action: String,
    val laserX: Float = 0f,
    val laserY: Float = 0f
)

class PcRemoteServer(
    private val context: Context,
    private val mediaRepo: MediaStoreRepository
) {

    private var engine: ApplicationEngine? = null

    private val _pptEvents = MutableSharedFlow<RemotePptEvent>(extraBufferCapacity = 64)
    val pptEvents: SharedFlow<RemotePptEvent> = _pptEvents.asSharedFlow()

    fun start(port: Int = Constants.PORT_WEB_PC, sessionToken: String = NetworkUtils.generateSessionToken()) {
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(PartialContent)
            install(WebSockets)
            install(CORS) {
                allowMethod(io.ktor.http.HttpMethod.Get)
                allowMethod(io.ktor.http.HttpMethod.Post)
                allowMethod(io.ktor.http.HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Range)
                allowHeader(HttpHeaders.Cookie)
                allowCredentials = true
            }

            routing {
                // PC Web Dashboard
                get("/pc") {
                    val html = loadAsset("web/pc/client.html")
                    call.respondText(html, ContentType.Text.Html)
                }

                // Remote View category files query
                get("/api/remote/files") {
                    val category = call.request.queryParameters["category"] ?: "photos"
                    val items = when (category) {
                        "photos" -> mediaRepo.getPhotos()
                        "videos" -> mediaRepo.getVideos()
                        "music" -> mediaRepo.getAudio()
                        "apps" -> mediaRepo.getInstalledApps()
                        "docs" -> mediaRepo.getDocuments()
                        else -> emptyList()
                    }

                    val jsonArray = buildJsonArray {
                        items.forEach { item ->
                            add(buildJsonObject {
                                put("id", item.id)
                                put("name", item.name)
                                put("size", item.size)
                                put("type", item.category)
                                put("thumbnail", "/api/thumbnail/${item.id}")
                                put("thumbnailLegacy", item.thumbnailUri ?: "")
                            })
                        }
                    }
                    call.respondText(jsonArray.toString(), ContentType.Application.Json)
                }

                // Real thumbnail for any media file by ID — serves PNG
                get("/api/thumbnail/{id}") {
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val allItems = mediaRepo.getPhotos() + mediaRepo.getVideos() +
                            mediaRepo.getAudio() + mediaRepo.getInstalledApps()
                    val found = allItems.find { it.id == id }
                    if (found != null) {
                        val bytes = renderThumbnailBytes(found)
                        if (bytes != null) {
                            call.response.header(HttpHeaders.CacheControl, "max-age=3600")
                            call.respondBytes(bytes, ContentType.Image.PNG)
                            return@get
                        }
                    }
                    call.respond(HttpStatusCode.NotFound, "Thumbnail not available")
                }

                // PlayTo & Remote View media streaming (HTTP Range)
                get("/api/remote/stream/{id}") {
                    val id = call.parameters["id"]
                    val file = mediaRepo.getFileById(id)
                    if (file != null && file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respondText("File not found", status = io.ktor.http.HttpStatusCode.NotFound)
                    }
                }

                get("/api/remote/download/{id}") {
                    val id = call.parameters["id"]
                    val file = mediaRepo.getFileById(id)
                    if (file != null && file.exists()) {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${file.name}\""
                        )
                        call.respondFile(file)
                    } else {
                        call.respondText("File not found", status = io.ktor.http.HttpStatusCode.NotFound)
                    }
                }

                // PPT Remote HTTP Endpoint
                post("/api/ppt/control") {
                    val body = call.receiveText()
                    try {
                        val obj = Json.parseToJsonElement(body) as? JsonObject
                        val action = obj?.get("action")?.jsonPrimitive?.content ?: "UNKNOWN"
                        _pptEvents.tryEmit(RemotePptEvent(action = action))
                        call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText("{\"error\":\"${e.message}\"}", status = io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }

                // PPT Remote & Laser Pointer WebSocket
                webSocket("/api/ppt/ws") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val obj = Json.parseToJsonElement(text) as? JsonObject
                                val type = obj?.get("type")?.jsonPrimitive?.content
                                when (type) {
                                    "PPT_COMMAND" -> {
                                        val action = obj.get("action")?.jsonPrimitive?.content ?: ""
                                        _pptEvents.tryEmit(RemotePptEvent(action = action))
                                    }
                                    "LASER_MOVE" -> {
                                        val x = obj.get("x")?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                                        val y = obj.get("y")?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                                        _pptEvents.tryEmit(RemotePptEvent(action = "LASER_MOVE", laserX = x, laserY = y))
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    private fun renderThumbnailBytes(item: com.swiftshare.explorer.MediaItem): ByteArray? {
        return try {
            val bitmap: Bitmap? = when (item.category) {
                "photos" -> {
                    val uri = item.thumbnailUri?.let { Uri.parse(it) } ?: return null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                    } else {
                        val id = android.content.ContentUris.parseId(uri)
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver, id,
                            android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null
                        )
                    }
                }
                "videos" -> {
                    val uri = item.thumbnailUri?.let { Uri.parse(it) }
                    if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                    } else {
                        @Suppress("DEPRECATION")
                        android.media.ThumbnailUtils.createVideoThumbnail(
                            item.path,
                            android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                        )
                    }
                }
                "music" -> {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(item.path)
                    val art = retriever.embeddedPicture
                    retriever.release()
                    art?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?.let { Bitmap.createScaledBitmap(it, 256, 256, true) }
                }
                "apps" -> {
                    // Render app icon via ImageBitmap
                    val imgBitmap = mediaRepo.loadAppIconBitmap(item.packageName, item.path)
                    imgBitmap?.let {
                        val androidBmp = it.asAndroidBitmap()
                        Bitmap.createScaledBitmap(androidBmp, 96, 96, true)
                    }
                }
                else -> null
            }
            bitmap?.let {
                val baos = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.PNG, 90, baos)
                baos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAsset(path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>SwiftShare PC Suite</h1><p>Asset error: ${e.message}</p></body></html>"
        }
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }
}

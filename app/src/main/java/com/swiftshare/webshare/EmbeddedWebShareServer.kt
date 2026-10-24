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
import com.swiftshare.common.SafeFileName
import com.swiftshare.explorer.MediaStoreRepository
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.pc.RemotePptEvent
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmbeddedWebShareServer(private val context: Context) {

    private var engine: ApplicationEngine? = null
    private val mediaRepo = MediaStoreRepository(context)

    val authManager = WebAuthManager()

    private val _queuedFiles = MutableStateFlow<List<Pair<FileManifestItem, File>>>(emptyList())
    val queuedFiles: StateFlow<List<Pair<FileManifestItem, File>>> = _queuedFiles.asStateFlow()

    private val _activeSessionToken = MutableStateFlow("")
    val activeSessionToken: StateFlow<String> = _activeSessionToken.asStateFlow()

    private val _pptEvents = MutableSharedFlow<RemotePptEvent>(extraBufferCapacity = 64)
    val pptEvents: SharedFlow<RemotePptEvent> = _pptEvents.asSharedFlow()

    fun setQueuedFiles(files: List<Pair<FileManifestItem, File>>) {
        _queuedFiles.value = files
    }

    fun start(port: Int = Constants.PORT_WEB_PC, sessionToken: String = NetworkUtils.generateSessionToken()) {
        _activeSessionToken.value = sessionToken

        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(PartialContent)
            install(WebSockets)
            install(CORS) {
                // Hardened CORS: allow standard P2P methods and headers without wildcard anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Range)
                allowHeader(HttpHeaders.Cookie)
                allowCredentials = true
                allowNonSimpleContentTypes = true
            }

            routing {
                // ── Authentication endpoints ──────────────────────────────────
                get("/login") {
                    val error = call.request.queryParameters["error"]
                    call.respondText(renderLoginPage(error), ContentType.Text.Html)
                }

                post("/api/auth/pin") {
                    val clientIp = getClientIp(call)
                    if (authManager.isIpLockedOut(clientIp)) {
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            "{\"error\":\"Too many failed attempts. Locked for 60 seconds.\"}"
                        )
                        return@post
                    }

                    val body = call.receiveText()
                    val pin = try {
                        val json = Json.parseToJsonElement(body) as? JsonObject
                        json?.get("pin")?.jsonPrimitive?.content ?: body
                    } catch (_: Exception) {
                        body
                    }.trim()

                    if (authManager.verifyPin(pin)) {
                        authManager.resetRateLimit(clientIp)
                        val sessionId = authManager.createAuthorizedSession()
                        call.response.cookies.append(
                            Cookie(
                                name = WebAuthManager.COOKIE_NAME,
                                value = sessionId,
                                httpOnly = true,
                                path = "/",
                                encoding = CookieEncoding.RAW
                            )
                        )
                        call.respondText("{\"status\":\"authorized\"}", ContentType.Application.Json)
                    } else {
                        val lockedOut = authManager.recordFailedAttempt(clientIp)
                        if (lockedOut) {
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                "{\"error\":\"Too many failed attempts. Locked for 60 seconds.\"}"
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                "{\"error\":\"Invalid PIN. Please check the code on your phone.\"}"
                            )
                        }
                    }
                }

                // ── Public invite & APK download (Zero-Data) ──────────────────
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

                // ── Landing Pages (WebShare & PC Dashboard) ───────────────────
                get("/") {
                    handlePageRequest(call, "web/webshare.html")
                }
                get("/webshare") {
                    handlePageRequest(call, "web/webshare.html")
                }
                get("/ios") {
                    handlePageRequest(call, "web/webshare.html")
                }
                get("/pc") {
                    handlePageRequest(call, "web/pc/client.html")
                }

                // ── Protected API endpoints (Require Valid Session) ───────────

                // Manifest of queued files
                get("/api/manifest") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
                    val freeSpace = Environment.getExternalStorageDirectory().freeSpace
                    val items = _queuedFiles.value.map { it.first }

                    val responseJson = buildJsonObject {
                        put("deviceName", Build.MODEL)
                        put("freeSpace", freeSpace)
                        put("tokenValid", true)
                        put("files", Json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(FileManifestItem.serializer()), items))
                    }
                    call.respondText(responseJson.toString(), ContentType.Application.Json)
                }

                // Real thumbnail endpoint
                get("/api/thumbnail/{id}") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
                    val fileId = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val pair = _queuedFiles.value.find { it.first.fileId == fileId }
                    if (pair != null) {
                        val thumbBytes = generateThumbnailBytes(pair.first, pair.second)
                        if (thumbBytes != null) {
                            call.response.header(HttpHeaders.CacheControl, "max-age=3600")
                            call.respondBytes(thumbBytes, ContentType.Image.PNG)
                            return@get
                        }
                    }
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
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
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
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
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

                // Upload files from browser to phone (Sanitized with SafeFileName)
                post("/api/upload") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@post
                    }
                    val multipart = call.receiveMultipart()
                    val uploadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftShare")
                    if (!uploadDir.exists()) uploadDir.mkdirs()

                    var uploadedCount = 0
                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val destination = SafeFileName.resolveUniqueFile(uploadDir, part.originalFileName)
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

                // ── PC Remote Endpoints ───────────────────────────────────────
                get("/api/remote/files") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
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

                get("/api/remote/stream/{id}") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
                    val id = call.parameters["id"]
                    val file = mediaRepo.getFileById(id)
                    if (file != null && file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                    }
                }

                get("/api/remote/download/{id}") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@get
                    }
                    val id = call.parameters["id"]
                    val file = mediaRepo.getFileById(id)
                    if (file != null && file.exists()) {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${file.name}\""
                        )
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                    }
                }

                post("/api/ppt/control") {
                    if (!isAuthenticated(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                        return@post
                    }
                    val body = call.receiveText()
                    try {
                        val obj = Json.parseToJsonElement(body) as? JsonObject
                        val action = obj?.get("action")?.jsonPrimitive?.content ?: "UNKNOWN"
                        _pptEvents.tryEmit(RemotePptEvent(action = action))
                        call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "{\"error\":\"${e.message}\"}")
                    }
                }

                webSocket("/api/ppt/ws") {
                    val cookieSession = call.request.cookies[WebAuthManager.COOKIE_NAME]
                    val tokenParam = call.request.queryParameters["token"]
                    val isAuthed = authManager.isValidSession(cookieSession) ||
                            authManager.verifyQrToken(tokenParam) ||
                            (tokenParam != null && tokenParam == _activeSessionToken.value)

                    if (!isAuthed) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }

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

    private suspend fun handlePageRequest(call: ApplicationCall, assetPath: String) {
        val token = call.request.queryParameters["token"]
        if (token != null && (authManager.verifyQrToken(token) || token == _activeSessionToken.value)) {
            val sessionId = authManager.createAuthorizedSession()
            call.response.cookies.append(
                Cookie(
                    name = WebAuthManager.COOKIE_NAME,
                    value = sessionId,
                    httpOnly = true,
                    path = "/",
                    encoding = CookieEncoding.RAW
                )
            )
            call.respondText(loadAsset(assetPath), ContentType.Text.Html)
            return
        }

        val sessionCookie = call.request.cookies[WebAuthManager.COOKIE_NAME]
        if (authManager.isValidSession(sessionCookie)) {
            call.respondText(loadAsset(assetPath), ContentType.Text.Html)
        } else {
            call.respondRedirect("/login")
        }
    }

    private fun isAuthenticated(call: ApplicationCall): Boolean {
        val cookie = call.request.cookies[WebAuthManager.COOKIE_NAME]
        if (authManager.isValidSession(cookie)) return true

        val token = call.request.queryParameters["token"] ?: call.request.headers["X-Session-Token"]
        if (token != null && (authManager.verifyQrToken(token) || token == _activeSessionToken.value)) {
            return true
        }
        return false
    }

    private fun getClientIp(call: ApplicationCall): String {
        return call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.local.remoteHost
    }

    private fun renderLoginPage(error: String?): String {
        val errorBlock = if (error != null) {
            """<div style="background:rgba(239,68,68,0.15);border:1px solid #ef4444;color:#ef4444;padding:12px;border-radius:12px;margin-bottom:16px;font-size:13px;">$error</div>"""
        } else ""

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SwiftShare Security Verification</title>
    <style>
        body { margin:0; padding:0; background:#090D16; color:#F8FAFC; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; display:flex; align-items:center; justify-content:center; min-height:100vh; }
        .card { background:#131B2E; border:1px solid #23314E; border-radius:20px; padding:32px; width:100%; max-width:400px; box-shadow:0 12px 40px rgba(0,0,0,0.5); text-align:center; }
        .badge { width:56px; height:56px; background:linear-gradient(135deg,#1A4FFF,#00E5FF); border-radius:16px; margin:0 auto 16px; display:flex; align-items:center; justify-content:center; font-size:28px; }
        h1 { font-size:20px; font-weight:700; margin-bottom:8px; }
        p { color:#94A3B8; font-size:13px; line-height:1.5; margin-bottom:24px; }
        input[type="text"] { width:100%; box-sizing:border-box; background:#1E293B; border:1px solid #23314E; border-radius:12px; padding:14px; color:#fff; font-size:22px; text-align:center; letter-spacing:6px; font-weight:700; margin-bottom:16px; outline:none; }
        input[type="text"]:focus { border-color:#00E5FF; }
        button { width:100%; background:linear-gradient(135deg,#1A4FFF,#00E5FF); color:#fff; border:none; border-radius:12px; padding:14px; font-size:15px; font-weight:700; cursor:pointer; }
        button:hover { opacity:0.95; }
        .footer { margin-top:20px; font-size:11px; color:#64748B; }
    </style>
</head>
<body>
    <div class="card">
        <div class="badge">🔒</div>
        <h1>SwiftShare Authorization</h1>
        <p>Enter the 6-digit PIN displayed on your phone's SwiftShare screen to access files.</p>
        $errorBlock
        <form id="pinForm" onsubmit="submitPin(event)">
            <input type="text" id="pinInput" maxlength="6" pattern="[0-9]{6}" placeholder="000000" autofocus required>
            <button type="submit" id="submitBtn">Verify & Connect</button>
        </form>
        <div class="footer">Encrypted Local Wi-Fi Connection &bull; Rate-Limited &bull; SwiftShare</div>
    </div>
    <script>
        async function submitPin(e) {
            e.preventDefault();
            const pin = document.getElementById('pinInput').value.trim();
            const btn = document.getElementById('submitBtn');
            btn.disabled = true;
            btn.textContent = 'Verifying...';
            try {
                const res = await fetch('/api/auth/pin', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ pin: pin })
                });
                const data = await res.json();
                if (res.ok) {
                    window.location.href = '/webshare';
                } else {
                    alert(data.error || 'Authentication failed');
                    btn.disabled = false;
                    btn.textContent = 'Verify & Connect';
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
                btn.disabled = false;
                btn.textContent = 'Verify & Connect';
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun generateThumbnailBytes(item: FileManifestItem, file: File): ByteArray? {
        return try {
            item.thumbnailBase64?.let { b64 ->
                if (b64.isNotBlank()) {
                    return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                }
            }
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
                        android.media.ThumbnailUtils.createVideoThumbnail(file.absolutePath, android.provider.MediaStore.Video.Thumbnails.MINI_KIND)
                    }
                }
                else -> null
            } ?: return null

            val baos = ByteArrayOutputStream()
            bmp?.compress(Bitmap.CompressFormat.PNG, 85, baos)
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun generateMediaItemThumbnailBytes(item: com.swiftshare.explorer.MediaItem): ByteArray? {
        return try {
            val bitmap: Bitmap? = when (item.category) {
                "photos" -> {
                    val uri = item.thumbnailUri?.let { Uri.parse(it) } ?: return null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        this@EmbeddedWebShareServer.context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                    } else {
                        val id = android.content.ContentUris.parseId(uri)
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                            this@EmbeddedWebShareServer.context.contentResolver, id,
                            android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null
                        )
                    }
                }
                "videos" -> {
                    val uri = item.thumbnailUri?.let { Uri.parse(it) }
                    if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        this@EmbeddedWebShareServer.context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
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
                }
                "apps" -> {
                    mediaRepo.loadAppIconBitmap(item.packageName, item.path)?.asAndroidBitmap()
                }
                else -> null
            } ?: return null

            val baos = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.PNG, 90, baos) ?: return null
            baos.toByteArray()
        } catch (_: Exception) {
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
        authManager.clearAllSessions()
    }
}

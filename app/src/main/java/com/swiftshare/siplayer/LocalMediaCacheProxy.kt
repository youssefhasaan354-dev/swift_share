package com.swiftshare.siplayer

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

class LocalMediaCacheProxy(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val cacheDir = File(context.cacheDir, "media_proxy_cache").apply { if (!exists()) mkdirs() }

    fun start(): Int {
        if (isRunning) return serverSocket?.localPort ?: 0
        serverSocket = ServerSocket(0) // dynamic available port
        isRunning = true

        thread(name = "SwiftMediaProxyThread") {
            while (isRunning && serverSocket?.isClosed == false) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    thread { handleProxyClient(clientSocket) }
                } catch (_: Exception) {}
            }
        }
        return serverSocket?.localPort ?: 0
    }

    private fun handleProxyClient(client: Socket) {
        try {
            val reader = client.getInputStream().bufferedReader()
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val requestPath = parts[1].removePrefix("/")
            val decodedTargetUrl = java.net.URLDecoder.decode(requestPath, "UTF-8")

            val targetUrl = URL(decodedTargetUrl)
            val connection = targetUrl.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 10000

            val cacheFile = File(cacheDir, "cache_" + decodedTargetUrl.hashCode() + ".tmp")
            val outputStream = client.getOutputStream()

            val contentLength = connection.contentLengthLong
            val contentType = connection.contentType ?: "video/mp4"

            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: $contentLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Connection: close\r\n\r\n"
            outputStream.write(header.toByteArray())

            val buffer = ByteArray(64 * 1024)
            val input: InputStream = connection.getInputStream()
            val cacheOut = cacheFile.outputStream()

            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                cacheOut.write(buffer, 0, bytesRead)
            }

            cacheOut.flush()
            cacheOut.close()
            input.close()
            client.close()
        } catch (_: Exception) {}
    }

    fun getProxyUrl(originalUrl: String): String {
        val port = serverSocket?.localPort ?: 0
        val encoded = java.net.URLEncoder.encode(originalUrl, "UTF-8")
        return "http://127.0.0.1:$port/$encoded"
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}

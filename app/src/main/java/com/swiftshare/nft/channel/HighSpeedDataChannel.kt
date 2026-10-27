package com.swiftshare.nft.channel

import android.os.Environment
import com.swiftshare.common.Constants
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.channel.models.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

// ─────────────────────────────────────────────────────────────────────────────
// Ultra-fast 4-stream parallel TCP worker pool with Keep-Alive, 1 MB socket
// buffers, tcpNoDelay, and zero-copy FileChannel.transferTo() for maximum
// 5GHz throughput.
// ─────────────────────────────────────────────────────────────────────────────

class HighSpeedDataChannel {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var isCancelled = false
    private var activeExpectedToken: String? = null

    private val _transferProgress = MutableStateFlow(TransferProgress())
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    fun startReceiverServer(
        downloadDir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftShare"),
        expectedSessionToken: String? = null,
        onFileReceived: (File, FileManifestItem) -> Unit = { _, _ -> },
        onError: (Throwable) -> Unit = {}
    ) {
        if (!downloadDir.exists()) downloadDir.mkdirs()
        isCancelled = false
        activeExpectedToken = expectedSessionToken

        serverJob = scope.launch {
            var serverChannel: ServerSocketChannel? = null
            try {
                serverChannel = ServerSocketChannel.open().apply {
                    configureBlocking(true)
                    socket().reuseAddress = true
                    // 1 MB receive buffer for maximum Wi-Fi 5GHz throughput
                    socket().receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
                    socket().soTimeout = Constants.SOCKET_TIMEOUT_MS
                    socket().bind(InetSocketAddress(Constants.PORT_DATA))
                }

                while (isActive && !isCancelled) {
                    val socketChannel = serverChannel.accept()
                    // Each incoming connection handled in its own coroutine (parallel streams)
                    launch {
                        handleIncomingConnection(socketChannel, downloadDir, onFileReceived)
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled) onError(e)
            } finally {
                try {
                    serverChannel?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleIncomingConnection(
        socketChannel: SocketChannel,
        downloadDir: File,
        onFileReceived: (File, FileManifestItem) -> Unit
    ) {
        try {
            socketChannel.socket().let { sock ->
                sock.receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
                sock.tcpNoDelay = true
                sock.soTimeout = Constants.SOCKET_TIMEOUT_MS
            }

            // 1. Read first 4 bytes to check for MAGIC_HEADER ("SWFT")
            val magicBuffer = ByteBuffer.allocate(4)
            while (magicBuffer.hasRemaining()) {
                if (socketChannel.read(magicBuffer) == -1) return
            }
            magicBuffer.flip()
            val isMagic = magicBuffer.get() == 'S'.code.toByte() &&
                    magicBuffer.get() == 'W'.code.toByte() &&
                    magicBuffer.get() == 'F'.code.toByte() &&
                    magicBuffer.get() == 'T'.code.toByte()

            if (isMagic) {
                val tokenLenBuffer = ByteBuffer.allocate(4)
                while (tokenLenBuffer.hasRemaining()) {
                    if (socketChannel.read(tokenLenBuffer) == -1) return
                }
                tokenLenBuffer.flip()
                val tokenLen = tokenLenBuffer.int
                val tokenBytes = ByteArray(tokenLen.coerceIn(0, 512))
                val tokenBuffer = ByteBuffer.wrap(tokenBytes)
                while (tokenBuffer.hasRemaining()) {
                    if (socketChannel.read(tokenBuffer) == -1) return
                }
                val receivedToken = String(tokenBytes).trim()

                // Security Check: reject connection before opening any file if token mismatch!
                val expected = activeExpectedToken
                if (!expected.isNullOrBlank()) {
                    val matches = java.security.MessageDigest.isEqual(
                        expected.toByteArray(Charsets.UTF_8),
                        receivedToken.toByteArray(Charsets.UTF_8)
                    )
                    if (!matches) {
                        socketChannel.close()
                        return
                    }
                }
            } else {
                if (!activeExpectedToken.isNullOrBlank()) {
                    // Expected token was configured but incoming stream lacks authentication header
                    socketChannel.close()
                    return
                }
            }

            // Read header: [fileId (36 bytes)] [offset (8 bytes)] [totalSize (8 bytes)] [nameLength (4 bytes)] [name (N bytes)]
            val headerBuffer = ByteBuffer.allocate(36 + 8 + 8 + 4)
            while (headerBuffer.hasRemaining()) {
                if (socketChannel.read(headerBuffer) == -1) return
            }
            headerBuffer.flip()

            val fileIdBytes = ByteArray(36)
            headerBuffer.get(fileIdBytes)
            val fileId = String(fileIdBytes).trim()

            val offset = headerBuffer.long
            val totalSize = headerBuffer.long
            val nameLength = headerBuffer.int

            val nameBuffer = ByteBuffer.allocate(nameLength)
            while (nameBuffer.hasRemaining()) {
                if (socketChannel.read(nameBuffer) == -1) return
            }
            nameBuffer.flip()
            val fileName = String(nameBuffer.array())

            val thumbLenBuffer = ByteBuffer.allocate(4)
            while (thumbLenBuffer.hasRemaining()) {
                if (socketChannel.read(thumbLenBuffer) == -1) return
            }
            thumbLenBuffer.flip()
            val thumbLength = thumbLenBuffer.int

            val thumbBase64 = if (thumbLength in 1..262144) {
                val thumbBuffer = ByteBuffer.allocate(thumbLength)
                while (thumbBuffer.hasRemaining()) {
                    if (socketChannel.read(thumbBuffer) == -1) return
                }
                thumbBuffer.flip()
                android.util.Base64.encodeToString(thumbBuffer.array(), android.util.Base64.NO_WRAP)
            } else null

            val sanitizedName = com.swiftshare.common.SafeFileName.sanitize(fileName)
            val targetFile = if (offset > 0L) {
                File(downloadDir, sanitizedName)
            } else {
                com.swiftshare.common.SafeFileName.resolveUniqueFile(downloadDir, fileName)
            }
            val fileOutputStream = FileOutputStream(targetFile, offset > 0)
            val fileChannel = fileOutputStream.channel

            if (offset > 0) {
                fileChannel.position(offset)
            }

            var bytesTransferred = offset
            // 512 KB direct buffer for optimal zero-copy performance
            val buffer = ByteBuffer.allocateDirect(Constants.TRANSFER_CHUNK_SIZE)

            var lastTime = System.currentTimeMillis()
            var lastBytes = bytesTransferred

            _transferProgress.value = TransferProgress(
                currentFileId = fileId,
                currentFileName = fileName,
                fileBytesTransferred = bytesTransferred,
                fileTotalBytes = totalSize,
                totalBytesTransferred = bytesTransferred,
                batchTotalBytes = totalSize,
                status = FileTransferStatus.TRANSFERRING,
                isSender = false,
                thumbnailBase64 = thumbBase64
            )

            while (bytesTransferred < totalSize && !isCancelled) {
                buffer.clear()
                val read = socketChannel.read(buffer)
                if (read == -1) break
                buffer.flip()
                while (buffer.hasRemaining()) {
                    fileChannel.write(buffer)
                }
                bytesTransferred += read

                val now = System.currentTimeMillis()
                val elapsed = now - lastTime
                if (elapsed >= 500) {
                    val deltaBytes = bytesTransferred - lastBytes
                    val speed = (deltaBytes * 1000) / elapsed
                    val remainingBytes = (totalSize - bytesTransferred).coerceAtLeast(0)
                    val eta = if (speed > 0) remainingBytes / speed else 0L

                    _transferProgress.value = _transferProgress.value.copy(
                        fileBytesTransferred = bytesTransferred,
                        totalBytesTransferred = bytesTransferred,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    )

                    lastTime = now
                    lastBytes = bytesTransferred
                }
            }

            fileChannel.close()
            fileOutputStream.close()
            socketChannel.close()

            if (bytesTransferred >= totalSize) {
                ChunkManager.clearResumeOffset(fileId)
                _transferProgress.value = _transferProgress.value.copy(
                    fileBytesTransferred = totalSize,
                    totalBytesTransferred = totalSize,
                    status = FileTransferStatus.COMPLETED
                )
                val item = FileManifestItem(fileId = fileId, name = fileName, size = totalSize, thumbnailBase64 = thumbBase64)
                onFileReceived(targetFile, item)
            } else {
                ChunkManager.saveResumeOffset(fileId, bytesTransferred)
            }
        } catch (e: Exception) {
            com.swiftshare.common.AppLogger.e("DataChannel", "Error processing incoming data channel connection: ${e.message}", e, notifyUser = true)
        }
    }

    fun sendFile(
        targetIp: String,
        manifestItem: FileManifestItem,
        sourceFile: File,
        batchTotalBytes: Long,
        cumulativeBytesStart: Long,
        sessionToken: String? = null,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            var socketChannel: SocketChannel? = null
            var fileChannel: FileChannel? = null
            try {
                isCancelled = false
                socketChannel = SocketChannel.open().apply {
                    configureBlocking(true)
                    socket().let { sock ->
                        // 1 MB send buffer, no Nagle delay, 15s timeout
                        sock.sendBufferSize = Constants.SOCKET_BUFFER_SIZE
                        sock.receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
                        sock.tcpNoDelay = true
                        sock.soTimeout = Constants.SOCKET_TIMEOUT_MS
                        sock.keepAlive = true
                    }
                    connect(InetSocketAddress(targetIp, Constants.PORT_DATA))
                }

                val offset = ChunkManager.getResumeOffset(manifestItem.fileId)
                val nameBytes = manifestItem.name.toByteArray()
                val tokenBytes = (sessionToken ?: "").toByteArray()
                val thumbBytes = manifestItem.thumbnailBase64?.let {
                    try {
                        android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        null
                    }
                } ?: ByteArray(0)

                // Header layout: MAGIC (4) + tokenLen (4) + tokenBytes + [fileId (36 bytes)] [offset (8)] [totalSize (8)] [nameLength (4)] [name (N)] [thumbLen (4)] [thumbBytes (M)]
                val paddedId = manifestItem.fileId.take(36).padEnd(36)
                val headerBuffer = ByteBuffer.allocate(4 + 4 + tokenBytes.size + 36 + 8 + 8 + 4 + nameBytes.size + 4 + thumbBytes.size).apply {
                    put('S'.code.toByte())
                    put('W'.code.toByte())
                    put('F'.code.toByte())
                    put('T'.code.toByte())
                    putInt(tokenBytes.size)
                    put(tokenBytes)
                    put(paddedId.toByteArray())
                    putLong(offset)
                    putLong(sourceFile.length())
                    putInt(nameBytes.size)
                    put(nameBytes)
                    putInt(thumbBytes.size)
                    put(thumbBytes)
                    flip()
                }

                while (headerBuffer.hasRemaining()) {
                    socketChannel.write(headerBuffer)
                }

                fileChannel = FileInputStream(sourceFile).channel
                val totalBytes = sourceFile.length()
                var currentPosition = offset

                var lastTime = System.currentTimeMillis()
                var lastBytes = currentPosition

                _transferProgress.value = TransferProgress(
                    currentFileId = manifestItem.fileId,
                    currentFileName = manifestItem.name,
                    fileBytesTransferred = currentPosition,
                    fileTotalBytes = totalBytes,
                    totalBytesTransferred = cumulativeBytesStart + currentPosition,
                    batchTotalBytes = batchTotalBytes,
                    status = FileTransferStatus.TRANSFERRING,
                    isSender = true,
                    thumbnailBase64 = manifestItem.thumbnailBase64
                )

                while (currentPosition < totalBytes && !isCancelled) {
                    // Zero-copy transfer in 512 KB chunks for maximum 5GHz throughput
                    val count = (totalBytes - currentPosition).coerceAtMost(Constants.TRANSFER_CHUNK_SIZE.toLong())
                    val transferred = fileChannel.transferTo(currentPosition, count, socketChannel)
                    if (transferred <= 0) break
                    currentPosition += transferred

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) {
                        val deltaBytes = currentPosition - lastBytes
                        val speed = (deltaBytes * 1000) / elapsed
                        val remainingBytes = (batchTotalBytes - (cumulativeBytesStart + currentPosition)).coerceAtLeast(0)
                        val eta = if (speed > 0) remainingBytes / speed else 0L

                        _transferProgress.value = _transferProgress.value.copy(
                            fileBytesTransferred = currentPosition,
                            totalBytesTransferred = cumulativeBytesStart + currentPosition,
                            speedBytesPerSec = speed,
                            etaSeconds = eta
                        )

                        lastTime = now
                        lastBytes = currentPosition
                    }
                }

                if (currentPosition >= totalBytes) {
                    ChunkManager.clearResumeOffset(manifestItem.fileId)
                    _transferProgress.value = _transferProgress.value.copy(
                        status = FileTransferStatus.COMPLETED
                    )
                    onSuccess()
                } else {
                    ChunkManager.saveResumeOffset(manifestItem.fileId, currentPosition)
                    onError(Exception("Transfer paused or interrupted"))
                }
            } catch (e: Exception) {
                onError(e)
            } finally {
                try { fileChannel?.close() } catch (_: Exception) {}
                try { socketChannel?.close() } catch (_: Exception) {}
            }
        }
    }

    fun cancel() {
        isCancelled = true
        serverJob?.cancel()
    }
}

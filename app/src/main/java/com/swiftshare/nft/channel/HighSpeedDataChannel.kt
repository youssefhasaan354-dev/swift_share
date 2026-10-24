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

    private val _transferProgress = MutableStateFlow(TransferProgress())
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    fun startReceiverServer(
        downloadDir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftShare"),
        onFileReceived: (File, FileManifestItem) -> Unit = { _, _ -> },
        onError: (Throwable) -> Unit = {}
    ) {
        if (!downloadDir.exists()) downloadDir.mkdirs()
        isCancelled = false

        serverJob = scope.launch {
            var serverChannel: ServerSocketChannel? = null
            try {
                serverChannel = ServerSocketChannel.open().apply {
                    configureBlocking(true)
                    socket().reuseAddress = true
                    // 1 MB receive buffer for maximum Wi-Fi 5GHz throughput
                    socket().receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
                    socket().soTimeout = Constants.SOCKET_TIMEOUT_MS
                    socket().bind(InetSocketAddress(Constants.PORT_DATA_NIO))
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

            val targetFile = File(downloadDir, fileName)
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
            e.printStackTrace()
        }
    }

    fun sendFile(
        targetIp: String,
        manifestItem: FileManifestItem,
        sourceFile: File,
        batchTotalBytes: Long,
        cumulativeBytesStart: Long,
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
                    connect(InetSocketAddress(targetIp, Constants.PORT_DATA_NIO))
                }

                val offset = ChunkManager.getResumeOffset(manifestItem.fileId)
                val nameBytes = manifestItem.name.toByteArray()
                val thumbBytes = manifestItem.thumbnailBase64?.let {
                    try {
                        android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        null
                    }
                } ?: ByteArray(0)

                // Header layout: [fileId (36 bytes)] [offset (8)] [totalSize (8)] [nameLength (4)] [name (N)] [thumbLen (4)] [thumbBytes (M)]
                val paddedId = manifestItem.fileId.take(36).padEnd(36)
                val headerBuffer = ByteBuffer.allocate(36 + 8 + 8 + 4 + nameBytes.size + 4 + thumbBytes.size).apply {
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

package com.swiftshare.nft.channel

import android.os.Environment
import com.swiftshare.common.AppLogger
import com.swiftshare.common.Constants
import com.swiftshare.common.SafeFileName
import com.swiftshare.nft.channel.models.FileManifestItem
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.channel.models.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channel
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.security.MessageDigest
import java.util.BitSet
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class HighSpeedDataChannel {

    companion object {
        const val STREAM_COUNT = 4
        const val CHUNK_SIZE = 64 * 1024 // 64 KB
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    val isCancelled = AtomicBoolean(false)
    private var activeExpectedToken: String? = null

    private val _transferProgress = MutableStateFlow(TransferProgress())
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    private var activeServerChannel: ServerSocketChannel? = null

    var serverPort: Int = 0
        private set

    // Injectable timeout for unit tests (default 15 seconds)
    var watchdogTimeoutMs: Long = 15_000L

    // Injected channel registry to track open channels and assert clean leak-free cancellation
    val channelRegistry: MutableList<Channel> = Collections.synchronizedList(mutableListOf())

    // Per-stream metrics for loopback concurrency verification
    val bytesReceivedPerStream = ConcurrentHashMap<Byte, AtomicLong>()
    val bytesSentPerStream = ConcurrentHashMap<Byte, AtomicLong>()

    // Active file manifests and checksum coordination
    val activeManifestItems = ConcurrentHashMap<String, FileManifestItem>()
    val receivedChecksums = ConcurrentHashMap<String, String>()
    val checksumLatches = ConcurrentHashMap<String, CountDownLatch>()

    // Track active .part files and .part.meta files
    private val activePartFiles = ConcurrentHashMap<String, File>()
    private val activeMetaFiles = ConcurrentHashMap<String, File>()

    fun getResumeBitmap(downloadDir: File, fileId: String, fileName: String, totalSize: Long): BitSet? {
        val (partFile, metaFile) = PartFileManager.resolvePartFileAndMeta(downloadDir, fileId, fileName, totalSize)
        if (!partFile.exists() || !metaFile.exists()) return null
        val meta = PartFileManager.readMeta(metaFile) ?: return null
        if (meta.fileId != fileId || meta.size != totalSize) return null
        return PartFileManager.base64ToBitSet(meta.completedChunksBase64, meta.totalChunks)
    }

    fun getReceivedBytes(fileId: String): Long = fileReceivedBytes[fileId]?.get() ?: 0L

    fun registerManifestItem(item: FileManifestItem) {
        activeManifestItems[item.fileId] = item
        checksumLatches[item.fileId] = CountDownLatch(1)
    }

    fun notifyChecksumReceived(fileId: String, sha256: String) {
        receivedChecksums[fileId] = sha256
        checksumLatches[fileId]?.countDown()
    }

    fun startReceiverServer(
        downloadDir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftShare"),
        port: Int = Constants.PORT_DATA,
        expectedSessionToken: String? = null,
        onBound: ((Int) -> Unit)? = null,
        onFileReceived: (File, FileManifestItem) -> Unit = { _, _ -> },
        onError: (Throwable) -> Unit = {}
    ) {
        if (!downloadDir.exists()) downloadDir.mkdirs()
        isCancelled.set(false)
        activeExpectedToken = expectedSessionToken

        serverJob = scope.launch {
            var serverChannel: ServerSocketChannel? = null
            val activeConnections = AtomicInteger(0)
            try {
                serverChannel = ServerSocketChannel.open().apply {
                    configureBlocking(true)
                    socket().reuseAddress = true
                    socket().receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
                    socket().soTimeout = Constants.SOCKET_TIMEOUT_MS
                    socket().bind(InetSocketAddress(port))
                }
                activeServerChannel = serverChannel
                channelRegistry.add(serverChannel)
                serverPort = (serverChannel.localAddress as InetSocketAddress).port
                onBound?.invoke(serverPort)

                while (isActive && !isCancelled.get()) {
                    val socketChannel = serverChannel.accept()
                    channelRegistry.add(socketChannel)
                    val connIndex = activeConnections.incrementAndGet()

                    // Policy: Reject any 5th incoming stream beyond the 4 parallel streams
                    if (connIndex > STREAM_COUNT + 1) { // allow margin for legacy single-stream
                        socketChannel.close()
                        continue
                    }

                    launch {
                        try {
                            handleIncomingStream(socketChannel, downloadDir, onFileReceived)
                        } catch (e: Exception) {
                            if (!isCancelled.get()) {
                                AppLogger.e("HighSpeedDataChannel", "Stream handling error: ${e.message}", e)
                                onError(e)
                            }
                        } finally {
                            activeConnections.decrementAndGet()
                            try { socketChannel.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled.get()) onError(e)
            } finally {
                serverPort = 0
                try {
                    serverChannel?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun handleIncomingStream(
        socketChannel: SocketChannel,
        downloadDir: File,
        onFileReceived: (File, FileManifestItem) -> Unit
    ) {
        socketChannel.socket().let { sock ->
            sock.receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
            sock.tcpNoDelay = true
            sock.soTimeout = watchdogTimeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        // 1. Read first 4 bytes to check MAGIC
        val magicBuffer = ByteBuffer.allocate(4)
        while (magicBuffer.hasRemaining()) {
            if (socketChannel.read(magicBuffer) == -1) return
        }
        magicBuffer.flip()
        val magicBytes = ByteArray(4)
        magicBuffer.get(magicBytes)

        if (magicBytes.contentEquals(StreamAuthHeader.MAGIC_BYTES)) {
            // Read StreamAuthHeader: tokenLen (4) + tokenBytes + streamIndex (1)
            val tokenLenBuf = ByteBuffer.allocate(4)
            while (tokenLenBuf.hasRemaining()) {
                if (socketChannel.read(tokenLenBuf) == -1) return
            }
            tokenLenBuf.flip()
            val tokenLen = tokenLenBuf.int
            if (tokenLen < 0 || tokenLen > 512) {
                socketChannel.close()
                return
            }
            val tokenBytes = ByteArray(tokenLen)
            val tokenBuf = ByteBuffer.wrap(tokenBytes)
            while (tokenBuf.hasRemaining()) {
                if (socketChannel.read(tokenBuf) == -1) return
            }
            val streamIndexBuf = ByteBuffer.allocate(1)
            while (streamIndexBuf.hasRemaining()) {
                if (socketChannel.read(streamIndexBuf) == -1) return
            }
            streamIndexBuf.flip()
            val streamIndex = streamIndexBuf.get()

            val receivedToken = String(tokenBytes, Charsets.UTF_8).trim()
            if (!DataChannelHeader.validateToken(activeExpectedToken, receivedToken)) {
                socketChannel.close()
                return
            }

            // Stream authenticated successfully. Now process incoming chunks.
            processChunksOnStream(socketChannel, streamIndex, downloadDir, onFileReceived)
        } else if (DataChannelHeader.isMagic(magicBytes)) {
            // Legacy single-stream fallback handler
            handleLegacySingleStream(socketChannel, downloadDir, magicBytes, onFileReceived)
        } else {
            socketChannel.close()
        }
    }

    // Shared chunk assembly state per fileId across all 4 streams
    private val fileBitmaps = ConcurrentHashMap<String, BitSet>()
    private val fileTotalChunks = ConcurrentHashMap<String, Int>()
    private val fileReceivedBytes = ConcurrentHashMap<String, AtomicLong>()
    private val fileRaStreams = ConcurrentHashMap<String, RandomAccessFile>()
    private val fileFinishedFlags = ConcurrentHashMap<String, AtomicBoolean>()

    private suspend fun processChunksOnStream(
        socketChannel: SocketChannel,
        streamIndex: Byte,
        downloadDir: File,
        onFileReceived: (File, FileManifestItem) -> Unit
    ) {
        val lastProgress = AtomicLong(System.currentTimeMillis())
        val watchdogJob = scope.launch {
            while (isActive && !isCancelled.get() && socketChannel.isOpen) {
                delay(1000)
                if (System.currentTimeMillis() - lastProgress.get() > watchdogTimeoutMs) {
                    AppLogger.e("DataChannel", "Watchdog triggered on stream $streamIndex; closing socket")
                    try { socketChannel.close() } catch (_: Exception) {}
                    break
                }
            }
        }

        try {
            while (!isCancelled.get() && socketChannel.isOpen) {
                // Read ChunkHeader (62 bytes)
                val headerBuf = ByteBuffer.allocate(ChunkHeader.HEADER_SIZE)
                while (headerBuf.hasRemaining()) {
                    val read = socketChannel.read(headerBuf)
                    if (read == -1) return
                    if (read > 0) lastProgress.set(System.currentTimeMillis())
                }
                headerBuf.flip()

                val header = ChunkHeader.parse(headerBuf) ?: throw IOException("Invalid ChunkHeader on stream $streamIndex")
                val manifestItem = activeManifestItems[header.fileId]

                // Validate Header
                if (manifestItem != null) {
                    val valResult = ChunkHeader.validate(header, manifestItem.size, activeManifestItems.keys)
                    if (valResult is ChunkHeader.Companion.ValidationResult.Invalid) {
                        throw IOException("Defensive header validation failed: ${valResult.reason}")
                    }
                }

                if (header.isEof) {
                    // Stream has completed sending its chunks
                    break
                }

                // Initialize shared state for this file if needed
                val totalChunks = ((manifestItem?.size ?: 0L) + CHUNK_SIZE - 1) / CHUNK_SIZE
                fileTotalChunks.putIfAbsent(header.fileId, totalChunks.toInt())

                val (partFile, metaFile) = PartFileManager.resolvePartFileAndMeta(
                    downloadDir = downloadDir,
                    fileId = header.fileId,
                    fileName = manifestItem?.name ?: header.fileId,
                    totalSize = manifestItem?.size ?: 0L
                )
                activePartFiles.putIfAbsent(header.fileId, partFile)
                activeMetaFiles.putIfAbsent(header.fileId, metaFile)

                val bitmap = fileBitmaps.computeIfAbsent(header.fileId) {
                    val diskMeta = PartFileManager.readMeta(metaFile)
                    if (diskMeta != null && diskMeta.fileId == header.fileId && diskMeta.size == (manifestItem?.size ?: 0L)) {
                        PartFileManager.base64ToBitSet(diskMeta.completedChunksBase64, totalChunks.toInt())
                    } else {
                        BitSet(totalChunks.toInt())
                    }
                }
                val receivedBytes = fileReceivedBytes.computeIfAbsent(header.fileId) {
                    AtomicLong(bitmap.cardinality().toLong() * CHUNK_SIZE)
                }

                val raf = fileRaStreams.computeIfAbsent(header.fileId) {
                    RandomAccessFile(partFile, "rw").apply {
                        if (manifestItem != null && length() < manifestItem.size) {
                            setLength(manifestItem.size)
                        }
                    }
                }

                // Read payload
                val payload = ByteArray(header.length)
                val payloadBuf = ByteBuffer.wrap(payload)
                while (payloadBuf.hasRemaining()) {
                    val read = socketChannel.read(payloadBuf)
                    if (read == -1) throw IOException("Premature EOF while reading chunk payload")
                    if (read > 0) lastProgress.set(System.currentTimeMillis())
                }

                // Verify CRC32
                val computedCrc = ChunkHeader.computeCrc32(payload)
                if (computedCrc != header.crc32) {
                    throw IOException("CRC32 mismatch on chunk at offset ${header.offset}: computed $computedCrc vs header ${header.crc32}")
                }

                val chunkIndex = (header.offset / CHUNK_SIZE).toInt()
                val alreadyReceived = synchronized(bitmap) { bitmap.get(chunkIndex) }

                if (!alreadyReceived) {
                    // Positional write via FileChannel directly to disk first
                    val writeBuf = ByteBuffer.wrap(payload)
                    raf.channel.write(writeBuf, header.offset)
                    // Mark completed in bitmap ONLY AFTER bytes are safely written to disk
                    synchronized(bitmap) {
                        bitmap.set(chunkIndex)
                    }
                    receivedBytes.addAndGet(header.length.toLong())

                    bytesReceivedPerStream.computeIfAbsent(header.streamId) { AtomicLong(0) }
                        .addAndGet(header.length.toLong())
                }

                // Check if file is completely received
                val finished = fileFinishedFlags.computeIfAbsent(header.fileId) { AtomicBoolean(false) }
                val isComplete = synchronized(bitmap) {
                    bitmap.cardinality() == fileTotalChunks[header.fileId]
                }

                if (isComplete && finished.compareAndSet(false, true)) {
                    finalizeFileTransfer(header.fileId, manifestItem, partFile, metaFile, raf, downloadDir, onFileReceived)
                }
            }
        } finally {
            watchdogJob.cancel()
            flushActiveMetaToDisk()
        }
    }

    private suspend fun finalizeFileTransfer(
        fileId: String,
        manifestItem: FileManifestItem?,
        partFile: File,
        metaFile: File,
        raf: RandomAccessFile,
        downloadDir: File,
        onFileReceived: (File, FileManifestItem) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            raf.close()
        } catch (_: Exception) {}

        // Wait for FileChecksumMessage on control channel with timeout (10s)
        val latch = checksumLatches[fileId]
        val received = latch?.await(10, TimeUnit.SECONDS) ?: false
        val expectedSha = receivedChecksums[fileId]

        if (!received || expectedSha.isNullOrBlank()) {
            throw IOException("FileChecksumMessage timeout or missing for fileId $fileId")
        }

        // Verify full-file SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(partFile).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }

        if (!actualSha.equals(expectedSha, ignoreCase = true)) {
            // Delete on final SHA-256 mismatch
            partFile.delete()
            metaFile.delete()
            activePartFiles.remove(fileId)
            activeMetaFiles.remove(fileId)
            throw IOException("SHA-256 mismatch! Expected $expectedSha but computed $actualSha")
        }

        // SHA-256 matches: rename .part to final destination safely and remove .meta
        val targetName = manifestItem?.name ?: partFile.name.removeSuffix(".part")
        val finalFile = SafeFileName.resolveUniqueFile(downloadDir, targetName)
        if (!partFile.renameTo(finalFile)) {
            // Fallback copy if renameTo fails across file boundaries
            partFile.copyTo(finalFile, overwrite = false)
            partFile.delete()
        }
        metaFile.delete()
        activePartFiles.remove(fileId)
        activeMetaFiles.remove(fileId)

        manifestItem?.let { onFileReceived(finalFile, it) }
    }

    /**
     * Legacy single-stream fallback handler for backwards compatibility with Flutter iOS companion
     * and v1 single-stream clients.
     *
     * SECURITY PROOF:
     * Enforces the session token via constant-time comparison [DataChannelHeader.validateToken]
     * against [activeExpectedToken] before accepting or writing any file bytes. Unauthorized
     * connections without a matching token are immediately closed.
     */
    private fun handleLegacySingleStream(
        socketChannel: SocketChannel,
        downloadDir: File,
        magicBytes: ByteArray,
        onFileReceived: (File, FileManifestItem) -> Unit
    ) {
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

        if (!DataChannelHeader.validateToken(activeExpectedToken, receivedToken)) {
            socketChannel.close()
            return
        }

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
        if (thumbLength > 0) {
            val thumbBuffer = ByteBuffer.allocate(thumbLength)
            while (thumbBuffer.hasRemaining()) {
                if (socketChannel.read(thumbBuffer) == -1) return
            }
        }

        val sanitizedName = SafeFileName.sanitize(fileName)
        val targetFile = if (offset > 0L) {
            File(downloadDir, sanitizedName)
        } else {
            SafeFileName.resolveUniqueFile(downloadDir, fileName)
        }

        val fos = FileOutputStream(targetFile, offset > 0)
        val fc = fos.channel
        if (offset > 0) fc.position(offset)

        var bytesTransferred = offset
        val directBuffer = ByteBuffer.allocateDirect(CHUNK_SIZE)
        while (!isCancelled.get()) {
            directBuffer.clear()
            val bytesRead = socketChannel.read(directBuffer)
            if (bytesRead == -1) break
            directBuffer.flip()
            while (directBuffer.hasRemaining()) {
                fc.write(directBuffer)
            }
            bytesTransferred += bytesRead
            if (totalSize > 0 && bytesTransferred >= totalSize) break
        }
        fc.close()
        fos.close()

        val item = activeManifestItems[fileId] ?: FileManifestItem(
            fileId = fileId,
            name = fileName,
            size = totalSize
        )
        onFileReceived(targetFile, item)
    }

    suspend fun sendFileParallel(
        targetIp: String,
        manifestItem: FileManifestItem,
        sourceFile: File,
        sessionToken: String?,
        controlChannel: TransferControlChannel? = null,
        batchId: String = "batch_default",
        port: Int = Constants.PORT_DATA,
        streamCount: Int = STREAM_COUNT,
        resumeBitmap: BitSet? = null,
        onProgress: ((Long, Long) -> Unit)? = null,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        val lastProgress = AtomicLong(System.currentTimeMillis())
        val clientChannels = Collections.synchronizedList(mutableListOf<SocketChannel>())
        val watchdogJob = scope.launch {
            while (isActive && !isCancelled.get()) {
                delay(1000)
                if (System.currentTimeMillis() - lastProgress.get() > watchdogTimeoutMs) {
                    AppLogger.e("DataChannel", "Sender watchdog triggered after $watchdogTimeoutMs ms; closing sockets")
                    clientChannels.forEach { try { it.close() } catch (_: Exception) {} }
                    break
                }
            }
        }

        try {
            isCancelled.set(false)
            val fileSize = sourceFile.length()
            val chunkCount = if (fileSize == 0L) 1 else ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
            val remainingChunks = (0 until chunkCount).filter { resumeBitmap == null || !resumeBitmap.get(it) }
            val chunkQueue = ConcurrentLinkedQueue<Int>(remainingChunks)

            // Connect 4 parallel streams and authenticate each
            val authLatch = CountDownLatch(streamCount)
            val workerJobs = mutableListOf<Job>()

            for (streamIdx in 0 until streamCount) {
                val sc = SocketChannel.open().apply {
                    configureBlocking(true)
                    socket().sendBufferSize = Constants.SOCKET_BUFFER_SIZE
                    socket().receiveBufferSize = Constants.SOCKET_BUFFER_SIZE
                    socket().tcpNoDelay = true
                    socket().soTimeout = watchdogTimeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    connect(InetSocketAddress(targetIp, port))
                }
                clientChannels.add(sc)
                channelRegistry.add(sc)

                // Send StreamAuthHeader
                val authHeader = StreamAuthHeader(
                    token = sessionToken ?: "",
                    streamIndex = streamIdx.toByte()
                )
                val authBuf = authHeader.serialize()
                while (authBuf.hasRemaining()) {
                    sc.write(authBuf)
                }
                authLatch.countDown()
            }

            // Ensure all 4 streams connect before dispatching chunks
            if (!authLatch.await(10, TimeUnit.SECONDS)) {
                throw IOException("Timed out establishing $streamCount parallel streams")
            }

            val totalBytesTransferredThisRun = AtomicLong(0L)
            val cumulativeTransferred = AtomicLong(
                (resumeBitmap?.cardinality() ?: 0).toLong() * CHUNK_SIZE
            )
            val raf = RandomAccessFile(sourceFile, "r")

            // Launch async SHA-256 calculation to send on control channel concurrently
            val shaJob = launch {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(sourceFile).use { fis ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (fis.read(buf).also { n = it } != -1 && !isCancelled.get()) {
                        digest.update(buf, 0, n)
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                controlChannel?.sendFileChecksum(batchId, manifestItem.fileId, sha256)
            }

            // Launch 4 parallel sender workers
            for (streamIdx in 0 until streamCount) {
                val channel = clientChannels[streamIdx]
                val job = launch {
                    val buffer = ByteArray(CHUNK_SIZE)
                    try {
                        while (isActive && !isCancelled.get()) {
                            val chunkIdx = chunkQueue.poll() ?: break
                            val offset = chunkIdx.toLong() * CHUNK_SIZE
                            val length = minOf(CHUNK_SIZE.toLong(), fileSize - offset).toInt()

                            synchronized(raf) {
                                raf.seek(offset)
                                raf.readFully(buffer, 0, length)
                            }

                            val crc = ChunkHeader.computeCrc32(buffer, 0, length)
                            val header = ChunkHeader(
                                streamId = streamIdx.toByte(),
                                fileId = manifestItem.fileId,
                                offset = offset,
                                length = length,
                                crc32 = crc,
                                isEof = false
                            )

                            val headerBuf = header.serialize()
                            while (headerBuf.hasRemaining() && !isCancelled.get()) {
                                channel.write(headerBuf)
                            }

                            val payloadBuf = ByteBuffer.wrap(buffer, 0, length)
                            while (payloadBuf.hasRemaining() && !isCancelled.get()) {
                                channel.write(payloadBuf)
                            }

                            lastProgress.set(System.currentTimeMillis())
                            totalBytesTransferredThisRun.addAndGet(length.toLong())
                            val currentCumulative = cumulativeTransferred.addAndGet(length.toLong())
                            bytesSentPerStream.computeIfAbsent(streamIdx.toByte()) { AtomicLong(0) }
                                .addAndGet(length.toLong())
                            onProgress?.invoke(currentCumulative, fileSize)
                        }

                        if (!isCancelled.get()) {
                            // Send EOF chunk for this stream
                            val eofHeader = ChunkHeader(
                                streamId = streamIdx.toByte(),
                                fileId = manifestItem.fileId,
                                offset = 0L,
                                length = 0,
                                crc32 = 0L,
                                isEof = true
                            )
                            val eofBuf = eofHeader.serialize()
                            while (eofBuf.hasRemaining() && !isCancelled.get()) {
                                channel.write(eofBuf)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isCancelled.get()) throw e
                    }
                }
                workerJobs.add(job)
            }

            workerJobs.forEach { it.join() }
            shaJob.join()
            try { raf.close() } catch (_: Exception) {}

            if (isCancelled.get()) {
                throw IOException("Transfer cancelled")
            }

            onSuccess()
        } catch (e: Exception) {
            if (!isCancelled.get()) {
                AppLogger.e("DataChannel", "sendFileParallel error: ${e.message}", e)
                onError(e)
            }
        } finally {
            watchdogJob.cancel()
            clientChannels.forEach { try { it.close() } catch (_: Exception) {} }
        }
    }

    // Single-stream compatibility wrapper
    fun sendFile(
        targetIp: String,
        manifestItem: FileManifestItem,
        sourceFile: File,
        batchTotalBytes: Long,
        cumulativeBytesStart: Long,
        sessionToken: String? = null,
        port: Int = Constants.PORT_DATA,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            sendFileParallel(
                targetIp = targetIp,
                manifestItem = manifestItem,
                sourceFile = sourceFile,
                sessionToken = sessionToken,
                batchId = "batch_compat",
                port = port,
                streamCount = 1,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    fun cancel(explicitUserCancel: Boolean = true) {
        isCancelled.set(true)
        serverJob?.cancel()
        scope.coroutineContext.cancelChildren()

        // Close server channel
        try {
            activeServerChannel?.close()
        } catch (_: Exception) {}
        activeServerChannel = null
        serverPort = 0

        // Close all channels in registry
        synchronized(channelRegistry) {
            channelRegistry.forEach { try { it.close() } catch (_: Exception) {} }
            channelRegistry.clear()
        }

        if (explicitUserCancel) {
            // Explicit user cancel -> close raf and purge .part and .part.meta
            fileRaStreams.values.forEach { try { it.close() } catch (_: Exception) {} }
            fileRaStreams.clear()
            activePartFiles.values.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            activeMetaFiles.values.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            activePartFiles.clear()
            activeMetaFiles.clear()
            fileBitmaps.clear()
            fileTotalChunks.clear()
            fileReceivedBytes.clear()
            fileFinishedFlags.clear()
        } else {
            // Non-explicit cancel (e.g. socket drop) -> flush .part.meta to disk FIRST and KEEP .part!
            flushActiveMetaToDisk()
            fileRaStreams.values.forEach { try { it.close() } catch (_: Exception) {} }
            fileRaStreams.clear()
        }
    }

    private fun flushActiveMetaToDisk() {
        try {
            fileRaStreams.values.forEach { try { it.fd.sync() } catch (_: Exception) {} }
            fileBitmaps.forEach { (fid, bmap) ->
                val mFile = activeMetaFiles[fid]
                val item = activeManifestItems[fid]
                val tChunks = fileTotalChunks[fid] ?: 0
                if (mFile != null && item != null && tChunks > 0) {
                    val base64 = synchronized(bmap) {
                        PartFileManager.bitSetToBase64(bmap)
                    }
                    val meta = com.swiftshare.nft.channel.models.PartFileMeta(
                        fileId = fid,
                        fileName = item.name,
                        size = item.size,
                        chunkSize = CHUNK_SIZE,
                        totalChunks = tChunks,
                        completedChunksBase64 = base64
                    )
                    PartFileManager.writeMeta(mFile, meta)
                }
            }
        } catch (_: Exception) {}
    }
}

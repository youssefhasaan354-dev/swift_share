package com.swiftshare.nft.channel

import com.swiftshare.common.Constants
import com.swiftshare.nft.channel.models.ControlMessage
import com.swiftshare.nft.channel.models.FileStatusAck
import com.swiftshare.nft.channel.models.FileTransferStatus
import com.swiftshare.nft.channel.models.HandshakeAck
import com.swiftshare.nft.channel.models.HandshakeReq
import com.swiftshare.nft.channel.models.MessageType
import com.swiftshare.nft.channel.models.TransferManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TransferControlChannel {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var activeSocket: Socket? = null
    private var writer: PrintWriter? = null

    private val _incomingMessages = MutableSharedFlow<ControlMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<ControlMessage> = _incomingMessages.asSharedFlow()

    private var activeServerSocket: ServerSocket? = null
    var serverPort: Int = 0
        private set

    fun startServer(
        expectedSessionToken: String,
        myDeviceName: String,
        freeStorageBytes: Long,
        port: Int = Constants.PORT_CONTROL_HTTP,
        onBound: ((Int) -> Unit)? = null,
        onHandshakeComplete: (HandshakeReq) -> Unit
    ) {
        serverJob = scope.launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(port)
                activeServerSocket = serverSocket
                serverPort = serverSocket.localPort
                onBound?.invoke(serverPort)
                while (isActive) {
                    val socket = serverSocket.accept()
                    launch {
                        handleClientConnection(socket, expectedSessionToken, myDeviceName, freeStorageBytes, onHandshakeComplete)
                    }
                }
            } catch (_: Exception) {
            } finally {
                serverPort = 0
                activeServerSocket = null
                try {
                    serverSocket?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleClientConnection(
        socket: Socket,
        expectedSessionToken: String,
        myDeviceName: String,
        freeStorageBytes: Long,
        onHandshakeComplete: (HandshakeReq) -> Unit
    ) {
        try {
            activeSocket = socket
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = PrintWriter(socket.getOutputStream(), true)
            writer = out

            while (socket.isConnected && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val msg = ControlMessage.fromJson(line) ?: continue

                when (msg.type) {
                    MessageType.HANDSHAKE_REQ -> {
                        val req = msg.handshakeReq
                        if (req != null) {
                            val accepted = (expectedSessionToken.isBlank() || req.sessionToken == expectedSessionToken) &&
                                    (freeStorageBytes > 0)
                            val ack = HandshakeAck(
                                accepted = accepted,
                                reason = if (accepted) "OK" else "Session token mismatch or storage full",
                                receiverStorageBytes = freeStorageBytes,
                                sessionToken = expectedSessionToken
                            )
                            sendMessage(ControlMessage(type = MessageType.HANDSHAKE_ACK, handshakeAck = ack))
                            if (accepted) {
                                onHandshakeComplete(req)
                            }
                        }
                    }
                    else -> {
                        _incomingMessages.tryEmit(msg)
                    }
                }
            }
        } catch (e: Exception) {
            com.swiftshare.common.AppLogger.e("ControlChannel", "Socket reader terminated: ${e.message}", e)
        }
    }

    fun connectToHost(
        hostIp: String,
        myDeviceName: String,
        sessionToken: String,
        freeStorageBytes: Long,
        port: Int = Constants.PORT_CONTROL_HTTP,
        onHandshakeSuccess: (HandshakeAck) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val socket = Socket(hostIp, port)
                activeSocket = socket
                val out = PrintWriter(socket.getOutputStream(), true)
                writer = out
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Send Handshake Request
                val req = HandshakeReq(
                    deviceName = myDeviceName,
                    freeStorageBytes = freeStorageBytes,
                    sessionToken = sessionToken
                )
                sendMessage(ControlMessage(type = MessageType.HANDSHAKE_REQ, handshakeReq = req))

                launch {
                    while (socket.isConnected && !socket.isClosed) {
                        val line = reader.readLine() ?: break
                        val msg = ControlMessage.fromJson(line) ?: continue

                        if (msg.type == MessageType.HANDSHAKE_ACK) {
                            msg.handshakeAck?.let { ack ->
                                if (ack.accepted) {
                                    onHandshakeSuccess(ack)
                                } else {
                                    onError(ack.reason)
                                }
                            }
                        } else {
                            _incomingMessages.tryEmit(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to connect to control channel")
            }
        }
    }

    fun sendManifest(manifest: TransferManifest) {
        sendMessage(ControlMessage(type = MessageType.TRANSFER_MANIFEST, manifest = manifest))
    }

    fun sendStatusAck(batchId: String, fileId: String, status: FileTransferStatus, bytes: Long) {
        val ack = FileStatusAck(
            batchId = batchId,
            fileId = fileId,
            status = status,
            transferredBytes = bytes
        )
        sendMessage(ControlMessage(type = MessageType.FILE_STATUS_ACK, statusAck = ack))
    }

    fun sendFileChecksum(batchId: String, fileId: String, sha256: String) {
        val checksumMsg = com.swiftshare.nft.channel.models.FileChecksumMessage(
            batchId = batchId,
            fileId = fileId,
            sha256 = sha256
        )
        sendMessage(ControlMessage(type = MessageType.FILE_CHECKSUM, fileChecksum = checksumMsg))
    }

    fun sendFileResumeRequest(batchId: String, fileId: String, chunkCount: Int, completedChunksBase64: String) {
        val req = com.swiftshare.nft.channel.models.FileResumeRequest(
            batchId = batchId,
            fileId = fileId,
            chunkCount = chunkCount,
            completedChunksBase64 = completedChunksBase64
        )
        sendMessage(ControlMessage(type = MessageType.FILE_RESUME_REQUEST, fileResumeRequest = req))
    }

    fun sendMessage(msg: ControlMessage) {
        scope.launch {
            try {
                writer?.println(msg.toJson())
            } catch (e: Exception) {
                com.swiftshare.common.AppLogger.e("ControlChannel", "Failed to send control message: ${e.message}", e)
            }
        }
    }

    fun close() {
        serverJob?.cancel()
        try {
            activeServerSocket?.close()
        } catch (_: Exception) {}
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeServerSocket = null
        serverPort = 0
        writer = null
        activeSocket = null
    }
}

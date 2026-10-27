package com.swiftshare.nft.channel.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class MessageType {
    HANDSHAKE_REQ,
    HANDSHAKE_ACK,
    TRANSFER_MANIFEST,
    FILE_STATUS_ACK,
    PPT_COMMAND,
    HEARTBEAT,
    FILE_CHECKSUM
}

@Serializable
enum class FileTransferStatus {
    PENDING,
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Serializable
data class FileManifestItem(
    val fileId: String,
    val name: String,
    val relativePath: String = "",
    val mimeType: String = "*/*",
    val size: Long,
    val sha256: String = "",
    val thumbnailBase64: String? = null,
    val isApkBundle: Boolean = false,
    val splitApkPaths: List<String> = emptyList(),
    val obbDirPaths: List<String> = emptyList()
)

@Serializable
data class HandshakeReq(
    val deviceName: String,
    val osType: String = "Android",
    val appVersion: String = "1.0.0",
    val freeStorageBytes: Long,
    val sessionToken: String
)

@Serializable
data class HandshakeAck(
    val accepted: Boolean,
    val reason: String = "OK",
    val receiverStorageBytes: Long,
    val sessionToken: String
)

@Serializable
data class TransferManifest(
    val batchId: String,
    val fileCount: Int,
    val totalBytes: Long,
    val files: List<FileManifestItem>
)

@Serializable
data class FileChecksumMessage(
    val batchId: String,
    val fileId: String,
    val sha256: String
)

@Serializable
data class FileStatusAck(
    val batchId: String,
    val fileId: String,
    val status: FileTransferStatus,
    val transferredBytes: Long,
    val errorMsg: String? = null
)

@Serializable
data class PptCommand(
    val action: String, // PREV_SLIDE, NEXT_SLIDE, BLANK_SCREEN, FULLSCREEN, LASER_MOVE
    val x: Float = 0f,
    val y: Float = 0f
)

private val jsonSerializer = Json { ignoreUnknownKeys = true }

@Serializable
data class ControlMessage(
    val type: MessageType,
    val handshakeReq: HandshakeReq? = null,
    val handshakeAck: HandshakeAck? = null,
    val manifest: TransferManifest? = null,
    val statusAck: FileStatusAck? = null,
    val pptCommand: PptCommand? = null,
    val fileChecksum: FileChecksumMessage? = null
) {
    fun toJson(): String = jsonSerializer.encodeToString(this)

    companion object {
        fun fromJson(json: String): ControlMessage? = try {
            jsonSerializer.decodeFromString(json)
        } catch (_: Exception) {
            null
        }
    }
}


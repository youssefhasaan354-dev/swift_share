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
    FILE_CHECKSUM,
    FILE_RESUME_REQUEST,
    TRANSFER_OFFER,
    TRANSFER_RESPONSE,
    CANCEL_TRANSFER
}

@Serializable
data class FileResumeRequest(
    val batchId: String,
    val fileId: String,
    val chunkCount: Int,
    val completedChunksBase64: String
)

@Serializable
data class PartFileMeta(
    val fileId: String,
    val fileName: String,
    val size: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val completedChunksBase64: String,
    val sha256: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

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

@Serializable
data class TransferOffer(
    val batchId: String,
    val senderName: String,
    val fileCount: Int,
    val totalBytes: Long,
    val files: List<FileManifestItem>
)

@Serializable
data class TransferResponse(
    val batchId: String,
    val accepted: Boolean,
    val reason: String = "OK",
    val rejectedFileIds: List<String> = emptyList()
)

@Serializable
data class CancelTransfer(
    val batchId: String,
    val fileId: String? = null,
    val reason: String = "USER_CANCELLED"
)

@Serializable
data class HeartbeatMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String,
    val sequence: Long = 0L
)

object TransferRejectionReasons {
    const val OK = "OK"
    const val USER_REJECTED = "USER_REJECTED"
    const val NOT_ENOUGH_SPACE = "NOT_ENOUGH_SPACE"
    const val ITEM_EXISTS = "ITEM_EXISTS"
    const val CANCELLED = "CANCELLED"
}

private val jsonSerializer = Json { ignoreUnknownKeys = true }

@Serializable
data class ControlMessage(
    val type: MessageType,
    val handshakeReq: HandshakeReq? = null,
    val handshakeAck: HandshakeAck? = null,
    val manifest: TransferManifest? = null,
    val statusAck: FileStatusAck? = null,
    val pptCommand: PptCommand? = null,
    val fileChecksum: FileChecksumMessage? = null,
    val fileResumeRequest: FileResumeRequest? = null,
    val transferOffer: TransferOffer? = null,
    val transferResponse: TransferResponse? = null,
    val cancelTransfer: CancelTransfer? = null,
    val heartbeat: HeartbeatMessage? = null
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


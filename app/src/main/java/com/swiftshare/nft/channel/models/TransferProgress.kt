package com.swiftshare.nft.channel.models

data class TransferProgress(
    val batchId: String = "",
    val currentFileId: String = "",
    val currentFileName: String = "",
    val fileIndex: Int = 0,
    val totalFiles: Int = 0,
    val fileBytesTransferred: Long = 0L,
    val fileTotalBytes: Long = 0L,
    val totalBytesTransferred: Long = 0L,
    val batchTotalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val status: FileTransferStatus = FileTransferStatus.PENDING,
    val isSender: Boolean = true,
    val thumbnailBase64: String? = null
) {
    val overallProgressPercent: Float
        get() = if (batchTotalBytes > 0) {
            (totalBytesTransferred.toFloat() / batchTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val fileProgressPercent: Float
        get() = if (fileTotalBytes > 0) {
            (fileBytesTransferred.toFloat() / fileTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

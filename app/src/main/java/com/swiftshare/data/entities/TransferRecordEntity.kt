package com.swiftshare.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val batchId: String,
    val fileId: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val isIncoming: Boolean,
    val status: String, // COMPLETED, FAILED, CANCELLED
    val peerName: String,
    val speedBytesPerSec: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

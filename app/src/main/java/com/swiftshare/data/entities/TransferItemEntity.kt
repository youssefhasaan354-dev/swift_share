package com.swiftshare.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfer_items",
    indices = [Index(value = ["sessionId"])]
)
data class TransferItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val fileId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val transferredBytes: Long = 0L,
    val status: String, // PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    val uriOrPath: String,
    val errorMsg: String? = null,
    val sha256: String? = null
)

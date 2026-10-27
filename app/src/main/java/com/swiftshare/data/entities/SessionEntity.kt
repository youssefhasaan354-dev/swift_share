package com.swiftshare.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,
    val peerName: String,
    val peerIp: String,
    val isIncoming: Boolean,
    val totalFiles: Int,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val status: String, // PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)

package com.swiftshare.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safebox_vault")
data class SafeBoxEntity(
    @PrimaryKey
    val id: String,
    val originalName: String,
    val originalPath: String,
    val encryptedPath: String,
    val fileSize: Long,
    val mimeType: String,
    val dateAdded: Long = System.currentTimeMillis()
)

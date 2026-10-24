package com.swiftshare.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.swiftshare.data.entities.SafeBoxEntity
import com.swiftshare.data.entities.TransferRecordEntity

@Database(
    entities = [TransferRecordEntity::class, SafeBoxEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
    abstract fun safeBoxDao(): SafeBoxDao
}

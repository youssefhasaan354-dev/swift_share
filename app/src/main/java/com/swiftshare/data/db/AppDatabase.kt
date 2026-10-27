package com.swiftshare.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.swiftshare.data.entities.SafeBoxEntity
import com.swiftshare.data.entities.SessionEntity
import com.swiftshare.data.entities.TransferItemEntity
import com.swiftshare.data.entities.TransferRecordEntity

@Database(
    entities = [
        TransferRecordEntity::class,
        SafeBoxEntity::class,
        SessionEntity::class,
        TransferItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
    abstract fun safeBoxDao(): SafeBoxDao
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `sessionId` TEXT NOT NULL,
                        `peerName` TEXT NOT NULL,
                        `peerIp` TEXT NOT NULL,
                        `isIncoming` INTEGER NOT NULL,
                        `totalFiles` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `transferredBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER,
                        PRIMARY KEY(`sessionId`)
                    )
                """.trimIndent())

                // 2. Create transfer_items table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transfer_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `fileId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `transferredBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `uriOrPath` TEXT NOT NULL,
                        `errorMsg` TEXT,
                        `sha256` TEXT
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_items_sessionId` ON `transfer_items` (`sessionId`)")

                // 3. Migrate any legacy transfer_records into sessions and transfer_items
                db.execSQL("""
                    INSERT OR IGNORE INTO sessions (sessionId, peerName, peerIp, isIncoming, totalFiles, totalBytes, transferredBytes, status, startTime, endTime)
                    SELECT batchId, peerName, '127.0.0.1', isIncoming, COUNT(*), SUM(fileSize), SUM(fileSize), 'COMPLETED', MIN(timestamp), MAX(timestamp)
                    FROM transfer_records
                    GROUP BY batchId
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO transfer_items (sessionId, fileId, name, size, mimeType, transferredBytes, status, uriOrPath, errorMsg, sha256)
                    SELECT batchId, fileId, fileName, fileSize, mimeType, fileSize, status, filePath, NULL, NULL
                    FROM transfer_records
                """.trimIndent())
            }
        }
    }
}


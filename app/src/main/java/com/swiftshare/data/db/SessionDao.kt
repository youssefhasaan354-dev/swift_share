package com.swiftshare.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.swiftshare.data.entities.SessionEntity
import com.swiftshare.data.entities.TransferItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TransferItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TransferItemEntity>)

    @Update
    suspend fun updateItem(item: TransferItemEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM transfer_items WHERE sessionId = :sessionId")
    fun getItemsForSession(sessionId: String): Flow<List<TransferItemEntity>>

    @Query("SELECT * FROM transfer_items WHERE sessionId = :sessionId")
    suspend fun getItemsForSessionSync(sessionId: String): List<TransferItemEntity>

    @Query("SELECT * FROM transfer_items ORDER BY id DESC")
    fun getAllTransferItems(): Flow<List<TransferItemEntity>>

    @Query("UPDATE transfer_items SET status = :status, transferredBytes = :transferredBytes WHERE sessionId = :sessionId AND fileId = :fileId")
    suspend fun updateItemStatus(sessionId: String, fileId: String, status: String, transferredBytes: Long)

    @Query("UPDATE sessions SET status = :status, transferredBytes = :transferredBytes, endTime = :endTime WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String, transferredBytes: Long, endTime: Long? = null)

    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM transfer_items WHERE sessionId = :sessionId")
    suspend fun deleteItemsForSession(sessionId: String)

    @Transaction
    suspend fun deleteSessionAndItems(sessionId: String) {
        deleteItemsForSession(sessionId)
        deleteSession(sessionId)
    }

    @Query("DELETE FROM sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM transfer_items")
    suspend fun clearAllItems()

    @Transaction
    suspend fun clearAll() {
        clearAllItems()
        clearAllSessions()
    }
}

package com.swiftshare.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.swiftshare.data.entities.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecordEntity): Long

    @Update
    suspend fun update(record: TransferRecordEntity)

    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records WHERE batchId = :batchId")
    suspend fun getRecordsByBatch(batchId: String): List<TransferRecordEntity>

    @Delete
    suspend fun delete(record: TransferRecordEntity)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()
}

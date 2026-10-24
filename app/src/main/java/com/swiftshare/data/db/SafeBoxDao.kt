package com.swiftshare.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.swiftshare.data.entities.SafeBoxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafeBoxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SafeBoxEntity)

    @Query("SELECT * FROM safebox_vault ORDER BY dateAdded DESC")
    fun getAllItems(): Flow<List<SafeBoxEntity>>

    @Query("SELECT * FROM safebox_vault WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): SafeBoxEntity?

    @Delete
    suspend fun delete(item: SafeBoxEntity)
}

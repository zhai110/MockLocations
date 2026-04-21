package com.mockloc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update

/**
 * 历史记录DAO
 */
@Dao
interface HistoryLocationDao {
    
    @Query("SELECT * FROM history_location ORDER BY timestamp DESC")
    suspend fun getAll(): List<HistoryLocation>
    
    @Query("SELECT * FROM history_location WHERE name LIKE :keyword OR address LIKE :keyword ORDER BY timestamp DESC")
    suspend fun search(keyword: String): List<HistoryLocation>
    
    @Query("SELECT * FROM history_location WHERE id = :id")
    suspend fun getById(id: Long): HistoryLocation?
    
    @Insert
    suspend fun insert(location: HistoryLocation)
    
    @Update
    suspend fun update(location: HistoryLocation)
    
    @Delete
    suspend fun delete(location: HistoryLocation)
    
    @Query("DELETE FROM history_location WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM history_location")
    suspend fun deleteAll()
    
    @Query("DELETE FROM history_location WHERE id = :id")
    suspend fun deleteById(id: Long)
}

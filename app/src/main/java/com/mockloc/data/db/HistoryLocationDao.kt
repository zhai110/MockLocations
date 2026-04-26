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
    
    /**
     * 检查是否存在相同坐标的历史记录
     * ✅ 修复：使用 ROUND 函数处理浮点数精度问题，保留6位小数（约0.1米精度）
     */
    @Query("SELECT EXISTS(SELECT 1 FROM history_location WHERE ROUND(latitude, 6) = ROUND(:lat, 6) AND ROUND(longitude, 6) = ROUND(:lng, 6))")
    suspend fun exists(lat: Double, lng: Double): Boolean
    
    /**
     * 根据坐标查询历史记录
     * ✅ 修复：同样使用 ROUND 确保查询准确性
     */
    @Query("SELECT * FROM history_location WHERE ROUND(latitude, 6) = ROUND(:lat, 6) AND ROUND(longitude, 6) = ROUND(:lng, 6) LIMIT 1")
    suspend fun getByCoordinates(lat: Double, lng: Double): HistoryLocation?
    
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
    
    /**
     * ✅ 新增：清理旧数据，只保留最近的 limit 条记录
     */
    @Query("DELETE FROM history_location WHERE id NOT IN (SELECT id FROM history_location ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun keepRecentRecords(limit: Int)
}

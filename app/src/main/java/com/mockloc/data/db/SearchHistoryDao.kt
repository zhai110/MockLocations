package com.mockloc.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

/**
 * 搜索历史数据访问对象
 */
@Dao
interface SearchHistoryDao {
    
    /**
     * 根据坐标查询搜索历史（检查是否已存在）
     */
    @Query("SELECT * FROM search_history WHERE latitude = :lat AND longitude = :lng LIMIT 1")
    suspend fun findByCoordinates(lat: Double, lng: Double): SearchHistory?
    
    /**
     * 更新搜索历史的时间戳
     */
    @Query("UPDATE search_history SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)
    
    /**
     * 查询所有搜索历史（按时间倒序）
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<SearchHistory>
    
    /**
     * 插入搜索历史
     */
    @Insert
    suspend fun insert(history: SearchHistory): Long
    
    /**
     * 删除单条记录
     */
    @Delete
    suspend fun delete(history: SearchHistory)
    
    /**
     * 根据ID删除
     */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * 清空所有记录
     */
    @Query("DELETE FROM search_history")
    suspend fun clearAll()
    
    /**
     * 限制最多保留100条记录，删除旧的
     */
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 100)")
    suspend fun limitRecords()
}

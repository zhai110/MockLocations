package com.mockloc.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 搜索历史数据访问对象
 */
@Dao
interface SearchHistoryDao {
    
    /**
     * 根据坐标查询搜索历史（检查是否已存在）
     * 注意：调用方需确保传入的坐标已统一精度（6位小数）
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
     * 插入搜索历史（如果坐标已存在则替换并更新时间戳）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
     * 限制最多保留指定条数记录，删除旧的
     */
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun limitRecords(limit: Int = 100)
}

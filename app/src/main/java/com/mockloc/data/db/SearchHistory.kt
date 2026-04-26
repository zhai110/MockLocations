package com.mockloc.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 搜索历史记录
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["keyword"]),       // 优化关键词搜索
        Index(value = ["timestamp"]),     // 优化按时间清理旧记录
        Index(value = ["latitude", "longitude"], unique = true) // ✅ 确保同一坐标只保留一条最新记录
    ]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,          // 搜索关键词
    val name: String,             // 地点名称
    val address: String,          // 地址
    val latitude: Double,         // 纬度（GCJ02）
    val longitude: Double,        // 经度（GCJ02）
    val timestamp: Long = System.currentTimeMillis()  // 搜索时间
)

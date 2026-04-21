package com.mockloc.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏位置实体
 */
@Entity(tableName = "favorite_location")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 位置名称
    val name: String,
    
    // 地址描述
    val address: String = "",
    
    // GCJ02坐标（高德地图坐标系）
    val latitude: Double,
    val longitude: Double,
    
    // 时间戳
    val timestamp: Long = System.currentTimeMillis()
)

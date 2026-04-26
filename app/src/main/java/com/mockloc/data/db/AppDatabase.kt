package com.mockloc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库
 * 
 * 数据库版本历史：
 * - version 1: 初始版本，包含历史记录和收藏位置表
 * - version 2: 添加搜索历史表 + 为常用字段添加索引
 * 
 * 迁移策略：
 * - 已定义 MIGRATION_1_2，支持从版本1平滑升级到版本2
 * - 移除了 fallbackToDestructiveMigration()，保护用户数据
 * - 后续版本升级时，应在此添加新的 Migration 策略
 */
@Database(
    entities = [HistoryLocation::class, FavoriteLocation::class, SearchHistory::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyLocationDao(): HistoryLocationDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    
    companion object {
        /**
         * 从版本2升级到版本3：
         * 1. 清理搜索历史表中的重复坐标记录（保留最早的一条）
         * 2. 为搜索历史表添加唯一索引，防止后续产生重复
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 清理重复数据：保留每个坐标下 id 最小（最早插入）的那条记录
                database.execSQL("""
                    DELETE FROM search_history 
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM search_history GROUP BY latitude, longitude
                    )
                """.trimIndent())
                
                // 2. 创建唯一索引：同一经纬度只保留一条记录
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_search_lat_lng_unique ON search_history(latitude, longitude)"
                )
            }
        }

        /**
         * 从版本1升级到版本2：
         * 1. 创建搜索历史表
         * 2. 为现有表添加索引以优化查询性能
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建搜索历史表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        keyword TEXT NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // 2. 为历史记录表添加时间戳索引（优化按时间排序查询）
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_history_timestamp ON history_location(timestamp)"
                )
                
                // 3. 为收藏位置表添加时间戳索引
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_favorite_timestamp ON favorite_location(timestamp)"
                )
                
                // 4. 为搜索历史表添加复合索引（优化关键词搜索和时间清理）
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_search_keyword ON search_history(keyword)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_search_timestamp ON search_history(timestamp)"
                )
            }
        }
    }
}

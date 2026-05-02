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
    entities = [HistoryLocation::class, FavoriteLocation::class, SearchHistory::class, SavedRoute::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyLocationDao(): HistoryLocationDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savedRouteDao(): SavedRouteDao
    
    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS saved_route (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        route_group TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        pointOrder INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_route_timestamp ON saved_route(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_route_route_group ON saved_route(route_group)")
            }
        }

        /**
         * 从版本3升级到版本4：
         * 修复索引名称，使其与 Room 自动生成的命名规则一致（index_{tableName}_{columns}）
         * 旧索引名是自定义的 idx_ 前缀，Room schema 验证不通过
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 重命名 history_location 表的索引
                database.execSQL("DROP INDEX IF EXISTS idx_history_timestamp")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_history_location_timestamp ON history_location(timestamp)")

                // 重命名 favorite_location 表的索引
                database.execSQL("DROP INDEX IF EXISTS idx_favorite_timestamp")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_location_timestamp ON favorite_location(timestamp)")

                // 重命名 search_history 表的索引
                database.execSQL("DROP INDEX IF EXISTS idx_search_keyword")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_keyword ON search_history(keyword)")

                database.execSQL("DROP INDEX IF EXISTS idx_search_timestamp")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)")

                // 重命名 search_history 唯一索引
                database.execSQL("DROP INDEX IF EXISTS idx_search_lat_lng_unique")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_latitude_longitude ON search_history(latitude, longitude)")
            }
        }

        /**
         * 从版本2升级到版本3：
         * 1. 清理搜索历史表中的重复坐标记录（保留最新的一条）
         * 2. 为搜索历史表添加唯一索引，防止后续产生重复
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    // 1. 清理重复数据：保留每个坐标下 timestamp 最大（最新）的那条记录
                    database.execSQL("""
                        DELETE FROM search_history 
                        WHERE id NOT IN (
                            SELECT id FROM (
                                SELECT id, MAX(timestamp) as max_ts 
                                FROM search_history 
                                GROUP BY latitude, longitude
                            )
                        )
                    """.trimIndent())
                    
                    // 2. 创建唯一索引：同一经纬度只保留一条记录（使用 Room 标准命名）
                    database.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_latitude_longitude ON search_history(latitude, longitude)"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "MIGRATION_2_3 failed: ${e.message}", e)
                    throw e
                }
            }
        }

        /**
         * 从版本1升级到版本2：
         * 1. 创建搜索历史表
         * 2. 为现有表添加索引以优化查询性能（使用 Room 标准命名 index_{tableName}_{columns}）
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
                
                // 2. 为历史记录表添加时间戳索引（Room 标准命名）
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_history_location_timestamp ON history_location(timestamp)"
                )
                
                // 3. 为收藏位置表添加时间戳索引（Room 标准命名）
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_favorite_location_timestamp ON favorite_location(timestamp)"
                )
                
                // 4. 为搜索历史表添加复合索引（Room 标准命名）
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_search_history_keyword ON search_history(keyword)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)"
                )
            }
        }
    }
}

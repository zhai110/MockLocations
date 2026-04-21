package com.mockloc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库
 * 
 * 数据库版本说明：
 * - version 1: 初始版本，包含历史记录和收藏位置表
 * - version 2: 添加搜索历史表
 * 
 * 迁移策略：
 * - 使用 fallbackToDestructiveMigration() 作为兜底策略
 * - 当没有定义具体迁移时，会删除所有表并重新创建
 * - 后续版本升级时，应添加具体的 Migration 策略以保留用户数据
 */
@Database(
    entities = [HistoryLocation::class, FavoriteLocation::class, SearchHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyLocationDao(): HistoryLocationDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    
    companion object {
        /**
         * 从版本1升级到版本2：添加搜索历史表
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建搜索历史表
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
            }
        }
        /**
         * 数据库迁移策略
         * 
         * 当数据库版本升级时，在此添加迁移策略。
         * 例如：从版本1升级到版本2
         * 
         * val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         // 执行迁移SQL
         *         database.execSQL("ALTER TABLE history_location ADD COLUMN new_column TEXT")
         *     }
         * }
         * 
         * 使用方式：
         * Room.databaseBuilder(...)
         *     .addMigrations(AppDatabase.MIGRATION_1_2)
         *     .build()
         */
        
        // 预留迁移策略示例（当前版本为1，无需迁移）
        // 当需要升级到版本2时，取消注释并实现迁移逻辑
        /*
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 示例：添加新列
                // database.execSQL("ALTER TABLE history_location ADD COLUMN new_field TEXT DEFAULT ''")
            }
        }
        */
    }
}

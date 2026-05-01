package com.mockloc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.amap.api.location.AMapLocationClient
import com.mockloc.data.db.AppDatabase
import com.mockloc.util.AddressCache
import com.mockloc.util.AnimationConfig
import com.mockloc.util.CrashHandler
import timber.log.Timber

/**
 * 应用程序入口类
 */
class VirtualLocationApp : Application() {

    companion object {
        const val CHANNEL_ID_LOCATION = "location_service"
        const val CHANNEL_ID_FLOATING = "floating_window"

        @Volatile
        private var instance: VirtualLocationApp? = null
        @Volatile
        private var database: AppDatabase? = null

        fun getInstance(): VirtualLocationApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }

        fun getDatabase(): AppDatabase {
            return database ?: throw IllegalStateException("Database not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化全局异常处理器（最先初始化）
        CrashHandler.init(this)
        Timber.d("CrashHandler initialized")

        // 初始化日志
        initTimber()

        // 初始化数据库
        initDatabase()

        // 创建通知渠道
        createNotificationChannels()

        // 初始化高德地图
        initAMap()

        // 设置跟随系统主题（支持浅色/深色模式自动切换）
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // 初始化动画配置（检测系统动画偏好和无障碍设置）
        AnimationConfig.initialize(this)

        // 初始化地址缓存
        AddressCache.init(this)

        Timber.d("Application initialized")
    }

    override fun onTerminate() {
        super.onTerminate()
        // 释放 AddressCache 资源，取消协程作用域
        AddressCache.destroy()
        Timber.d("Application terminated, resources cleaned up")
    }

    /**
     * 初始化数据库
     * 
     * 数据库配置说明：
     * 1. addMigrations() - 添加数据库迁移策略，用于版本升级时保留用户数据
     * 2. fallbackToDestructiveMigration() - 当迁移失败时的降级方案（仅 DEBUG 模式）
     * 
     * 生产环境策略：
     * - 优先使用 Migration 平滑升级，保护用户数据
     * - 如果迁移失败，记录日志并上报，避免静默崩溃
     * 
     * DEBUG 环境策略：
     * - 启用 fallbackToDestructiveMigration()，快速重置数据库便于调试
     */
    private fun initDatabase() {
        val builder = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "virtual_location.db"
        )
            // 添加迁移策略：从版本1→2→3→4
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        
        // ✅ 生产环境 & 测试环境：严格使用 Migration，保护用户数据
        // ✅ 如需调试，手动清除应用数据后重新安装
        Timber.d("Database initialized with strict migration policy")
        
        database = builder.build()
    }

    /**
     * 初始化 Timber 日志
     */
    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // 生产环境可以上传到服务器
            // Timber.plant(CrashReportingTree())
        }
    }

    /**
     * 创建通知渠道 (Android 8.0+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 定位服务通知渠道
            val locationChannel = NotificationChannel(
                CHANNEL_ID_LOCATION,
                "定位服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "虚拟定位服务运行状态"
                setShowBadge(false)
            }
            
            // 悬浮窗通知渠道
            val floatingChannel = NotificationChannel(
                CHANNEL_ID_FLOATING,
                "悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗控制面板"
                setShowBadge(false)
            }
            
            notificationManager.createNotificationChannels(listOf(locationChannel, floatingChannel))
        }
    }

    /**
     * 初始化高德地图
     */
    private fun initAMap() {
        // 高德地图隐私合规初始化（必须在任何高德SDK调用之前）
        try {
            // 设置隐私合规
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
            Timber.d("AMap privacy initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AMap privacy")
        }
    }
}
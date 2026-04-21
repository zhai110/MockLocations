package com.mockloc.util

/**
 * 应用常量配置
 * 
 * 注意：动画配置请使用 AnimationConfig.kt 中的 AnimationConfig 对象
 */

/**
 * UI尺寸常量
 */
object UiDimensions {
    // BottomSheet
    const val BOTTOM_SHEET_PEEK_HEIGHT_DP = 340
    const val BOTTOM_SHEET_PADDING_BOTTOM_DP = 80
    
    // FAB
    const val FAB_MARGIN_BOTTOM_DP = 150
    const val FAB_TRANSLATION_Y_DP = 50
    
    // 导航栏
    const val NAVIGATION_BAR_HEIGHT_DP = 56
    
    // 状态栏
    const val STATUS_BAR_MARGIN_DP = 16
    
    // 搜索栏
    const val SEARCH_CARD_CORNER_RADIUS_DP = 16
    const val SEARCH_CARD_ELEVATION_DP = 4
}

/**
 * 数据库配置
 */
object DatabaseConfig {
    const val DATABASE_NAME = "virtual_location.db"
    const val DATABASE_VERSION = 1
    
    // 历史记录限制
    const val HISTORY_MAX_COUNT = 100
    const val HISTORY_CLEANUP_DAYS = 30
    
    // 收藏限制
    const val FAVORITE_MAX_COUNT = 50
}

/**
 * 地图配置
 */
object MapConfig {
    // 默认缩放级别
    const val DEFAULT_ZOOM_LEVEL = 15f
    const val MIN_ZOOM_LEVEL = 3f
    const val MAX_ZOOM_LEVEL = 20f
    
    // 标记限制
    const val MAX_MARKERS_COUNT = 100
    
    // 定位精度
    const val LOCATION_ACCURACY_HIGH = 10f  // 米
    const val LOCATION_ACCURACY_MEDIUM = 50f
    const val LOCATION_ACCURACY_LOW = 100f
}

/**
 * 位置模拟配置
 */
object LocationSimulatorConfig {
    // 速度配置（米/秒）
    const val SPEED_WALKING = 1.4f        // 步行 ~5km/h
    const val SPEED_RUNNING = 3.0f        // 跑步 ~11km/h
    const val SPEED_CYCLING = 5.5f        // 骑行 ~20km/h
    const val SPEED_DRIVING = 16.7f       // 驾车 ~60km/h
    
    // 摇杆配置
    const val JOYSTICK_MAX_SPEED = 20.0f  // 最大速度 m/s
    const val JOYSTICK_DEAD_ZONE = 0.1f   // 死区
    
    // 更新间隔（毫秒）
    const val UPDATE_INTERVAL_FAST = 100L
    const val UPDATE_INTERVAL_NORMAL = 500L
    const val UPDATE_INTERVAL_SLOW = 1000L
    
    // 随机偏移（米）
    const val RANDOM_OFFSET_MAX = 5.0f
}

/**
 * 权限请求配置
 */
object PermissionConfig {
    // 权限请求码
    const val REQUEST_CODE_LOCATION = 1001
    const val REQUEST_CODE_FLOATING_WINDOW = 1002
    const val REQUEST_CODE_STORAGE = 1003
    const val REQUEST_CODE_NOTIFICATION = 1004
    
    // Rationale显示阈值
    const val RATIONAL_SHOW_THRESHOLD = 2  // 拒绝2次后显示说明
}

/**
 * 网络配置
 */
object NetworkConfig {
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    
    // POI搜索
    const val POI_SEARCH_PAGE_SIZE = 20
    const val POI_SEARCH_RADIUS = 50000  // 50公里
}

/**
 * 通知配置
 */
object NotificationConfig {
    const val CHANNEL_ID_LOCATION = "location_service"
    const val CHANNEL_ID_FLOATING = "floating_window"
    
    const val NOTIFICATION_ID_LOCATION = 1001
    const val NOTIFICATION_ID_FLOATING = 1002
}

/**
 * SharedPreferences键名
 */
object PrefKeys {
    const val PREFS_NAME = "virtual_location_prefs"
    
    // 用户偏好
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_MAP_TYPE = "map_type"
    const val KEY_SIMULATION_SPEED = "simulation_speed"
    const val KEY_RANDOM_OFFSET_ENABLED = "random_offset_enabled"
    
    // 首次启动
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    
    // 统计
    const val KEY_USAGE_COUNT = "usage_count"
    const val KEY_LAST_USAGE_TIME = "last_usage_time"
}

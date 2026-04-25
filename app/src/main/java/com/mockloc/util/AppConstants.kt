package com.mockloc.util

/**
 * 应用常量配置
 * 
 * 注意：
 * 1. 动画配置请使用 AnimationConfig.kt 中的 AnimationConfig 对象
 * 2. 本文件仅保留实际被使用的常量，避免技术债务
 * 3. 已移除的常量：UiDimensions, DatabaseConfig, MapConfig, LocationSimulatorConfig, NetworkConfig, NotificationConfig, PrefKeys
 */

/**
 * SharedPreferences 配置
 * 
 * 统一管理所有 SP 文件名和键名，避免硬编码字符串
 */
object PrefsConfig {
    // ==================== SP 文件名 ====================
    const val SETTINGS = "settings"              // 用户设置、模拟配置
    const val MAP_STATE = "map_state"            // 地图状态（中心点、缩放）
    const val ADDRESS_CACHE = "address_cache"    // 地址缓存
    const val ONBOARDING = "onboarding_prefs"    // 新手引导状态
    const val PERMISSION = "permission_prefs"    // 权限请求历史
    
    // ==================== Settings 中的键名 ====================
    object Settings {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MAP_TYPE = "map_type"
        const val KEY_SIMULATION_SPEED = "simulation_speed"
        const val KEY_RANDOM_OFFSET_ENABLED = "random_offset_enabled"
        const val KEY_JOYSTICK_TYPE = "joystick_type"
        const val KEY_JOYSTICK_SIZE = "joystick_size"
        const val KEY_JOYSTICK_TRANSPARENCY = "joystick_transparency"
        
        // 移动速度设置
        const val KEY_WALK_SPEED = "walk_speed"              // 步行速度 (km/h)
        const val KEY_RUN_SPEED = "run_speed"                // 跑步速度 (km/h)
        const val KEY_BIKE_SPEED = "bike_speed"              // 骑行速度 (km/h)
        
        // 位置设置
        const val KEY_ALTITUDE = "altitude"                  // 海拔高度 (米)
        const val KEY_LOCATION_UPDATE_INTERVAL = "location_update_interval"  // 位置更新频率 (ms)
        const val KEY_RANDOM_OFFSET = "random_offset"        // 随机偏移开关
        
        // 摇杆设置
        const val KEY_JOYSTICK_HAPTIC = "joystick_haptic"    // 摇杆触觉反馈
        
        // 其他设置
        const val KEY_AUTO_START = "auto_start"              // 开机自启
        const val KEY_LOGGING = "logging"                    // 日志开关
        const val KEY_HISTORY_EXPIRY = "history_expiry"      // 历史记录有效期 (天)
    }
    
    // ==================== Map State 中的键名 ====================
    object MapState {
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ZOOM = "zoom"
        const val KEY_MARKED_LAT = "marked_lat"      // 标记位置纬度
        const val KEY_MARKED_LNG = "marked_lng"      // 标记位置经度
    }
    
    // ==================== Onboarding 中的键名前缀 ====================
    object Onboarding {
        const val KEY_PREFIX = "shown_"
        
        // 引导类型
        const val GUIDE_MOCK_LOCATION_PERMISSION = "${KEY_PREFIX}mock_location_permission"
        const val GUIDE_JOYSTICK_USAGE = "${KEY_PREFIX}joystick_usage"
        const val GUIDE_LOCATION_SEARCH = "${KEY_PREFIX}location_search"
        const val GUIDE_FAVORITE_LOCATION = "${KEY_PREFIX}favorite_location"
        const val GUIDE_FLOATING_WINDOW = "${KEY_PREFIX}floating_window"
        const val GUIDE_MAP_CONTROLS = "${KEY_PREFIX}map_controls"
    }
    
    // ==================== Permission 中的键名 ====================
    object Permission {
        const val KEY_LOCATION_REQUEST_COUNT = "location_request_count"
        const val KEY_LOCATION_DENIED_COUNT = "location_denied_count"
    }
}

/**
 * 权限请求配置
 */
object PermissionConfig {
    // Rationale显示阈值
    const val RATIONAL_SHOW_THRESHOLD = 2  // 拒绝2次后显示说明
}

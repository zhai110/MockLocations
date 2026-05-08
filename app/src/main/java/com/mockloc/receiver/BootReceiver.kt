package com.mockloc.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mockloc.service.LocationService
import com.mockloc.util.PrefsConfig
import timber.log.Timber

/**
 * 开机自启接收器
 * 
 * 功能说明：
 * - 监听系统开机完成广播 (ACTION_BOOT_COMPLETED)
 * - 根据用户设置决定是否自动启动定位服务
 * - 读取上次模拟的位置信息并恢复
 * 
 * 兼容性注意事项：
 * - 需要声明 RECEIVE_BOOT_COMPLETED 权限
 * - Android 10+ 需要用户手动授权后台定位权限
 * - Android 12+ 后台启动前台服务有限制，可能抛出 ForegroundServiceStartNotAllowedException
 * - 部分厂商ROM（小米、华为、OPPO、vivo等）默认禁止应用自启，
 *   需要用户在系统设置中手动允许自启，本应用无法绕过此限制
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed received")
            
            try {
                // 读取设置，判断是否启用开机自启
                val prefs = context.getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)
                val autoStartEnabled = prefs.getBoolean(PrefsConfig.Settings.KEY_AUTO_START, false)
                
                if (autoStartEnabled) {
                    Timber.d("Auto start enabled, starting service...")
                    
                    // ✅ 修复：使用 toDoubleOrNull() 安全转换，并验证坐标范围
                    val lastLat = prefs.getString(PrefsConfig.Settings.KEY_LAST_LAT, null)?.toDoubleOrNull()
                        ?: prefs.getFloat(PrefsConfig.Settings.KEY_LAST_LAT, 0f).toDouble()
                    val lastLng = prefs.getString(PrefsConfig.Settings.KEY_LAST_LNG, null)?.toDoubleOrNull()
                        ?: prefs.getFloat(PrefsConfig.Settings.KEY_LAST_LNG, 0f).toDouble()
                    val lastAlt = prefs.getString(PrefsConfig.Settings.KEY_LAST_ALT, null)?.toDoubleOrNull()
                        ?: prefs.getFloat(PrefsConfig.Settings.KEY_LAST_ALT, 55.0f).toDouble()
                    
                    // 验证坐标范围是否有效（纬度 -90~90，经度 -180~180）
                    val isValidLat = lastLat in -90.0..90.0 && lastLat != 0.0
                    val isValidLng = lastLng in -180.0..180.0 && lastLng != 0.0
                    
                    if (isValidLat && isValidLng) {
                        val serviceIntent = Intent(context, LocationService::class.java).apply {
                            action = LocationService.ACTION_START
                            putExtra(LocationService.EXTRA_LATITUDE, lastLat)
                            putExtra(LocationService.EXTRA_LONGITUDE, lastLng)
                            putExtra(LocationService.EXTRA_ALTITUDE, lastAlt)
                            putExtra(LocationService.EXTRA_COORD_GCJ02, true) // 标记为GCJ02坐标
                        }
                        
                        try {
                            // Android 8.0+ 需要使用 startForegroundService
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            Timber.d("Service started with last location: ($lastLat, $lastLng)")
                        } catch (e: IllegalStateException) {
                            // Android 12+ 后台启动前台服务限制
                            // ForegroundServiceStartNotAllowedException 是 IllegalStateException 的子类
                            Timber.w(e, "Cannot start foreground service from background (Android 12+ restriction). " +
                                "User may need to disable battery optimization for this app.")
                        }
                    } else {
                        Timber.d("No valid last location, skip auto start")
                    }
                } else {
                    Timber.d("Auto start disabled, skip")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to auto start service. " +
                    "If on a custom ROM (Xiaomi/Huawei/OPPO/vivo), check if auto-start is allowed in system settings.")
            }
        }
    }
}
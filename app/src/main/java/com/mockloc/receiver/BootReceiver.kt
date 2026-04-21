package com.mockloc.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mockloc.service.LocationService
import timber.log.Timber

/**
 * 开机自启接收器
 * 
 * 功能说明：
 * - 监听系统开机完成广播 (ACTION_BOOT_COMPLETED)
 * - 根据用户设置决定是否自动启动定位服务
 * - 读取上次模拟的位置信息并恢复
 * 
 * 注意事项：
 * - 需要声明 RECEIVE_BOOT_COMPLETED 权限
 * - Android 10+ 需要用户手动授权后台定位权限
 * - 部分厂商ROM可能限制自启，需要在系统设置中允许
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed received")
            
            try {
                // 读取设置，判断是否启用开机自启
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val autoStartEnabled = prefs.getBoolean("auto_start", false)
                
                if (autoStartEnabled) {
                    Timber.d("Auto start enabled, starting service...")
                    
                    // 读取上次保存的位置信息（使用String保存Double精度，兼容旧版Float格式）
                    val lastLat = prefs.getString("last_lat", null)?.toDouble()
                        ?: prefs.getFloat("last_lat", 0f).toDouble()
                    val lastLng = prefs.getString("last_lng", null)?.toDouble()
                        ?: prefs.getFloat("last_lng", 0f).toDouble()
                    val lastAlt = prefs.getString("last_alt", null)?.toDouble()
                        ?: prefs.getFloat("last_alt", 55.0f).toDouble()
                    
                    // 如果有有效的位置信息，则启动服务
                    if (lastLat != 0.0 && lastLng != 0.0) {
                        val serviceIntent = Intent(context, LocationService::class.java).apply {
                            action = LocationService.ACTION_START
                            putExtra(LocationService.EXTRA_LATITUDE, lastLat)
                            putExtra(LocationService.EXTRA_LONGITUDE, lastLng)
                            putExtra(LocationService.EXTRA_ALTITUDE, lastAlt)
                            putExtra(LocationService.EXTRA_COORD_GCJ02, true) // 标记为GCJ02坐标
                        }
                        
                        // Android 8.0+ 需要使用 startForegroundService
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        
                        Timber.d("Service started with last location: ($lastLat, $lastLng)")
                    } else {
                        Timber.d("No valid last location, skip auto start")
                    }
                } else {
                    Timber.d("Auto start disabled, skip")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to auto start service")
            }
        }
    }
}
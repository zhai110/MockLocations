package com.mockloc.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mockloc.R

/**
 * 渐进式权限管理器
 * 
 * 功能：
 * 1. 分阶段请求权限，避免一次性弹出多个对话框
 * 2. 显示权限说明Rationale，让用户理解为什么需要权限
 * 3. 永久拒绝后引导用户去设置页面
 * 4. 记录权限请求历史，智能判断何时显示Rationale
 * 
 * 注意：此类不再管理回调，回调由 Activity 通过 ActivityResultLauncher 处理
 */
object ProgressivePermissionManager {
    
    // SharedPreferences键名
    private const val PREFS_NAME = "permission_prefs"
    private const val KEY_LOCATION_REQUEST_COUNT = "location_request_count"
    private const val KEY_LOCATION_DENIED_COUNT = "location_denied_count"
    
    /**
     * 检查是否应该显示权限说明（Rationale）
     * 
     * @param activity 当前 Activity
     * @return 应该显示 Rationale 时返回 true
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestCount = prefs.getInt(KEY_LOCATION_REQUEST_COUNT, 0)
        val deniedCount = prefs.getInt(KEY_LOCATION_DENIED_COUNT, 0)
            
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
            
        return requestCount == 0 || shouldShowRationale
    }
        
    /**
     * 检查是否应该引导用户去设置页面
     * 
     * @param activity 当前 Activity
     * @return 应该引导去设置时返回 true
     */
    fun shouldGuideToSettings(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deniedCount = prefs.getInt(KEY_LOCATION_DENIED_COUNT, 0)
            
        return deniedCount >= PermissionConfig.RATIONAL_SHOW_THRESHOLD
    }
        
    /**
     * 记录权限被拒绝
     */
    fun recordPermissionDenied(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deniedCount = prefs.getInt(KEY_LOCATION_DENIED_COUNT, 0)
        prefs.edit().putInt(KEY_LOCATION_DENIED_COUNT, deniedCount + 1).apply()
    }
        
    /**
     * 记录权限请求次数
     */
    fun recordPermissionRequest(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestCount = prefs.getInt(KEY_LOCATION_REQUEST_COUNT, 0)
        prefs.edit().putInt(KEY_LOCATION_REQUEST_COUNT, requestCount + 1).apply()
    }
    
    /**
     * 重置权限请求历史（用于测试或用户主动重置）
     */
    fun resetPermissionHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_LOCATION_REQUEST_COUNT)
            .remove(KEY_LOCATION_DENIED_COUNT)
            .apply()
    }
}

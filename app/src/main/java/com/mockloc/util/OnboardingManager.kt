package com.mockloc.util

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.mockloc.R

/**
 * 新手引导管理器
 * 
 * 功能：
 * 1. 追踪用户是否已完成某个功能的初次使用
 * 2. 在合适的时机显示引导提示
 * 3. 避免重复显示相同的引导
 */
object OnboardingManager {
    
    // SharedPreferences键名前缀
    private const val PREFS_NAME = "onboarding_prefs"
    private const val KEY_PREFIX = "shown_"
    
    // 引导类型枚举
    enum class GuideType {
        MOCK_LOCATION_PERMISSION,  // 模拟位置权限
        JOYSTICK_USAGE,            // 摇杆使用
        LOCATION_SEARCH,           // 位置搜索
        FAVORITE_LOCATION,         // 收藏位置
        FLOATING_WINDOW,           // 悬浮窗功能
        MAP_CONTROLS               // 地图控制按钮
    }
    
    /**
     * 检查是否已显示过某个引导
     */
    fun hasShownGuide(context: Context, guideType: GuideType): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFIX + guideType.name, false)
    }
    
    /**
     * 标记引导已显示
     */
    fun markGuideAsShown(context: Context, guideType: GuideType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREFIX + guideType.name, true).apply()
    }
    
    /**
     * 显示引导提示（只显示一次）
     * 
     * @param context 上下文
     * @param view 显示Snackbar的视图
     * @param guideType 引导类型
     * @param message 提示消息
     * @param actionText 可选的操作按钮文本
     * @param onAction 可选的操作回调
     */
    fun showGuideOnce(
        context: Context,
        view: View,
        guideType: GuideType,
        message: String,
        actionText: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        if (hasShownGuide(context, guideType)) {
            return
        }
        
        // 显示引导
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.primary))
        snackbar.setTextColor(ContextCompat.getColor(context, R.color.white))
        
        actionText?.let {
            snackbar.setAction(it) {
                onAction?.invoke()
            }
            snackbar.setActionTextColor(ContextCompat.getColor(context, R.color.white))
        }
        
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                // 用户看到后标记为已显示
                markGuideAsShown(context, guideType)
            }
        })
        
        snackbar.show()
    }
    
    /**
     * 重置所有引导状态（用于测试或重新引导）
     */
    fun resetAllGuides(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
    
    /**
     * 重置特定引导状态
     */
    fun resetGuide(context: Context, guideType: GuideType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PREFIX + guideType.name).apply()
    }
}

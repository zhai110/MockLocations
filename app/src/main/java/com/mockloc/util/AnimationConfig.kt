package com.mockloc.util

import android.content.Context
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 动画配置管理器
 * 
 * 功能：
 * 1. 检测系统动画偏好（减少动画模式）
 * 2. 提供统一的动画时长配置
 * 3. 支持运行时动态调整动画速度
 */
object AnimationConfig {
    
    /**
     * 动画速度倍率（1.0 = 正常，0.5 = 慢速，0.0 = 禁用）
     */
    private var animationScale: Float = 1.0f
    
    /**
     * 是否启用动画（考虑系统设置和用户偏好）
     */
    private var animationsEnabled: Boolean = true
    
    /**
     * 基础动画时长配置（毫秒）
     */
    object Duration {
        const val INSTANT = 0L          // 即时
        const val FAST = 150L           // 快速
        const val NORMAL = 250L         // 正常
        const val SLOW = 400L           // 慢速
        const val VERY_SLOW = 600L      // 非常慢
        
        // 特定场景的推荐时长
        const val BUTTON_PRESS = FAST
        const val FADE_IN = NORMAL
        const val FADE_OUT = FAST
        const val SLIDE_IN = NORMAL
        const val SLIDE_OUT = FAST
        const val BOUNCE_IN = VERY_SLOW
        const val LIST_ITEM_STAGGER = 50L  // 列表项级联延迟
    }
    
    /**
     * 初始化动画配置
     * 应在Application.onCreate()中调用
     */
    fun initialize(context: Context) {
        // 检测系统动画缩放比例
        detectSystemAnimationScale(context)
        
        // 检测是否启用减少动画
        checkReducedMotion(context)
    }
    
    /**
     * 检测系统动画缩放比例
     */
    private fun detectSystemAnimationScale(context: Context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            animationScale = scale.coerceIn(0f, 10f)  // 限制在0-10倍
        } catch (e: Exception) {
            animationScale = 1.0f
        }
    }
    
    /**
     * 检查是否启用减少动画（无障碍设置）
     */
    private fun checkReducedMotion(context: Context) {
        try {
            val reducedMotion = Settings.Secure.getInt(
                context.contentResolver,
                "reduce_animations",
                0
            ) == 1
            
            // Android 10+ 的新API
            val transitionAnimationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            
            // 如果用户选择了减少动画或动画比例为0，则禁用动画
            animationsEnabled = !reducedMotion && transitionAnimationScale > 0f
        } catch (e: Exception) {
            animationsEnabled = true
        }
    }
    
    /**
     * 获取调整后的动画时长
     * 根据系统动画缩放比例和无障碍设置自动调整
     */
    fun getAdjustedDuration(baseDuration: Long): Long {
        if (!animationsEnabled) {
            return 0L  // 禁用动画时返回0
        }
        return (baseDuration * animationScale).toLong()
    }
    
    /**
     * 获取标准淡入时长
     */
    fun getFadeInDuration(): Long {
        return getAdjustedDuration(Duration.FADE_IN)
    }
    
    /**
     * 获取标准淡出时长
     */
    fun getFadeOutDuration(): Long {
        return getAdjustedDuration(Duration.FADE_OUT)
    }
    
    /**
     * 获取按钮按压时长
     */
    fun getButtonPressDuration(): Long {
        return getAdjustedDuration(Duration.BUTTON_PRESS)
    }
    
    /**
     * 获取滑动进入时长
     */
    fun getSlideInDuration(): Long {
        return getAdjustedDuration(Duration.SLIDE_IN)
    }
    
    /**
     * 获取滑动退出时长
     */
    fun getSlideOutDuration(): Long {
        return getAdjustedDuration(Duration.SLIDE_OUT)
    }
    
    /**
     * 获取弹跳进入时长
     */
    fun getBounceInDuration(): Long {
        return getAdjustedDuration(Duration.BOUNCE_IN)
    }
    
    /**
     * 获取列表项级联延迟
     */
    fun getListItemStaggerDelay(): Long {
        return getAdjustedDuration(Duration.LIST_ITEM_STAGGER)
    }
    
    /**
     * 检查是否应该跳过动画
     * （当用户启用减少动画模式时）
     */
    fun shouldSkipAnimation(): Boolean {
        return !animationsEnabled || animationScale == 0f
    }
    
    /**
     * 检查是否启用了减少动画
     */
    fun isReducedMotionEnabled(): Boolean {
        return !animationsEnabled
    }
    
    /**
     * 获取当前动画缩放比例
     */
    fun getAnimationScale(): Float {
        return animationScale
    }
    
    /**
     * 手动设置动画启用状态
     * （用于应用内设置覆盖系统设置）
     */
    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
    }
    
    /**
     * 手动设置动画速度倍率
     * （用于应用内设置）
     */
    fun setAnimationScale(scale: Float) {
        animationScale = scale.coerceIn(0f, 10f)
    }
    
    /**
     * 重置为系统默认设置
     */
    fun resetToSystemDefaults(context: Context) {
        initialize(context)
    }
}

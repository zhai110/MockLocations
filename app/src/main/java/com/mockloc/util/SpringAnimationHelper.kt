package com.mockloc.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 弹簧动画工具类
 * 
 * 使用Physics-based animation创建自然的物理效果
 * 比传统属性动画更流畅、更符合真实物理规律
 */
object SpringAnimationHelper {
    
    /**
     * 弹簧预设配置
     */
    object Presets {
        // 快速回弹（适合按钮反馈）
        val SNAPPY = SpringConfig(
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY,
            stiffness = SpringForce.STIFFNESS_HIGH
        )
        
        // 中等弹性（适合卡片展开）
        val MEDIUM = SpringConfig(
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY,
            stiffness = SpringForce.STIFFNESS_MEDIUM
        )
        
        // 缓慢回弹（适合强调效果）
        val BOUNCY = SpringConfig(
            dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY,
            stiffness = SpringForce.STIFFNESS_LOW
        )
        
        // 无弹性（适合精确控制）
        val NO_BOUNCE = SpringConfig(
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY,
            stiffness = SpringForce.STIFFNESS_MEDIUM
        )
    }
    
    /**
     * 弹簧配置数据类
     */
    data class SpringConfig(
        val dampingRatio: Float,
        val stiffness: Float
    )
    
    /**
     * 弹簧缩放动画（X和Y轴同时）
     * 
     * @param view 目标视图
     * @param finalScale 最终缩放比例（1.0 = 原始大小）
     * @param config 弹簧配置
     * @param onComplete 完成回调
     */
    fun springScale(
        view: View,
        finalScale: Float = 1f,
        config: SpringConfig = Presets.MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.scaleX = finalScale
            view.scaleY = finalScale
            onComplete?.invoke()
            return
        }
        
        val animX = SpringAnimation(view, DynamicAnimation.SCALE_X, finalScale)
        val animY = SpringAnimation(view, DynamicAnimation.SCALE_Y, finalScale)
        
        listOf(animX, animY).forEach { anim ->
            anim.spring.apply {
                dampingRatio = config.dampingRatio
                stiffness = config.stiffness
            }
            anim.addEndListener { _, _, _, _ ->
                onComplete?.invoke()
            }
        }
        
        animX.start()
        animY.start()
    }
    
    /**
     * 弹簧平移动画（垂直方向）
     * 
     * @param view 目标视图
     * @param finalY 最终Y位置（像素）
     * @param config 弹簧配置
     * @param onComplete 完成回调
     */
    fun springTranslateY(
        view: View,
        finalY: Float = 0f,
        config: SpringConfig = Presets.MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.translationY = finalY
            onComplete?.invoke()
            return
        }
        
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, finalY).apply {
            spring.apply {
                dampingRatio = config.dampingRatio
                stiffness = config.stiffness
            }
            addEndListener { _, _, _, _ ->
                onComplete?.invoke()
            }
            start()
        }
    }
    
    /**
     * 弹簧平移动画（水平方向）
     */
    fun springTranslateX(
        view: View,
        finalX: Float = 0f,
        config: SpringConfig = Presets.MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.translationX = finalX
            onComplete?.invoke()
            return
        }
        
        SpringAnimation(view, DynamicAnimation.TRANSLATION_X, finalX).apply {
            spring.apply {
                dampingRatio = config.dampingRatio
                stiffness = config.stiffness
            }
            addEndListener { _, _, _, _ ->
                onComplete?.invoke()
            }
            start()
        }
    }
    
    /**
     * 弹簧淡入动画
     * 结合透明度和轻微缩放，创造自然的出现效果
     */
    fun springFadeIn(
        view: View,
        config: SpringConfig = Presets.MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.alpha = 1f
            view.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }
        
        view.apply {
            alpha = 0f
            scaleX = 0.95f
            scaleY = 0.95f
            visibility = View.VISIBLE
        }
        
        // 透明度使用传统动画（Spring不支持alpha）
        view.animate()
            .alpha(1f)
            .setDuration(AnimationConfig.getFadeInDuration())
            .start()
        
        // 缩放使用弹簧动画
        springScale(view, 1f, config, onComplete)
    }
    
    /**
     * 按钮按压弹簧效果
     * 按下时缩小，释放时弹回
     */
    fun buttonPressEffect(view: View, pressed: Boolean) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.scaleX = if (pressed) 0.95f else 1f
            view.scaleY = if (pressed) 0.95f else 1f
            return
        }
        
        val targetScale = if (pressed) 0.95f else 1f
        springScale(view, targetScale, Presets.SNAPPY)
    }
    
    /**
     * FAB展开/收起弹簧动画
     */
    fun fabExpand(view: View, expanded: Boolean) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        
        // 先缩小再放大，创造"弹出"效果
        if (expanded) {
            view.apply {
                scaleX = 0f
                scaleY = 0f
            }
            springScale(view, 1f, Presets.BOUNCY)
        } else {
            springScale(view, 0f, Presets.SNAPPY) {
                view.visibility = View.GONE
            }
        }
    }
    
    /**
     * 列表项弹簧进入动画
     * 每个项目依次弹入
     */
    fun listItemSpringIn(
        view: View,
        position: Int,
        config: SpringConfig = Presets.MEDIUM,
        onComplete: (() -> Unit)? = null
    ) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.apply {
                alpha = 1f
                translationY = 0f
                visibility = View.VISIBLE
            }
            onComplete?.invoke()
            return
        }
        
        view.apply {
            alpha = 0f
            translationY = 30f
            visibility = View.VISIBLE
        }
        
        // 延迟执行，创造级联效果
        val delay = position * AnimationConfig.getListItemStaggerDelay()
        
        view.postDelayed({
            // 透明度动画
            view.animate()
                .alpha(1f)
                .setDuration(AnimationConfig.getFadeInDuration())
                .start()
            
            // Y轴弹簧动画
            springTranslateY(view, 0f, config, onComplete)
        }, delay)
    }
    
    /**
     * 取消视图上的所有弹簧动画
     */
    fun cancelAllSprings(view: View) {
        // SpringAnimation会自动管理，无需手动取消
        // 但可以通过设置最终值来立即停止
        view.clearAnimation()
    }
    
    /**
     * 创建自定义弹簧动画
     * 
     * @param view 目标视图
     * @param property 要动画的属性
     * @param finalValue 最终值
     * @param config 弹簧配置
     */
    fun createCustomSpring(
        view: View,
        property: DynamicAnimation.ViewProperty,
        finalValue: Float,
        config: SpringConfig = Presets.MEDIUM
    ): SpringAnimation {
        return SpringAnimation(view, property, finalValue).apply {
            spring.apply {
                dampingRatio = config.dampingRatio
                stiffness = config.stiffness
            }
        }
    }
}

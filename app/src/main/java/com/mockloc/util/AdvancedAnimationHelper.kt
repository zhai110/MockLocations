package com.mockloc.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 高级动画工具类
 * 提供复杂的组合动画效果（脉冲、弹跳、成功/失败反馈等）
 */
object AdvancedAnimationHelper {

    /**
     * 脉冲动画（用于摇杆激活、位置标记等）
     * 效果：视图会像心跳一样放大缩小，产生脉冲感
     */
    fun pulse(view: View, duration: Long = AnimationConfig.Duration.NORMAL, repeatCount: Int = 2) {
        // 如果应该跳过动画，不执行
        if (AnimationConfig.shouldSkipAnimation()) return
        
        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.15f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.15f, 1f)
        val alpha = PropertyValuesHolder.ofFloat("alpha", 1f, 0.7f, 1f)
        
        ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha).apply {
            this.duration = duration
            this.repeatCount = repeatCount
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 连续脉冲动画（无限循环，直到手动停止）
     */
    fun pulseInfinite(view: View, duration: Long = AnimationConfig.Duration.SLOW): ObjectAnimator {
        // 如果应该跳过动画，返回空animator
        if (AnimationConfig.shouldSkipAnimation()) {
            return ObjectAnimator.ofFloat(view, "alpha", 1f).apply { start() }
        }
        
        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f, 1f)
        
        return ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            this.duration = duration
            this.repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 弹跳进入动画（比XML版本更流畅）
     */
    fun bounceIn(view: View, duration: Long = AnimationConfig.getBounceInDuration(), onComplete: (() -> Unit)? = null) {
        // 如果应该跳过动画，直接显示
        if (AnimationConfig.shouldSkipAnimation()) {
            view.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }
        
        view.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
            visibility = View.VISIBLE
        }
        
        // 第一阶段：快速放大并 overshoot
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 0f, 1.15f),
            PropertyValuesHolder.ofFloat("scaleY", 0f, 1.15f),
            PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
        ).apply {
            this.duration = (duration * 0.6).toLong()
            interpolator = OvershootInterpolator(2f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 第二阶段：回弹到正常大小
                    ObjectAnimator.ofPropertyValuesHolder(
                        view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.15f, 1f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.15f, 1f)
                    ).apply {
                        this.duration = (duration * 0.4).toLong()
                        interpolator = AccelerateDecelerateInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                onComplete?.invoke()
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

    /**
     * 成功动画（勾选标记效果）
     * 效果：缩放 + 旋转 + 淡入
     */
    fun successAnimation(view: View, duration: Long = AnimationConfig.Duration.NORMAL, onComplete: (() -> Unit)? = null) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }
        view.apply {
            scaleX = 0f
            scaleY = 0f
            rotation = -180f
            alpha = 0f
            visibility = View.VISIBLE
        }
        
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 0f, 1.2f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 0f, 1.2f, 1f),
            PropertyValuesHolder.ofFloat("rotation", -180f, 0f),
            PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
        ).apply {
            this.duration = duration
            interpolator = OvershootInterpolator(1.5f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * 失败动画（抖动效果）
     * 效果：左右快速抖动
     */
    fun errorAnimation(view: View, duration: Long = AnimationConfig.Duration.FAST, onComplete: (() -> Unit)? = null) {
        if (AnimationConfig.shouldSkipAnimation()) {
            onComplete?.invoke()
            return
        }
        val shakeDistance = 10f
        val times = 4
        
        ObjectAnimator.ofFloat(view, "translationX", 0f, -shakeDistance, shakeDistance, 
            -shakeDistance, shakeDistance, 0f).apply {
            this.duration = duration
            this.repeatCount = times
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationX = 0f
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * 警告动画（闪烁效果）
     * 效果：快速闪烁几次
     */
    fun warningAnimation(view: View, duration: Long = AnimationConfig.Duration.SLOW, blinkCount: Int = 3) {
        if (AnimationConfig.shouldSkipAnimation()) return
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0.3f, 1f).apply {
            this.duration = duration / blinkCount
            this.repeatCount = blinkCount - 1
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 滑动进入动画（从指定方向）
     */
    fun slideIn(view: View, direction: SlideDirection = SlideDirection.BOTTOM, 
               duration: Long = AnimationConfig.getSlideInDuration(), onComplete: (() -> Unit)? = null) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }
        val distance = when (direction) {
            SlideDirection.TOP -> -view.height.toFloat()
            SlideDirection.BOTTOM -> view.height.toFloat()
            SlideDirection.LEFT -> -view.width.toFloat()
            SlideDirection.RIGHT -> view.width.toFloat()
        }
        
        view.apply {
            when (direction) {
                SlideDirection.TOP, SlideDirection.BOTTOM -> translationY = distance
                SlideDirection.LEFT, SlideDirection.RIGHT -> translationX = distance
            }
            alpha = 0f
            visibility = View.VISIBLE
        }
        
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            when (direction) {
                SlideDirection.TOP, SlideDirection.BOTTOM -> 
                    PropertyValuesHolder.ofFloat("translationY", distance, 0f)
                else -> 
                    PropertyValuesHolder.ofFloat("translationX", distance, 0f)
            },
            PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
        ).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * 滑动退出动画
     */
    fun slideOut(view: View, direction: SlideDirection = SlideDirection.BOTTOM,
                duration: Long = AnimationConfig.getSlideOutDuration(), onComplete: (() -> Unit)? = null) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.visibility = View.GONE
            onComplete?.invoke()
            return
        }
        val distance = when (direction) {
            SlideDirection.TOP -> -view.height.toFloat()
            SlideDirection.BOTTOM -> view.height.toFloat()
            SlideDirection.LEFT -> -view.width.toFloat()
            SlideDirection.RIGHT -> view.width.toFloat()
        }
        
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            when (direction) {
                SlideDirection.TOP, SlideDirection.BOTTOM -> 
                    PropertyValuesHolder.ofFloat("translationY", 0f, distance)
                else -> 
                    PropertyValuesHolder.ofFloat("translationX", 0f, distance)
            },
            PropertyValuesHolder.ofFloat("alpha", 1f, 0f)
        ).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * 旋转动画
     */
    fun rotate(view: View, fromDegrees: Float = 0f, toDegrees: Float = 360f,
              duration: Long = 1000, repeatCount: Int = 0) {
        ObjectAnimator.ofFloat(view, "rotation", fromDegrees, toDegrees).apply {
            this.duration = duration
            this.repeatCount = repeatCount
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 组合动画：缩放+淡入（用于卡片展开）
     */
    fun scaleFadeIn(view: View, duration: Long = AnimationConfig.getFadeInDuration(), onComplete: (() -> Unit)? = null) {
        if (AnimationConfig.shouldSkipAnimation()) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            onComplete?.invoke()
            return
        }
        view.apply {
            scaleX = 0.8f
            scaleY = 0.8f
            alpha = 0f
            visibility = View.VISIBLE
        }
        
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 0.8f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 0.8f, 1f),
            PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
        ).apply {
            this.duration = duration
            interpolator = OvershootInterpolator(1.2f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * 滑动方向枚举
     */
    enum class SlideDirection {
        TOP, BOTTOM, LEFT, RIGHT
    }
}

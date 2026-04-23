package com.mockloc.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import com.mockloc.R

/**
 * 动画工具类
 * 
 * 提供完整的视图动画解决方案：
 * - 基础动画：淡入淡出、滑动、按钮反馈
 * - 高级动画：脉冲、弹跳、成功/失败反馈、旋转等
 * - 安全动画：带状态检查的淡入淡出
 */
object AnimationHelper {

    /**
     * 淡入显示View（优化版，避免闪屏）
     */
    fun fadeIn(view: View, duration: Long = AnimationConfig.getFadeInDuration(), onComplete: (() -> Unit)? = null) {
        // 如果应该跳过动画，直接显示
        if (AnimationConfig.shouldSkipAnimation()) {
            view.alpha = 1f
            view.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }
        
        // 先设置为透明但不隐藏，避免闪屏
        if (view.alpha != 0f) {
            view.alpha = 0f
        }
        view.visibility = View.VISIBLE
        
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 确保最终状态为完全不透明
                    view.alpha = 1f
                    onComplete?.invoke()
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    view.alpha = 1f
                }
            })
            start()
        }
    }

    /**
     * 淡出隐藏View（优化版，避免闪屏）
     */
    fun fadeOut(view: View, duration: Long = AnimationConfig.getFadeOutDuration(), onComplete: (() -> Unit)? = null) {
        // 如果应该跳过动画，直接隐藏
        if (AnimationConfig.shouldSkipAnimation()) {
            view.visibility = View.GONE
            view.alpha = 0f
            onComplete?.invoke()
            return
        }
        
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.alpha = 0f  // 确保最终状态
                    onComplete?.invoke()
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    view.visibility = View.GONE
                    view.alpha = 0f
                }
            })
            start()
        }
    }

    /**
     * 使用XML动画淡入
     */
    fun fadeInWithXml(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.fade_in)
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * 使用XML动画淡出
     */
    fun fadeOutWithXml(view: View, onComplete: (() -> Unit)? = null) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.fade_out).apply {
            setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    view.visibility = View.GONE
                    onComplete?.invoke()
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        }
        view.startAnimation(animation)
    }

    /**
     * 弹跳进入动画（使用XML资源）
     * @deprecated 使用 bounceIn(view, duration, onComplete) 获得更好的控制
     */
    @Deprecated("Use bounceIn with callback for better control", ReplaceWith("bounceIn(view, AnimationConfig.getBounceInDuration(), null)"))
    fun bounceInXml(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.bounce_in)
        view.startAnimation(animation)
    }

    /**
     * 按钮按压效果
     */
    fun buttonPress(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.btn_press)
        view.startAnimation(animation)
    }

    /**
     * 按钮释放效果
     */
    fun buttonRelease(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.btn_release)
        view.startAnimation(animation)
    }

    /**
     * 为View添加点击动画反馈
     */
    fun addClickAnimation(view: View, onClick: (View) -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    buttonPress(v)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    buttonRelease(v)
                }
            }
            false
        }
        view.setOnClickListener(onClick)
    }

    /**
     * 滑动显示View（从底部）
     */
    fun slideUp(view: View, duration: Long = 300) {
        view.translationY = view.height.toFloat()
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, "translationY", view.height.toFloat(), 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 滑动隐藏View（向底部）
     */
    fun slideDown(view: View, duration: Long = 300, onComplete: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(view, "translationY", 0f, view.height.toFloat()).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.translationY = 0f
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * 数字滚动动画（用于坐标显示）- 优化版，避免闪烁
     */
    fun animateNumberChange(textView: android.widget.TextView, newValue: String, duration: Long = AnimationConfig.Duration.FAST) {
        // 如果应该跳过动画，直接设置文本
        if (AnimationConfig.shouldSkipAnimation()) {
            textView.text = newValue
            return
        }
        
        // 使用更平滑的透明度变化，避免明显闪烁
        val currentAlpha = textView.alpha
        
        ObjectAnimator.ofFloat(textView, "alpha", currentAlpha, 0.7f, currentAlpha).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    // 在透明度最低点更新文本，减少视觉跳跃
                }
                
                override fun onAnimationEnd(animation: Animator) {
                    textView.text = newValue
                    textView.alpha = 1f  // 确保最终完全不透明
                }
            })
            start()
        }
    }
    
    /**
     * 安全地淡入显示（如果视图已经在显示中，则不执行）
     */
    fun fadeInSafe(view: View, duration: Long = AnimationConfig.getFadeInDuration(), onComplete: (() -> Unit)? = null) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) {
            onComplete?.invoke()
            return
        }
        fadeIn(view, duration, onComplete)
    }
    
    /**
     * 安全地淡出隐藏（如果视图已经隐藏，则不执行）
     */
    fun fadeOutSafe(view: View, duration: Long = AnimationConfig.getFadeOutDuration(), onComplete: (() -> Unit)? = null) {
        if (view.visibility == View.GONE || view.alpha == 0f) {
            onComplete?.invoke()
            return
        }
        fadeOut(view, duration, onComplete)
    }
    
    // ==================== 高级动画（原 AdvancedAnimationHelper）====================
    
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
     * 滑动方向枚举
     */
    enum class SlideDirection {
        TOP, BOTTOM, LEFT, RIGHT
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
}

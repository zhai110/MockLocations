package com.mockloc.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import com.mockloc.R

/**
 * 动画工具类
 * 提供常用的视图动画和属性动画方法
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
     * 弹跳进入动画（用于标记点）
     */
    fun bounceIn(view: View) {
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
}

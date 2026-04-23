package com.mockloc.service

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import timber.log.Timber

/**
 * 拖动手势助手
 * 
 * 用于处理悬浮窗的拖动逻辑
 */
class DragHelper(private val view: View) {
    var windowManager: android.view.WindowManager? = null
    var windowParams: android.view.WindowManager.LayoutParams? = null
    
    private var downX = 0
    private var downY = 0
    private var lastX = 0
    private var lastY = 0
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    /**
     * 在 onInterceptTouchEvent 中调用
     * @return true 表示应拦截事件
     */
    fun onIntercept(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX.toInt()
                downY = ev.rawY.toInt()
                lastX = downX
                lastY = downY
                isDragging = false
                Timber.d("ACTION_DOWN at ($downX, $downY)")
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX.toInt() - downX
                val dy = ev.rawY.toInt() - downY
                if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    isDragging = true
                    Timber.d("Start dragging: dx=$dx, dy=$dy")
                }
            }
        }
        return isDragging
    }

    /**
     * 在 onTouchEvent 中调用
     * @return true 表示已消费事件
     */
    @SuppressLint("ClickableViewAccessibility")
    fun onTouch(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX.toInt()
                lastY = ev.rawY.toInt()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val nowX = ev.rawX.toInt()
                val nowY = ev.rawY.toInt()
                val dx = nowX - lastX
                val dy = nowY - lastY
                lastX = nowX
                lastY = nowY
                
                windowParams?.let { params ->
                    params.x += dx
                    params.y += dy
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to update view layout")
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    view.performClick()
                }
                isDragging = false
            }
        }
        return true
    }
}

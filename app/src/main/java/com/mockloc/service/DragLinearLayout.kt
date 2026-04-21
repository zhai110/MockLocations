package com.mockloc.service

import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout

/**
 * 可拖动的 LinearLayout
 * 
 * 支持手势拖动整个布局，同时可以排除特定区域（如摇杆）不触发拖动
 */
class DragLinearLayout(context: Context) : LinearLayout(context) {
    val dragHelper = DragHelper(this)
    
    /** 触摸在此 View 区域内时不拦截拖动（如摇杆/按钮区域） */
    var dragExcludeView: android.view.View? = null
    
    /** 当前手势是否起始于排除区域，整个手势期间都不拦截 */
    private var gestureInExclude = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                gestureInExclude = false
                // 如果触摸点在排除区域内，整个手势都不拦截
                dragExcludeView?.let { exclude ->
                    val location = IntArray(2)
                    exclude.getLocationOnScreen(location)
                    val x = ev.rawX.toInt()
                    val y = ev.rawY.toInt()
                    if (x >= location[0] && x <= location[0] + exclude.width &&
                        y >= location[1] && y <= location[1] + exclude.height) {
                        gestureInExclude = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureInExclude = false
            }
        }
        if (gestureInExclude) return false  // 整个手势期间都不拦截
        return dragHelper.onIntercept(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (gestureInExclude && ev.action == MotionEvent.ACTION_UP) {
            gestureInExclude = false
        }
        if (gestureInExclude) return false  // 不消费，交给子 View
        return dragHelper.onTouch(ev)
    }
}

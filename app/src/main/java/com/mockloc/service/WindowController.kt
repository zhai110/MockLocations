package com.mockloc.service

import android.view.View
import android.view.WindowManager
import timber.log.Timber

/**
 * 悬浮窗控制器接口
 * 
 * 用于将 FloatingWindowManager 中的窗口管理逻辑拆分为独立的控制器
 */
interface WindowController {
    
    /**
     * 初始化窗口视图
     */
    fun initialize()
    
    /**
     * 显示窗口
     */
    fun show()
    
    /**
     * 隐藏窗口
     */
    fun hide()
    
    /**
     * 销毁窗口，释放资源
     */
    fun destroy()
    
    /**
     * 获取窗口根视图
     */
    val rootView: View?
    
    /**
     * 窗口是否已初始化
     */
    val isInitialized: Boolean
    
    /**
     * 窗口是否可见
     */
    val isVisible: Boolean
    
    /**
     * 启用搜索框焦点（移除 FLAG_NOT_FOCUSABLE）
     * 
     * @param windowManager 窗口管理器
     * @param windowParams 窗口参数
     */
    fun enableSearchFocus(windowManager: WindowManager, windowParams: WindowManager.LayoutParams) {
        try {
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            rootView?.let { windowManager.updateViewLayout(it, windowParams) }
        } catch (e: Exception) {
            Timber.w(e, "enableSearchFocus failed")
        }
    }
    
    /**
     * 禁用搜索框焦点（恢复 FLAG_NOT_FOCUSABLE）
     * 
     * @param windowManager 窗口管理器
     * @param windowParams 窗口参数
     */
    fun disableSearchFocus(windowManager: WindowManager, windowParams: WindowManager.LayoutParams) {
        try {
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            rootView?.let { windowManager.updateViewLayout(it, windowParams) }
        } catch (e: Exception) {
            Timber.w(e, "disableSearchFocus failed")
        }
    }
}

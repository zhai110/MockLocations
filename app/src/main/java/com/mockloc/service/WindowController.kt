package com.mockloc.service

import android.view.View

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
}

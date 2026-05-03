package com.mockloc.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.mockloc.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

/**
 * 悬浮窗管理器（新架构精简版）
 *
 * 管理三个窗口控制器（同一时刻只显示一个）：
 * 1. JoystickWindowController — 主摇杆控制窗口
 * 2. MapWindowController — 地图选点窗口
 * 3. HistoryWindowController — 历史记录窗口
 *
 * 职责：
 * - WindowManager + LayoutParams 管理
 * - 窗口切换（show/hide）
 * - 主题同步
 */
class FloatingWindowManager(private val service: LocationService) {

    companion object {
        private const val WINDOW_TYPE_JOYSTICK = 0
        private const val WINDOW_TYPE_MAP = 1
        private const val WINDOW_TYPE_HISTORY = 2
    }

    /** 缓存当前是否为深色模式，主题切换时用于刷新视图 */
    private var isNightMode: Boolean = false

    /**
     * 带主题的 Context — 根据 isNightMode 创建 ContextThemeWrapper，
     * 确保从资源读取的颜色/主题正确匹配当前暗黑模式。
     */
    private var themedContext: Context = com.mockloc.util.ThemeUtils.createThemedContext(service).also { isNightMode = it.second }.first

    private val windowManager: WindowManager =
        service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager

    // 共享一个 LayoutParams，切换窗口时位置保持
    private val windowParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.RGBA_8888
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        gravity = Gravity.START or Gravity.TOP
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        x = 300
        y = 300
    }

    private var currentWindowType = WINDOW_TYPE_JOYSTICK

    /** 统一的协程作用域，用于管理异步任务，防止内存泄漏 */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 窗口控制器
    private var joystickController: JoystickWindowController? = null
    private var mapController: MapWindowController? = null
    private var historyController: HistoryWindowController? = null
    
    // 跟踪视图是否已显示
    private var isJoystickViewShown = false
    private var isMapViewShown = false
    private var isHistoryViewShown = false

    // 回调接口
    interface FloatingWindowListener {
        /** 摇杆方向回调 */
        fun onDirection(auto: Boolean, angle: Double, r: Double)
        /** 位置选择回调（WGS84坐标） */
        fun onPositionSelected(wgsLng: Double, wgsLat: Double, alt: Double)
        /** 速度变化回调 */
        fun onSpeedChanged(speedMs: Float)
    }

    private var listener: FloatingWindowListener? = null

    fun setListener(l: FloatingWindowListener) {
        listener = l
    }

    // ==================== 控制器初始化 ====================

    /**
     * 初始化所有窗口控制器
     */
    private fun initializeControllers() {
        if (joystickController != null) return  // 已经初始化
        
        try {
            // 初始化摇杆控制器
            joystickController = JoystickWindowController(
                context = themedContext,
                service = service,
                onDirectionChanged = { auto, angle, r ->
                    listener?.onDirection(auto, angle, r)
                },
                onSwitchToHistory = {
                    switchToHistory()
                },
                onSwitchToMap = {
                    switchToMap()
                }
            )
            joystickController?.initialize()
            
            // 初始化地图控制器
            mapController = MapWindowController(
                context = themedContext,
                service = service,
                windowManager = windowManager,
                windowParams = windowParams,
                onSwitchToJoystick = {
                    switchToJoystick()
                },
                onSwitchToHistory = {
                    switchToHistory()
                },
                onLocationSelected = { lat, lng ->
                    listener?.onPositionSelected(lng, lat, 0.0)
                }
            )
            mapController?.initialize()
            
            // 初始化历史记录控制器
            historyController = HistoryWindowController(
                context = themedContext,
                service = service,
                windowManager = windowManager,
                windowParams = windowParams,
                onSwitchToJoystick = {
                    switchToJoystick()
                },
                onSwitchToMap = {
                    switchToMap()
                },
                onHistorySelected = { location ->
                    // ✅ 修复：数据库中存储的是 GCJ-02，需要转换为 WGS-84
                    val wgs = com.mockloc.util.MapUtils.gcj02ToWgs84(location.longitude, location.latitude)
                    listener?.onPositionSelected(wgs[0], wgs[1], 0.0)
                }
            )
            historyController?.initialize()
            
            Timber.d("All window controllers initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize window controllers")
        }
    }

    /**
     * 切换到摇杆窗口
     */
    private fun switchToJoystick() {
        currentWindowType = WINDOW_TYPE_JOYSTICK
        hide()
        show()
    }

    /**
     * 切换到地图窗口
     */
    private fun switchToMap() {
        currentWindowType = WINDOW_TYPE_MAP
        hide()
        show()
    }

    /**
     * 切换到历史记录窗口
     */
    private fun switchToHistory() {
        currentWindowType = WINDOW_TYPE_HISTORY
        hide()
        show()
    }

    // ==================== 显示/隐藏 ====================

    /**
     * 显示当前窗口
     */
    fun show() {
        val controller = getCurrentController()
        
        if (controller == null) {
            initializeControllers()
            // 重新获取 controller
            return show()
        }
        
        showController(controller)
    }
    
    /**
     * 获取当前窗口类型的控制器
     */
    private fun getCurrentController(): WindowController? {
        return when (currentWindowType) {
            WINDOW_TYPE_JOYSTICK -> joystickController
            WINDOW_TYPE_MAP -> mapController
            WINDOW_TYPE_HISTORY -> historyController
            else -> null
        }
    }
    
    /**
     * 设置当前窗口的显示标志
     */
    private fun setShownFlag(value: Boolean) {
        when (currentWindowType) {
            WINDOW_TYPE_JOYSTICK -> isJoystickViewShown = value
            WINDOW_TYPE_MAP -> isMapViewShown = value
            WINDOW_TYPE_HISTORY -> isHistoryViewShown = value
        }
    }
    
    /**
     * 显示指定的控制器窗口（通用逻辑）
     */
    private fun showController(controller: WindowController) {
        controller.show()
        val view = controller.rootView
        if (view != null) {
            if (view.parent != null) {
                Timber.d("${getWindowTypeName()} view already shown")
                setShownFlag(true)
                return
            }
            
            // 先隐藏其他窗口（带淡出动画）
            val otherViews = getOtherVisibleViews()
            
            if (otherViews.isEmpty()) {
                // 没有其他窗口显示，直接淡入
                view.alpha = 0f
                addViewSafe(view)
                setupDragHelper(view)
                com.mockloc.util.AnimationHelper.fadeIn(view)
            } else {
                // 有其他窗口显示，先淡出再显示新窗口
                fadeOutMultipleViews(otherViews) {
                    showViewWithDrag(view)
                }
            }
            setShownFlag(true)
        }
    }
    
    /**
     * 获取窗口类型名称（用于日志）
     */
    private fun getWindowTypeName(): String {
        return when (currentWindowType) {
            WINDOW_TYPE_JOYSTICK -> "Joystick"
            WINDOW_TYPE_MAP -> "Map"
            WINDOW_TYPE_HISTORY -> "History"
            else -> "Unknown"
        }
    }
    
    /**
     * 获取其他正在显示的窗口视图列表
     */
    private fun getOtherVisibleViews(): List<View> {
        val views = mutableListOf<View>()
        
        when (currentWindowType) {
            WINDOW_TYPE_JOYSTICK -> {
                mapController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                historyController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
            }
            WINDOW_TYPE_MAP -> {
                joystickController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                historyController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
            }
            WINDOW_TYPE_HISTORY -> {
                joystickController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                mapController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
            }
        }
        
        return views
    }
    
    /**
     * 依次淡出多个视图
     */
    private fun fadeOutMultipleViews(views: List<View>, onComplete: () -> Unit) {
        if (views.isEmpty()) {
            onComplete()
            return
        }
        
        // 递归淡出所有视图
        fun fadeOutNext(index: Int) {
            if (index >= views.size) {
                onComplete()
                return
            }
            
            fadeOutAndRemove(views[index]) {
                fadeOutNext(index + 1)
            }
        }
        
        fadeOutNext(0)
    }

    /**
     * 显示视图并设置拖动
     */
    private fun showViewWithDrag(view: View) {
        view.alpha = 0f
        addViewSafe(view)
        setupDragHelper(view)
        com.mockloc.util.AnimationHelper.fadeIn(view)
    }

    /**
     * 设置拖动辅助（统一方法）
     */
    private fun setupDragHelper(view: View) {
        if (view is DragLinearLayout) {
            view.dragHelper.windowManager = windowManager
            view.dragHelper.windowParams = windowParams
        }
    }

    /**
     * 摇杆类型变化时切换显示（圆形摇杆 / 八方向按钮）
     */
    fun onJoystickTypeChanged() {
        // 更新新控制器
        joystickController?.onJoystickTypeChanged()
    }

    /**
     * 隐藏所有窗口
     */
    fun hide() {
        // 移除控制器的视图
        joystickController?.rootView?.let { removeViewSafeImmediate(it) }
        mapController?.rootView?.let { removeViewSafeImmediate(it) }
        historyController?.rootView?.let { removeViewSafeImmediate(it) }
        
        // 重置显示标志
        isJoystickViewShown = false
        isMapViewShown = false
        isHistoryViewShown = false
    }

    /**
     * 销毁所有窗口
     */
    fun destroy() {
        // 取消所有协程，防止内存泄漏
        scope.cancel()
        
        // 销毁所有控制器
        joystickController?.destroy()
        mapController?.destroy()
        historyController?.destroy()
        
        // 使用 immediate 版本立即移除所有窗口，防止泄漏
        joystickController?.rootView?.let { removeViewSafeImmediate(it) }
        mapController?.rootView?.let { removeViewSafeImmediate(it) }
        historyController?.rootView?.let { removeViewSafeImmediate(it) }
        
        // 彻底清理所有引用，防止内存泄漏
        joystickController = null
        mapController = null
        historyController = null
        listener = null  // 清理监听器引用
    }

    // ==================== 初始化 ====================

    /**
     * 初始化悬浮窗管理器
     */
    fun init() {
        Timber.d("FloatingWindowManager initialized")
    }

    /**
     * 同步悬浮窗与系统主题
     * - 深色模式：切换高德地图到夜景，销毁并重建悬浮窗 UI（深色配色）
     * 由 LocationService.themeReceiver 调用
     */
    fun syncMapWithSystemTheme() {
        val isNight = (service.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val themeChanged = isNightMode != isNight
        isNightMode = isNight

        // 主题真的变了 → 重建 themedContext 和视图
        if (themeChanged) {
            Timber.d("Theme changed (night=$isNight), recreating floating views...")
            
            // 使用显示标志判断悬浮窗是否真正在屏幕上显示
            // 修复：原代码用 currentWindowType != null，但 currentWindowType 是 Int 类型，
            // 永远不为 null，导致 wasShowing 永远为 true，夜间模式切换时错误地弹出悬浮窗
            val wasShowing = isJoystickViewShown || isMapViewShown || isHistoryViewShown
            
            // ✅ 关键修复：先清理旧的控制器和视图，避免内存泄漏
            // 1. 移除所有窗口视图
            joystickController?.rootView?.let { removeViewSafeImmediate(it) }
            mapController?.rootView?.let { removeViewSafeImmediate(it) }
            historyController?.rootView?.let { removeViewSafeImmediate(it) }

            // 2. 销毁所有控制器（释放内部资源）
            joystickController?.destroy()
            mapController?.destroy()
            historyController?.destroy()
            
            // 3. 清空控制器引用（帮助 GC 回收）
            joystickController = null
            mapController = null
            historyController = null
            
            // 4. ✅ 显式清理旧的 themedContext（断开引用链）
            // 注意：ContextThemeWrapper 本身很轻量，但为了最佳实践，显式置空
            themedContext = service  // 临时指向 service，避免悬空引用
            
            // 5. 创建新的 themedContext
            themedContext = com.mockloc.util.ThemeUtils.createThemedContext(service).also { isNightMode = it.second }.first
            val savedWindowType = currentWindowType

            // 重新初始化控制器
            initializeControllers()
            
            currentWindowType = savedWindowType
            
            // 只有之前真正在显示时才重新显示
            if (wasShowing) {
                show()
                Timber.d("Floating window restored after theme change")
            } else {
                Timber.d("Floating window was not showing, skip auto-show")
            }
        }
    }

    // ==================== 公共查询方法 ====================
    
    /**
     * 检查地图窗口是否正在显示
     */
    fun isMapWindowVisible(): Boolean = isMapViewShown
    
    /**
     * 检查历史记录窗口是否正在显示
     */
    fun isHistoryWindowVisible(): Boolean = isHistoryViewShown
    
    // ==================== 工具方法 ====================

    private fun addViewSafe(view: View?) {
        if (view == null) {
            Timber.w("addViewSafe: view is null!")
            return
        }
        if (view.parent == null) {
            try {
                windowManager.addView(view, windowParams)
                Timber.d("addViewSafe: added view=$view")
            } catch (e: Exception) {
                Timber.w(e, "addView failed")
            }
        } else {
            Timber.w("addViewSafe: view already has parent")
        }
    }

    private fun removeViewSafeImmediate(view: View?) {
        view ?: return
        if (view.parent != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: Exception) {
                Timber.w(e, "removeViewImmediate failed")
            }
        }
    }

    /**
     * 淡出并移除视图（解决窗口切换不协调问题）
     */
    private fun fadeOutAndRemove(view: View?, onComplete: (() -> Unit)? = null) {
        if (view == null || view.parent == null) {
            // 视图不存在或已移除，直接回调
            onComplete?.invoke()
            return
        }
        
        // 使用 AnimationHelper 的 fadeOut，它会自动处理无障碍设置
        com.mockloc.util.AnimationHelper.fadeOut(view) {
            // 淡出完成后移除视图
            removeViewSafeImmediate(view)
            onComplete?.invoke()
        }
    }
}

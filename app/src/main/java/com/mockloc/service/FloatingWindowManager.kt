package com.mockloc.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.mockloc.R
import com.mockloc.data.db.AppDatabase
import com.mockloc.data.repository.LocationRepository
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
        private const val WINDOW_TYPE_ROUTE_CONTROL = 3  // ✅ 路线控制悬浮窗
    }

    /** 缓存当前是否为深色模式，主题切换时用于刷新视图 */
    private var isNightMode: Boolean = false

    /** ✅ 防止快速连续切换主题导致的状态混乱 */
    private var isSyncingTheme = false

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
    private var routeControlController: RouteControlWindowController? = null  // ✅ 路线控制控制器
    
    // 跟踪视图是否已显示
    private var isJoystickViewShown = false
    private var isMapViewShown = false
    private var isHistoryViewShown = false
    private var isRouteControlViewShown = false  // ✅ 路线控制视图显示状态

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

    /**
     * 设置监听器
     * @param l 监听器，传入 null 可清除监听器引用防止内存泄漏
     */
    fun setListener(l: FloatingWindowListener?) {
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
            
            // ✅ 检查初始化是否成功
            if (joystickController?.rootView == null) {
                Timber.e("❌ JoystickController initialization failed")
                throw IllegalStateException("JoystickController rootView is null after initialization")
            }
            
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
                    // ✅ 修复：onPositionSelected 接口定义为 (wgsLng, wgsLat)，需要交换
                    listener?.onPositionSelected(lng, lat, 0.0)
                }
            )
            mapController?.initialize()
            
            // ✅ 检查初始化是否成功
            if (mapController?.rootView == null) {
                Timber.e("❌ MapWindowController initialization failed")
                throw IllegalStateException("MapWindowController rootView is null after initialization")
            }
            
            // 初始化历史记录控制器
            val locationRepository = LocationRepository(
                com.mockloc.VirtualLocationApp.getDatabase().historyLocationDao(),
                com.mockloc.VirtualLocationApp.getDatabase().favoriteLocationDao()
            )
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
                },
                locationRepository = locationRepository  // ✅ Phase 1: 注入 Repository
            )
            historyController?.initialize()
            
            // ✅ 检查初始化是否成功
            if (historyController?.rootView == null) {
                Timber.e("❌ HistoryWindowController initialization failed")
                throw IllegalStateException("HistoryWindowController rootView is null after initialization")
            }
            
            Timber.d("All window controllers initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize window controllers")
            // ✅ 清空所有控制器引用，确保下次 show() 能检测到失败
            joystickController = null
            mapController = null
            historyController = null
            routeControlController = null
            throw e  // ✅ 重新抛出异常，让调用方知道初始化失败
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

    // ✅ 递归保护：防止 show() 无限递归
    private var isShowingInProgress = false
    
    // ==================== 显示/隐藏 ====================

    /**
     * 显示当前窗口
     */
    fun show() {
        // ✅ 防止无限递归
        if (isShowingInProgress) {
            Timber.w("show() already in progress, skipping to prevent infinite recursion")
            return
        }
        
        isShowingInProgress = true
        try {
            val controller = getCurrentController()
            
            if (controller == null) {
                Timber.d("Controller not initialized, initializing...")
                initializeControllers()
                
                // ✅ 检查初始化是否成功
                val newController = getCurrentController()
                if (newController == null) {
                    Timber.e("❌ Failed to initialize controllers after retry")
                    return
                }
                
                showController(newController)
            } else {
                showController(controller)
            }
        } finally {
            isShowingInProgress = false
        }
    }
    
    /**
     * 获取当前窗口类型的控制器
     */
    private fun getCurrentController(): WindowController? {
        return when (currentWindowType) {
            WINDOW_TYPE_JOYSTICK -> joystickController
            WINDOW_TYPE_MAP -> mapController
            WINDOW_TYPE_HISTORY -> historyController
            WINDOW_TYPE_ROUTE_CONTROL -> routeControlController  // ✅ 添加路线控制窗口
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
            WINDOW_TYPE_ROUTE_CONTROL -> isRouteControlViewShown = value  // ✅ 添加路线控制
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
            WINDOW_TYPE_ROUTE_CONTROL -> "RouteControl"  // ✅ 添加路线控制
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
                routeControlController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }  // ✅ 添加路线控制窗口
            }
            WINDOW_TYPE_MAP -> {
                joystickController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                historyController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                routeControlController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }  // ✅ 添加路线控制窗口
            }
            WINDOW_TYPE_HISTORY -> {
                joystickController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                mapController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                routeControlController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }  // ✅ 添加路线控制窗口
            }
            WINDOW_TYPE_ROUTE_CONTROL -> {  // ✅ 新增：路线控制模式
                joystickController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                mapController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
                historyController?.rootView?.takeIf { it.parent != null }?.let { views.add(it) }
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
        routeControlController?.rootView?.let { removeViewSafeImmediate(it) }  // ✅ 隐藏路线控制窗口
        
        // 重置显示标志
        isJoystickViewShown = false
        isMapViewShown = false
        isHistoryViewShown = false
        isRouteControlViewShown = false  // ✅ 重置路线控制显示状态
    }
    
    /**
     * ✅ 显示路线控制悬浮窗
     */
    fun showRouteControl() {
        if (isRouteControlViewShown) {
            Timber.d("Route control window already shown")
            return
        }
        
        // 初始化控制器（如果尚未初始化）
        if (routeControlController == null) {
            routeControlController = RouteControlWindowController(themedContext, service, windowManager, windowParams)
        }
        
        val controller = routeControlController!!
        
        // ✅ 调用控制器的 initialize()
        controller.initialize()
        
        val view = controller.rootView
        if (view != null) {
            if (view.parent != null) {
                Timber.d("Route control view already shown")
                isRouteControlViewShown = true
                currentWindowType = WINDOW_TYPE_ROUTE_CONTROL
                return
            }
            
            // ✅ 先隐藏其他窗口（带淡出动画）
            val otherViews = getOtherVisibleViews()
            
            if (otherViews.isEmpty()) {
                // 没有其他窗口显示，直接淡入
                view.alpha = 0f
                addViewSafe(view)
                com.mockloc.util.AnimationHelper.fadeIn(view)
            } else {
                // 有其他窗口显示，先淡出再显示新窗口
                fadeOutMultipleViews(otherViews) {
                    showViewWithDrag(view)
                }
            }
            
            isRouteControlViewShown = true
            currentWindowType = WINDOW_TYPE_ROUTE_CONTROL
            Timber.d("Route control window shown successfully")
        } else {
            Timber.e("Route control rootView is null after initialization")
        }
    }
    
    /**
     * ✅ 隐藏路线控制悬浮窗
     */
    fun hideRouteControl() {
        if (!isRouteControlViewShown) {
            return
        }
        
        val view = routeControlController?.rootView
        if (view != null && view.parent != null) {
            // ✅ 淡出动画后移除视图
            com.mockloc.util.AnimationHelper.fadeOut(view) {
                try {
                    windowManager.removeView(view)
                    Timber.d("Route control window removed")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to remove route control window")
                }
            }
        }
        
        isRouteControlViewShown = false
        Timber.d("Route control window hidden")
    }

    /**
     * 销毁所有窗口
     */
    fun destroy() {
        Timber.d("FloatingWindowManager.destroy() called")
        
        // 1. 取消所有协程，防止内存泄漏
        scope.cancel()
        
        // 2. 销毁所有控制器
        joystickController?.destroy()
        mapController?.destroy()
        historyController?.destroy()
        
        // 3. 使用 immediate 版本立即移除所有窗口，防止泄漏
        joystickController?.rootView?.let { removeViewSafeImmediate(it) }
        mapController?.rootView?.let { removeViewSafeImmediate(it) }
        historyController?.rootView?.let { removeViewSafeImmediate(it) }
        
        // 4. 重置所有显示标志（防止状态不一致）
        isJoystickViewShown = false
        isMapViewShown = false
        isHistoryViewShown = false
        currentWindowType = WINDOW_TYPE_JOYSTICK
        
        // 5. 彻底清理所有引用，防止内存泄漏
        joystickController = null
        mapController = null
        historyController = null
        listener = null
        
        Timber.d("FloatingWindowManager destroyed successfully")
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
     * 由 LocationService.onConfigurationChanged 调用
     */
    fun syncMapWithSystemTheme() {
        // ✅ 防抖：如果正在同步主题，直接忽略后续请求
        if (isSyncingTheme) {
            Timber.w("Theme sync already in progress, skipping...")
            return
        }
        
        val isNight = (service.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val themeChanged = isNightMode != isNight
        isNightMode = isNight

        // 主题真的变了 → 更新主题
        if (themeChanged) {
            isSyncingTheme = true
            Timber.d("Theme changed (night=$isNight), updating floating views...")
            
            try {
                // 使用显示标志判断悬浮窗是否真正在屏幕上显示
                val wasShowing = isJoystickViewShown || isMapViewShown || isHistoryViewShown || isRouteControlViewShown  // ✅ 添加路线控制窗口
                
                if (wasShowing) {
                    // ✅ 悬浮窗显示时：完全重建（销毁旧控制器 + 创建新控制器）
                    Timber.d("Floating window is showing, full rebuild required")
                    rebuildControllers()
                } else {
                    // ✅ 悬浮窗未显示时：只更新 themedContext，不销毁控制器
                    Timber.d("Floating window not showing, only update themedContext")
                    themedContext = com.mockloc.util.ThemeUtils.createThemedContext(service).also { isNightMode = it.second }.first
                    
                    // ✅ 如果路线控制窗口已初始化，也需要更新主题
                    routeControlController?.updateTheme(isNight)
                    
                    // 如果控制器已初始化，更新它们的 themedContext（通过重新初始化）
                    if (joystickController != null || mapController != null || historyController != null) {
                        Timber.d("Controllers exist but not showing, will recreate on next show()")
                        // 不清除控制器引用，下次 show() 时会检测到主题变化并重建
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync theme")
                // ✅ 即使失败也要重置标志，允许下次重试
                isSyncingTheme = false
                throw e
            }
            
            // ✅ 成功完成后重置标志
            isSyncingTheme = false
        }
    }
    
    /**
     * 完全重建所有控制器（仅在悬浮窗显示时调用）
     */
    private fun rebuildControllers() {
        val savedWindowType = currentWindowType
        val wasRouteControlShown = isRouteControlViewShown  // ✅ 保存路线控制窗口状态
        
        // ✅ 关键修复：先重置显示标志，防止 show() 重复添加视图
        isJoystickViewShown = false
        isMapViewShown = false
        isHistoryViewShown = false
        isRouteControlViewShown = false  // ✅ 添加路线控制窗口
        
        // 1. 移除所有窗口视图
        joystickController?.rootView?.let { removeViewSafeImmediate(it) }
        mapController?.rootView?.let { removeViewSafeImmediate(it) }
        historyController?.rootView?.let { removeViewSafeImmediate(it) }
        routeControlController?.rootView?.let { removeViewSafeImmediate(it) }  // ✅ 添加路线控制窗口

        // 2. 销毁所有控制器（释放内部资源）
        joystickController?.destroy()
        mapController?.destroy()
        historyController?.destroy()
        routeControlController?.destroy()  // ✅ 添加路线控制窗口
        
        // 3. 清空控制器引用（帮助 GC 回收）
        joystickController = null
        mapController = null
        historyController = null
        routeControlController = null  // ✅ 添加路线控制窗口
        
        // 4. 创建新的 themedContext（使用新主题）
        themedContext = com.mockloc.util.ThemeUtils.createThemedContext(service).also { isNightMode = it.second }.first

        // 5. 重新初始化控制器
        try {
            initializeControllers()
        } catch (e: Exception) {
            Timber.e(e, "Failed to reinitialize controllers after theme change")
            // ✅ 即使初始化失败，也要恢复窗口类型标志
            currentWindowType = savedWindowType
            isSyncingTheme = false
            return  // 放弃恢复，避免崩溃
        }
        
        // ✅ 6. 如果之前路线控制窗口是显示的，重新创建它
        if (wasRouteControlShown) {
            Timber.d("Recreating route control controller after theme change")
            try {
                routeControlController = RouteControlWindowController(themedContext, service, windowManager, windowParams)
                routeControlController?.initialize()
                
                // ✅ 检查初始化是否成功
                if (routeControlController?.rootView == null) {
                    Timber.e("❌ RouteControlWindowController initialization failed after theme change")
                    routeControlController = null
                    isRouteControlViewShown = false
                    currentWindowType = savedWindowType
                    isSyncingTheme = false
                    return
                }
                
                // ✅ 使用标准流程显示，而不是手动 addView
                val view = routeControlController?.rootView
                if (view != null && view.parent == null) {
                    view.alpha = 0f
                    addViewSafe(view)
                    com.mockloc.util.AnimationHelper.fadeIn(view)
                    isRouteControlViewShown = true
                    currentWindowType = WINDOW_TYPE_ROUTE_CONTROL  // ✅ 恢复窗口类型
                    Timber.d("Route control window restored after theme change")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore route control window")
                routeControlController = null
                isRouteControlViewShown = false
                currentWindowType = savedWindowType
            }
        } else {
            // ✅ 7. 如果不是路线控制窗口，才调用 show() 恢复其他窗口
            currentWindowType = savedWindowType
            try {
                show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to show window after theme change")
                // ✅ 即使失败也要重置标志
                isSyncingTheme = false
            }
        }
        
        Timber.d("Floating window restored after theme change")
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

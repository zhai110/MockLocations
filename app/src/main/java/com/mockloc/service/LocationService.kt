package com.mockloc.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Criteria
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mockloc.R
import com.mockloc.ui.main.MainActivity
import com.mockloc.util.MapUtils
import com.mockloc.util.PrefsConfig
import timber.log.Timber
import com.mockloc.VirtualLocationApp
import com.mockloc.service.RoutePoint
import com.mockloc.service.RoutePlaybackState
import com.mockloc.repository.PoiSearchHelper

/**
 * 虚拟定位服务（前台服务）
 * 
 * 核心职责：
 * 1. 注册 TestProvider (GPS + Network) 并持续注入模拟位置
 * 2. 坐标系转换：高德地图使用 GCJ-02，Mock Location 注入 WGS-84
 * 3. 支持摇杆控制移动（方向+速度）— 委托给 MovementController
 * 4. 前台服务 + 通知栏操作（显示/隐藏摇杆）
 * 5. 服务被系统回收后自动重启
 * 
 * 参考项目架构：摇杆直接在 ServiceGo（前台服务）中创建，不需要单独的悬浮窗服务。
 * 悬浮窗管理委托给 FloatingWindowManager（参考项目 JoyStick.java 的职责拆分）。
 */
class LocationService : Service() {

    // ✅ 新增：服务专属的协程作用域，确保服务停止时所有任务自动取消
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var moveJob: Job? = null

    // ==================== 对外暴露的 StateFlow（供 ServiceConnector 订阅）====================

    /**
     * 模拟状态 StateFlow
     * 替代 @Volatile staticIsRunning 的静态标志，提供响应式观察
     */
    private val _simulationState = MutableStateFlow(SimulationState())
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    /**
     * 路线播放状态 StateFlow
     * 透传 RoutePlaybackEngine 的状态，供 ServiceConnector flatMapLatest 订阅
     */
    val routePlaybackState: StateFlow<RoutePlaybackState>
        get() = routePlaybackEngine?.state ?: MutableStateFlow(RoutePlaybackState()).asStateFlow()

    /**
     * 模拟状态数据类
     */
    data class SimulationState(
        val isSimulating: Boolean = false,
        val isAutoMoving: Boolean = false,
        val speedMode: String = "walk",
        val currentSpeed: Float = 1.4f
    )

    companion object {
        const val ACTION_START = "com.mockloc.action.START"
        const val ACTION_STOP = "com.mockloc.action.STOP"
        const val ACTION_UPDATE = "com.mockloc.action.UPDATE"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_COORD_GCJ02 = "coord_gcj02"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_service"
        // 默认位置更新间隔（毫秒），可通过设置调整
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 100L
        private const val HANDLER_MSG_ID = 0

        private const val NOTE_ACTION_SHOW = "com.mockloc.action.SHOW_JOYSTICK"
        private const val NOTE_ACTION_HIDE = "com.mockloc.action.HIDE_JOYSTICK"

        /** 静态标志，供外部查询服务是否正在运行 */
        @Volatile
        private var staticIsRunning = false

        /** 外部查询服务是否正在模拟 */
        fun isSimulating(): Boolean = staticIsRunning
    }

    private lateinit var locationManager: LocationManager

    @Volatile private var isRunning = false

    /** 位置注入器（Phase 3: 从 LocationService 中提取） */
    private lateinit var positionInjector: PositionInjector

    /** 摇杆移动控制器（Phase 3: 从 LocationService 中提取） */
    private lateinit var movementController: MovementController

    // 位置更新间隔（可从设置中读取）
    private var locationUpdateInterval = DEFAULT_LOCATION_UPDATE_INTERVAL_MS

    private val binder = LocalBinder()
    private lateinit var noteActionReceiver: NoteActionReceiver
    private lateinit var prefs: SharedPreferences
    
    // ✅ 修复：PoiSearchHelper 单例化，避免频繁创建导致内存泄漏
    private var poiSearchHelper: PoiSearchHelper? = null

    // SP 变更监听器：设置页修改后即时生效
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsConfig.Settings.KEY_ALTITUDE -> {
                positionInjector.updateAltitude(prefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 55.0f).toDouble())
                Timber.d("Altitude updated from settings: ${positionInjector.altitude}")
            }
            PrefsConfig.Settings.KEY_WALK_SPEED, PrefsConfig.Settings.KEY_RUN_SPEED, PrefsConfig.Settings.KEY_BIKE_SPEED -> {
                movementController.applySpeedMode(movementController.getCurrentSpeedMode())
            }
            PrefsConfig.Settings.KEY_JOYSTICK_TYPE -> {
                // 摇杆类型变化，通知悬浮窗切换
                floatingWindowManager?.onJoystickTypeChanged()
            }
            PrefsConfig.Settings.KEY_LOGGING -> {
                val enabled = prefs.getBoolean(PrefsConfig.Settings.KEY_LOGGING, true)
                updateLoggingTree(enabled)
                Timber.d("Logging updated from settings: $enabled")
            }
            PrefsConfig.Settings.KEY_LOCATION_UPDATE_INTERVAL -> {
                // 位置更新间隔变化（协程循环中会自动读取最新值）
                Timber.d("Location update interval setting changed")
            }
            PrefsConfig.Settings.KEY_HISTORY_EXPIRY -> {
                // 历史有效期变化，立即清理过期记录
                cleanupExpiredHistory()
            }
        }
    }

    // ==================== 悬浮窗管理 ====================
    private var floatingWindowManager: FloatingWindowManager? = null
    private var isJoystickVisible = false

    // ==================== 路线播放引擎 ====================
    private var routePlaybackEngine: RoutePlaybackEngine? = null
    
    /**
     * ✅ 公开访问路线播放引擎（用于悬浮窗状态监听）
     */
    val playbackEngine: RoutePlaybackEngine?
        get() = routePlaybackEngine
    
    /**
     * ✅ 路线控制悬浮窗状态监听器
     */
    private var routeControlStateListener: ((Boolean) -> Unit)? = null
    
    /**
     * ✅ 设置路线控制状态监听器
     */
    fun setRouteControlStateListener(listener: ((Boolean) -> Unit)?) {
        routeControlStateListener = listener
    }
    
    /**
     * ✅ 通知路线控制悬浮窗状态变化
     */
    private fun notifyRouteControlStateChanged(isPlaying: Boolean) {
        routeControlStateListener?.invoke(isPlaying)
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    // ==================== 路线播放控制方法（供 MainViewModel 调用）====================
    
    fun setRoute(points: List<RoutePoint>) {
        routePlaybackEngine?.setRoute(points)
    }
    
    fun addRoutePoint(point: RoutePoint) {
        routePlaybackEngine?.addPoint(point)
    }
    
    fun removeLastRoutePoint(): RoutePoint? {
        return routePlaybackEngine?.removeLastPoint()
    }
    
    fun removeRoutePointAt(index: Int): RoutePoint? {
        return routePlaybackEngine?.removePointAt(index)
    }
    
    fun insertRoutePointAt(index: Int, point: RoutePoint) {
        routePlaybackEngine?.insertPointAt(index, point)
    }
    
    fun clearRoute() {
        routePlaybackEngine?.clearRoute()
    }
    
    fun playRoute() {
        routePlaybackEngine?.play()
        // ✅ 通知悬浮窗状态变化
        notifyRouteControlStateChanged(true)
    }
    
    fun pauseRoute() {
        routePlaybackEngine?.pause()
        // ✅ 通知悬浮窗状态变化
        notifyRouteControlStateChanged(false)
    }
    
    fun stopRoute() {
        routePlaybackEngine?.stop()
    }
    
    fun setRouteLooping(loop: Boolean) {
        routePlaybackEngine?.setLooping(loop)
    }
    
    fun setRouteSpeedMultiplier(multiplier: Float) {
        routePlaybackEngine?.setSpeedMultiplier(multiplier)
    }
    
    fun getRoutePoints(): List<RoutePoint> {
        return routePlaybackEngine?.getPoints() ?: emptyList()
    }
    
    fun getRoutePlaybackState(): RoutePlaybackState {
        return routePlaybackEngine?.state?.value ?: RoutePlaybackState()
    }
    
    /**
     * ✅ 隐藏悬浮窗（用于路线模式下不需要摇杆控制）
     */
    fun hideFloatingWindow() {
        if (isJoystickVisible) {
            floatingWindowManager?.hide()
            isJoystickVisible = false
            Timber.d("Floating window hidden (route mode)")
        }
    }
    
    /**
     * ✅ 显示摇杆悬浮窗（单点模式）
     */
    fun showFloatingWindow() {
        // ✅ 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Timber.w("Cannot show floating window: no overlay permission")
            return
        }
        
        // ✅ 只在非路线播放模式下显示摇杆
        val isRoutePlaying = routePlaybackEngine?.state?.value?.isPlaying == true
        if (!isRoutePlaying && !isJoystickVisible) {
            floatingWindowManager?.show()
            isJoystickVisible = true
            Timber.d("Joystick window shown (single-point mode)")
        }
    }
    
    /**
     * ✅ 显示路线控制悬浮窗
     */
    fun showRouteControlWindow() {
        // ✅ 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Timber.w("Cannot show route control window: no overlay permission")
            return
        }
        floatingWindowManager?.showRouteControl()
        Timber.d("Route control window requested")
    }
    
    /**
     * ✅ 隐藏路线控制悬浮窗
     */
    fun hideRouteControlWindow() {
        floatingWindowManager?.hideRouteControl()
        Timber.d("Route control window hide requested")
    }
    
    /**
     * ✅ 停止路线播放并重置状态
     */
    fun stopRoutePlayback() {
        routePlaybackEngine?.stop()
        Timber.d("Route playback stopped")
    }

    // ==================== 生命周期 ====================

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)

        // 初始化 LocationManager 和 PositionInjector（必须在 altitude 设置之前）
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        positionInjector = PositionInjector(locationManager)
        positionInjector.updateAltitude(prefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 55.0f).toDouble())

        // 初始化摇杆移动控制器（必须在 applySpeedMode 之前）
        movementController = MovementController(positionInjector, prefs)
        movementController.applySpeedMode(prefs.getString(PrefsConfig.Settings.KEY_SPEED_MODE, "walk") ?: "walk")

        // ✅ 初始化 PoiSearchHelper 单例
        poiSearchHelper = PoiSearchHelper(applicationContext)

        // ✅ 应用保存的速度模式（防止服务重启后速度重置为默认值）
        // 已在 movementController 初始化时应用

        // 读取位置更新间隔设置（毫秒）
        locationUpdateInterval = prefs.getLong(PrefsConfig.Settings.KEY_LOCATION_UPDATE_INTERVAL, DEFAULT_LOCATION_UPDATE_INTERVAL_MS)
        Timber.d("Location update interval: ${locationUpdateInterval}ms")

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // 主题切换已改用 onConfigurationChanged 回调，不再需要广播

        // 初始化日志开关
        initLoggingFromPrefs()
        // 启动时清理过期历史记录
        cleanupExpiredHistory()

        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Timber.e(e, "startForeground failed")
        }

        try {
            positionInjector.registerTestProviders()
            initLocationUpdateLoop()
            registerNoteActionReceiver()
            Timber.d("LocationService created, TestProviders registered")
        } catch (e: Exception) {
            Timber.e(e, "LocationService onCreate initialization failed")
        }

        // 初始化悬浮窗管理器
        floatingWindowManager = FloatingWindowManager(this)
        floatingWindowManager?.setListener(object : FloatingWindowManager.FloatingWindowListener {
            override fun onDirection(auto: Boolean, angle: Double, r: Double) {
                movementController.processDirection(auto, angle, r)
            }
            override fun onPositionSelected(wgsLng: Double, wgsLat: Double, alt: Double) {
                setPositionWgs84(wgsLat, wgsLng, alt)
            }
            override fun onSpeedChanged(speedMs: Float) {
                movementController.setSpeed(speedMs)
            }
        })

        // 参考项目 ServiceGo.onCreate(): initJoyStick() → mJoyStick.show()
        // 注意：不应在 onCreate 中自动显示悬浮窗，只初始化，在 ACTION_START 时显示
        try {
            if (Settings.canDrawOverlays(this)) {
                floatingWindowManager?.init()
                Timber.d("FloatingWindow initialized (not shown yet)")
            } else {
                Timber.w("No overlay permission, floating window not shown")
            }
        } catch (e: Exception) {
            Timber.e(e, "FloatingWindowManager init failed")
        }

        // ✅ 初始化路线播放引擎（运行在前台服务中，支持后台位置更新）
        routePlaybackEngine = RoutePlaybackEngine(serviceScope) { latLng, bearing ->
            updatePlaybackPosition(latLng.longitude, latLng.latitude, positionInjector.altitude, bearing)
        }
        
        // ✅ 监听路线播放状态变化，通知悬浮窗
        serviceScope.launch {
            routePlaybackEngine?.state?.collect { state ->
                // 当 isPlaying 状态变化时，通知悬浮窗
                notifyRouteControlStateChanged(state.isPlaying)
            }
        }
        
        Timber.d("RoutePlaybackEngine initialized in LocationService")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                positionInjector.updateAltitude(intent.getDoubleExtra(EXTRA_ALTITUDE, getAltitudeFromPrefs()))
                val isGcj02 = intent.getBooleanExtra(EXTRA_COORD_GCJ02, true)

                if (isGcj02 && latitude != 0.0 && longitude != 0.0) {
                    val wgs = MapUtils.gcj02ToWgs84(longitude, latitude)
                    startSimulation(wgs[1], wgs[0])
                } else {
                    startSimulation(latitude, longitude)
                }

                // ✅ 不在这里显示摇杆悬浮窗，由 MainViewModel 的前后台监听控制
                // 摇杆仅在 App 进入后台时显示（如果正在模拟）
            }
            ACTION_STOP -> {
                stopSimulation()
            }
            ACTION_UPDATE -> {
                val (curLat, curLng) = positionInjector.getCurrentPosition()
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, curLat)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, curLng)
                val isGcj02 = intent.getBooleanExtra(EXTRA_COORD_GCJ02, true)

                Timber.d("📍 Received UPDATE_POSITION: lat=$latitude, lng=$longitude, isGcj02=$isGcj02")

                if (isGcj02) {
                    val wgs = MapUtils.gcj02ToWgs84(longitude, latitude)
                    updateTargetLocation(wgs[1], wgs[0])
                    Timber.d("🔄 Converted to WGS84: lat=${wgs[1]}, lng=${wgs[0]}")
                } else {
                    updateTargetLocation(latitude, longitude)
                }
                
                try {
                    positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                    Timber.d("✅ Manual location injection triggered for UPDATE_POSITION")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to inject location manually")
                }
            }
        }
        
        // ✅ 使用 START_NOT_STICKY：用户手动停止后不自动重启
        // 系统因内存不足杀死服务后也不会重启（这是期望的行为）
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 用户从最近任务中滑动删除时调用
     * 
     * 行为策略：
     * - 如果正在模拟定位：停止模拟，关闭悬浮窗，停止服务
     * - 如果未模拟：直接停止服务
     * 
     * 这样用户可以真正退出应用，而不是被强制重启
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.d("onTaskRemoved: user swiped away app from recent tasks")
        
        try {
            // 1. 停止模拟定位
            if (isRunning) {
                Timber.d("Stopping simulation due to task removal")
                stopSimulation()
            }
            
            // 2. 隐藏并销毁悬浮窗
            if (isJoystickVisible) {
                Timber.d("Hiding floating window due to task removal")
                floatingWindowManager?.hide()
                isJoystickVisible = false
            }
            
            // 3. 停止前台服务
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            // 4. 停止服务自身
            stopSelf()
            
            Timber.d("Service stopped cleanly on task removal")
        } catch (e: Exception) {
            Timber.e(e, "onTaskRemoved cleanup failed")
        }
        
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // ✅ 关键修复：无论悬浮窗是否可见，都要同步主题状态
        // 否则当悬浮窗未打开时切换主题，下次打开悬浮窗会使用旧的 Context
        Timber.d("Configuration changed, syncing floating window theme")
        floatingWindowManager?.syncMapWithSystemTheme()
    }

    override fun onDestroy() {
        Timber.d("LocationService onDestroy started")
        
        // 1. 标记服务停止
        isRunning = false
        staticIsRunning = false
        _simulationState.update { SimulationState() } // 重置模拟状态
        
        // 2. 取消协程任务（✅ 替代 HandlerThread 清理）
        moveJob?.cancel()
        serviceScope.cancel() // 取消作用域内所有子协程
        
        // 3. 关闭摇杆移动控制器（取消定时器 + 关闭执行器）
        movementController.shutdown()
        
        // 4. 解注册监听器（防止内存泄漏）
        floatingWindowManager?.setListener(null)
        
        // 5. 销毁悬浮窗管理器（会清理所有视图和协程）
        floatingWindowManager?.destroy()
        isJoystickVisible = false
        
        // 6. 清理 floatingWindowManager 引用
        floatingWindowManager = null
        
        // 7. 销毁路线播放引擎
        routePlaybackEngine?.destroy()
        routePlaybackEngine = null
        
        // 8. 移除 TestProvider
        positionInjector.removeTestProviders()

        // 9. 注销广播接收器
        try { 
            unregisterReceiver(noteActionReceiver)
            Timber.d("noteActionReceiver unregistered")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "noteActionReceiver not registered or already unregistered")
        } catch (e: Exception) {
            Timber.e(e, "unregisterReceiver failed unexpectedly")
        }
        
        // 10. 注销 SharedPreferences 监听器
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        
        // 11. 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        // 12. 清理 PoiSearchHelper 引用（防止内存泄漏）
        poiSearchHelper = null

        Timber.d("LocationService destroyed completely")
        super.onDestroy()
    }

    // ==================== 位置更新循环 ====================

    private fun initLocationUpdateLoop() {
        // ✅ 修复：取消旧的协程，防止多个同时运行
        moveJob?.cancel()
        Timber.d("🔄 initLocationUpdateLoop: 启动位置更新协程")
        moveJob = serviceScope.launch {
            var loopCount = 0
            while (isActive) {
                loopCount++
                // 动态读取最新的更新间隔
                val interval = prefs.getLong(PrefsConfig.Settings.KEY_LOCATION_UPDATE_INTERVAL, DEFAULT_LOCATION_UPDATE_INTERVAL_MS)
                delay(interval)
                
                if (isRunning) {
                    positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                    if (loopCount % 10 == 0) {  // 每10次循环打印一次日志
                        Timber.d("🔄 位置更新循环 #${loopCount}: isRunning=$isRunning")
                    }
                } else {
                    if (loopCount % 50 == 0) {  // 每50次打印一次，避免日志过多
                        Timber.d("⏸️ 位置更新循环 #${loopCount}: isRunning=$isRunning (跳过注入)")
                    }
                }
            }
            Timber.d("🛑 位置更新协程已退出")
        }
    }

    // ==================== 模拟控制 ====================

    private fun startSimulation(latitude: Double, longitude: Double) {
        if (isRunning) {
            updateTargetLocation(latitude, longitude)
            return
        }
        positionInjector.updatePosition(latitude, longitude)
        isRunning = true
        staticIsRunning = true
        _simulationState.update { it.copy(isSimulating = true) }
        positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
        positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
        // 如果协程循环已被取消（stopSimulation后重启），需要重新启动
        if (moveJob?.isActive != true) {
            initLocationUpdateLoop()
        }
        
        // 保存当前位置信息，用于开机自启恢复
        saveLastLocation()
        
        Timber.d("Simulation started at WGS84 ($latitude, $longitude)")
    }

    private fun stopSimulation() {
        if (!isRunning) return
        
        isRunning = false
        staticIsRunning = false
        _simulationState.update { it.copy(isSimulating = false, isAutoMoving = false) }
        moveJob?.cancel() // ✅ 取消协程任务
        movementController.cancelAutoMove()
        floatingWindowManager?.hide()
        isJoystickVisible = false
        stopSelf()
    }

    /**
     * 保存最后位置信息到 SharedPreferences
     * 用于开机自启时恢复模拟位置
     */
    private fun saveLastLocation() {
        try {
            val (lat, lng, alt) = positionInjector.getPositionTriple()
            val gcj = MapUtils.wgs84ToGcj02(lng, lat)
            prefs.edit()
                .putString(PrefsConfig.Settings.KEY_LAST_LAT, gcj[1].toString())
                .putString(PrefsConfig.Settings.KEY_LAST_LNG, gcj[0].toString())
                .putString(PrefsConfig.Settings.KEY_LAST_ALT, alt.toString())
                .apply()
            Timber.d("Last location saved: GCJ02 (${gcj[1]}, ${gcj[0]}), alt=$alt")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save last location")
        }
    }

    private fun updateTargetLocation(latitude: Double, longitude: Double) {
        positionInjector.updatePosition(latitude, longitude)
    }

    // ==================== 速度模式（委托给 MovementController）====================

    /**
     * 切换速度模式（由悬浮窗调用）
     * 委托给 MovementController，同时更新 SimulationState
     * @param mode 速度模式（walk/run/bike）
     */
    fun setSpeedMode(mode: String) {
        movementController.setSpeedMode(mode) { newMode ->
            _simulationState.update { it.copy(speedMode = newMode) }
        }
    }

    /** 设置移动速度（委托给 MovementController） */
    fun setSpeed(speed: Float) {
        movementController.setSpeed(speed)
    }
    
    private fun getAltitudeFromPrefs(): Double {
        return prefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 0f).toDouble()
    }

    /**
     * 设置模拟位置（WGS-84 坐标系）
     * @param lat 纬度
     * @param lng 经度
     * @param alt 海拔
     */
    fun setPositionWgs84(lat: Double, lng: Double, alt: Double) {
        Timber.d("🔍 setPositionWgs84 被调用: lat=$lat, lng=$lng, alt=$alt, isRunning=$isRunning")
        serviceScope.launch(Dispatchers.IO) {
            Timber.d("🔍 setPositionWgs84 在 IO 线程中执行")
            if (!isRunning) {
                Timber.d("🚀 setPositionWgs84: 检测到未启动模拟，自动启动")
                positionInjector.updatePosition(lat, lng, alt)
                isRunning = true
                staticIsRunning = true
                _simulationState.update { it.copy(isSimulating = true) }
                positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)

                if (moveJob?.isActive != true) {
                    Timber.d("🔄 协程未运行，重新启动 initLocationUpdateLoop")
                    initLocationUpdateLoop()
                } else {
                    Timber.d("✅ 协程已在运行: isActive=${moveJob?.isActive}")
                }

                saveLastLocation()
                Timber.d("✅ 模拟已自动启动: lat=$lat, lng=$lng")
            } else {
                Timber.d("📍 已在模拟中，更新位置")
                positionInjector.updatePosition(lat, lng, alt)
                positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                Timber.d("✅ 位置已更新: lat=$lat, lng=$lng")
            }
        }
    }

    /**
     * 更新路线播放位置
     * 
     * 和摇杆模式一致：只更新坐标，initLocationUpdateLoop 协程统一按间隔注入
     */
    fun updatePlaybackPosition(gcjLng: Double, gcjLat: Double, alt: Double, bearing: Float) {
        serviceScope.launch(Dispatchers.IO) {
            val wgs = MapUtils.gcj02ToWgs84(gcjLng, gcjLat)
            val wgsLng = wgs[0]
            val wgsLat = wgs[1]

            if (kotlin.math.abs(wgsLat) > 90 || kotlin.math.abs(wgsLng) > 180) {
                Timber.w("⚠️ updatePlaybackPosition: 转换后的坐标超出范围 lat=$wgsLat, lng=$wgsLng，跳过更新")
                return@launch
            }

            positionInjector.updatePosition(wgsLat, wgsLng, alt)
            positionInjector.updateBearing(bearing)
            Timber.d("🎬 updatePlaybackPosition: lat=$gcjLat, lng=$gcjLng, bearing=$bearing (coordinate updated, injection delegated to loop)")
        }
    }

    /** 获取当前位置（WGS-84），委托给 PositionInjector */
    fun getCurrentLocation(): Pair<Double, Double> = positionInjector.getCurrentPosition()

    /** 获取当前位置（GCJ-02，高德地图坐标系），委托给 PositionInjector */
    fun getCurrentLocationGcj02(): Pair<Double, Double> = positionInjector.getCurrentPositionGcj02()
    

    // ==================== 日志控制 ====================

    private var loggingEnabled = true

    private fun updateLoggingTree(enabled: Boolean) {
        loggingEnabled = enabled
        if (enabled) {
            // 确保 DebugTree 存在
            if (Timber.forest().none { it is Timber.DebugTree }) {
                Timber.plant(Timber.DebugTree())
            }
        } else {
            // 仅移除 DebugTree，保留其他组件（如 LeakCanary）种植的 Tree
            Timber.forest()
                .filterIsInstance<Timber.DebugTree>()
                .forEach { Timber.uproot(it) }
        }
    }

    private fun initLoggingFromPrefs() {
        loggingEnabled = prefs.getBoolean(PrefsConfig.Settings.KEY_LOGGING, true)
        if (!loggingEnabled) {
            updateLoggingTree(false)
        }
    }

    // ==================== 历史记录清理 ====================

    // ✅ Phase 1: 通过 LocationRepository 清理过期历史
    private val locationRepository by lazy {
        val db = com.mockloc.VirtualLocationApp.getDatabase()
        com.mockloc.data.repository.LocationRepository(db.historyLocationDao(), db.favoriteLocationDao())
    }

    private fun cleanupExpiredHistory() {
        val expiryDays = prefs.getInt(PrefsConfig.Settings.KEY_HISTORY_EXPIRY, 30)
        if (expiryDays <= 0) return  // -1 = 永久保存
        val cutoffTime = System.currentTimeMillis() - (expiryDays.toLong() * 24 * 60 * 60 * 1000)
        
        // ✅ 关键修复：使用 serviceScope 异步执行，避免阻塞主线程
        serviceScope.launch(Dispatchers.IO) {
            try {
                locationRepository.deleteHistoryOlderThan(cutoffTime)
                Timber.d("Cleaned up history older than $expiryDays days")
            } catch (e: Exception) {
                Timber.w(e, "Failed to cleanup expired history")
            }
        }
    }

    // ==================== 通知栏 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "定位服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "虚拟定位服务运行状态"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val clickIntent = Intent(this, MainActivity::class.java)
        val clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val showPI = PendingIntent.getBroadcast(this, 0, Intent(NOTE_ACTION_SHOW), PendingIntent.FLAG_IMMUTABLE)
        val hidePI = PendingIntent.getBroadcast(this, 0, Intent(NOTE_ACTION_HIDE), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.simulating))
            .setSmallIcon(R.drawable.ic_my_location)
            .setContentIntent(clickPI)
            .addAction(NotificationCompat.Action(null, "显示摇杆", showPI))
            .addAction(NotificationCompat.Action(null, "隐藏摇杆", hidePI))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun registerNoteActionReceiver() {
        try {
            noteActionReceiver = NoteActionReceiver()
            val filter = IntentFilter().apply {
                addAction(NOTE_ACTION_SHOW)
                addAction(NOTE_ACTION_HIDE)
            }

            ContextCompat.registerReceiver(
                this,
                noteActionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Timber.w(t = e, message = "registerNoteActionReceiver failed")
        }
    }

    inner class NoteActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NOTE_ACTION_SHOW -> {
                    floatingWindowManager?.show()
                    isJoystickVisible = true
                }
                NOTE_ACTION_HIDE -> {
                    floatingWindowManager?.hide()
                    isJoystickVisible = false
                }
            }
        }
    }
}

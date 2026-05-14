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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * 虚拟定位前台服务
 * 核心职责：注册 TestProvider 并注入模拟位置、坐标系转换（GCJ-02 ↔ WGS-84）、
 * 摇杆控制（委托 MovementController）、前台通知、悬浮窗（委托 FloatingWindowManager）
 */
class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var moveJob: Job? = null

    // ── 对外暴露的 StateFlow（供 ServiceConnector 订阅）─────────
    private val _simulationState = MutableStateFlow(SimulationState())
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    /** 路线播放状态（透传 RoutePlaybackEngine） */
    val routePlaybackState: StateFlow<RoutePlaybackState>
        get() = routePlaybackEngine?.state ?: MutableStateFlow(RoutePlaybackState()).asStateFlow()

    /** 地图状态（悬浮窗与主界面共享） */
    data class SharedMapState(
        val centerLat: Double = 0.0,
        val centerLng: Double = 0.0,
        val zoom: Float = 15f,
        val markedLat: Double = 0.0,
        val markedLng: Double = 0.0,
        val hasMarkedPosition: Boolean = false
    )
    private val _sharedMapState = MutableStateFlow(SharedMapState())
    val sharedMapState: StateFlow<SharedMapState> = _sharedMapState.asStateFlow()

    data class SimulationState(
        val isSimulating: Boolean = false,
        val isAutoMoving: Boolean = false,
        val speedMode: String = "walk",
        val currentSpeed: Float = 1.4f
    )

    // ── 更新共享地图状态 ──
    fun updateSharedMapState(centerLat: Double, centerLng: Double, zoom: Float, 
                            markedLat: Double? = null, markedLng: Double? = null) {
        _sharedMapState.update { state ->
            state.copy(
                centerLat = centerLat,
                centerLng = centerLng,
                zoom = zoom,
                markedLat = markedLat ?: state.markedLat,
                markedLng = markedLng ?: state.markedLng,
                hasMarkedPosition = if (markedLat != null && markedLng != null) true else state.hasMarkedPosition
            )
        }
    }

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
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 100L
        private const val HANDLER_MSG_ID = 0
        private const val NOTE_ACTION_SHOW = "com.mockloc.action.SHOW_JOYSTICK"
        private const val NOTE_ACTION_HIDE = "com.mockloc.action.HIDE_JOYSTICK"

        @Volatile private var staticIsRunning = false
        fun isSimulating(): Boolean = staticIsRunning
    }

    private lateinit var locationManager: LocationManager
    @Volatile private var isRunning = false
    private val positionLock = Mutex()  // ✅ 修复：保护位置更新的互斥锁
    @Volatile private var isCleaningUp = false  // ✅ 修复：防止 onTaskRemoved 重复调用
    private lateinit var positionInjector: PositionInjector
    private lateinit var movementController: MovementController
    private var locationUpdateInterval = DEFAULT_LOCATION_UPDATE_INTERVAL_MS
    private val binder = LocalBinder()
    private lateinit var noteActionReceiver: NoteActionReceiver
    private lateinit var prefs: SharedPreferences
    private var poiSearchHelper: PoiSearchHelper? = null

    // SP 变更监听：设置页修改后即时生效
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsConfig.Settings.KEY_ALTITUDE -> {
                positionInjector.updateAltitude(prefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 55.0f).toDouble())
            }
            PrefsConfig.Settings.KEY_WALK_SPEED, PrefsConfig.Settings.KEY_RUN_SPEED, PrefsConfig.Settings.KEY_BIKE_SPEED -> {
                movementController.applySpeedMode(movementController.getCurrentSpeedMode())
            }
            PrefsConfig.Settings.KEY_JOYSTICK_TYPE -> floatingWindowManager?.onJoystickTypeChanged()
            PrefsConfig.Settings.KEY_LOGGING -> updateLoggingTree(prefs.getBoolean(PrefsConfig.Settings.KEY_LOGGING, true))
            PrefsConfig.Settings.KEY_LOCATION_UPDATE_INTERVAL -> Unit // 协程循环中自动读取最新值
            PrefsConfig.Settings.KEY_HISTORY_EXPIRY -> cleanupExpiredHistory()
        }
    }

    // ── 悬浮窗 & 路线播放 ──────────────────────────────────────
    private var floatingWindowManager: FloatingWindowManager? = null
    private var isJoystickVisible = false
    private var routePlaybackEngine: RoutePlaybackEngine? = null
    val playbackEngine: RoutePlaybackEngine? get() = routePlaybackEngine
    private var routeControlStateListener: ((Boolean) -> Unit)? = null

    fun setRouteControlStateListener(listener: ((Boolean) -> Unit)?) { routeControlStateListener = listener }
    private fun notifyRouteControlStateChanged(isPlaying: Boolean) { routeControlStateListener?.invoke(isPlaying) }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    // ── 路线操作（委托给 RoutePlaybackEngine）──────────────────────
    fun setRoute(points: List<RoutePoint>) = routePlaybackEngine?.setRoute(points) ?: Unit
    fun addRoutePoint(point: RoutePoint) = routePlaybackEngine?.addPoint(point) ?: Unit
    fun removeLastRoutePoint(): RoutePoint? = routePlaybackEngine?.removeLastPoint()
    fun removeRoutePointAt(index: Int): RoutePoint? = routePlaybackEngine?.removePointAt(index)
    fun insertRoutePointAt(index: Int, point: RoutePoint) = routePlaybackEngine?.insertPointAt(index, point) ?: Unit
    fun clearRoute() = routePlaybackEngine?.clearRoute() ?: Unit
    fun playRoute() {
        routePlaybackEngine?.play() ?: return
        // isSimulating 由 routePlaybackEngine.state.collect 自然同步，无需手动设置
    }

    fun pauseRoute() {
        routePlaybackEngine?.pause() ?: return
        // isSimulating 由 routePlaybackEngine.state.collect 自然同步
    }

    fun stopRoute() {
        routePlaybackEngine?.stop() ?: return
        // isSimulating 由 routePlaybackEngine.state.collect 自然同步
    }
    fun setRouteLooping(loop: Boolean) = routePlaybackEngine?.setLooping(loop) ?: Unit
    fun setRouteSpeedMultiplier(multiplier: Float) = routePlaybackEngine?.setSpeedMultiplier(multiplier) ?: Unit
    fun getRoutePoints(): List<RoutePoint> = routePlaybackEngine?.getPoints() ?: emptyList()
    fun getRoutePlaybackState(): RoutePlaybackState = routePlaybackEngine?.state?.value ?: RoutePlaybackState()
    
    // ── 悬浮窗操作 ──────────────────────────────────────────────
    /** 隐藏摇杆悬浮窗（路线模式下不需要摇杆） */
    fun hideFloatingWindow() {
        if (isJoystickVisible) {
            floatingWindowManager?.hide()
            isJoystickVisible = false
        }
    }

    /** 显示摇杆悬浮窗（单点模式，需悬浮窗权限且非路线播放中） */
    fun showFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) return
        val isRoutePlaying = routePlaybackEngine?.state?.value?.isPlaying == true
        if (!isRoutePlaying && !isJoystickVisible) {
            floatingWindowManager?.show()
            isJoystickVisible = true
        }
    }

    fun showRouteControlWindow() {
        if (!Settings.canDrawOverlays(this)) return
        floatingWindowManager?.showRouteControl()
    }

    fun hideRouteControlWindow() = floatingWindowManager?.hideRouteControl() ?: Unit

    fun stopRoutePlayback() = routePlaybackEngine?.stop() ?: Unit

    // ── 生命周期 ────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)

        // 初始化核心组件（顺序敏感：PositionInjector → MovementController）
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        positionInjector = PositionInjector(locationManager)
        positionInjector.updateAltitude(prefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 55.0f).toDouble())
        movementController = MovementController(positionInjector, prefs)
        movementController.applySpeedMode(prefs.getString(PrefsConfig.Settings.KEY_SPEED_MODE, "walk") ?: "walk")
        poiSearchHelper = PoiSearchHelper(applicationContext)
        locationUpdateInterval = prefs.getLong(PrefsConfig.Settings.KEY_LOCATION_UPDATE_INTERVAL, DEFAULT_LOCATION_UPDATE_INTERVAL_MS)

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        initLoggingFromPrefs()
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
        } catch (e: Exception) {
            Timber.e(e, "onCreate initialization failed")
        }

        // 初始化悬浮窗（不自动显示，ACTION_START 时按需显示）
        initFloatingWindow()

        // 初始化路线播放引擎
        routePlaybackEngine = RoutePlaybackEngine(serviceScope) { latLng, bearing ->
            updatePlaybackPosition(latLng.longitude, latLng.latitude, positionInjector.altitude, bearing)
        }
        serviceScope.launch {
            routePlaybackEngine?.state?.collect { state ->
                notifyRouteControlStateChanged(state.isPlaying)
                // ✅ 修复：路线播放状态变化时同步更新模拟状态
                // 忽略 isStarting 过渡状态，只在 isPlaying 真正变化时更新
                _simulationState.update { it.copy(isSimulating = state.isPlaying) }
            }
        }
    }

    /** 创建悬浮窗事件监听器（摇杆方向/选点/速度） */
    private fun createFloatingWindowListener() = object : FloatingWindowManager.FloatingWindowListener {
        override fun onDirection(auto: Boolean, angle: Double, r: Double) {
            movementController.processDirection(auto, angle, r)
        }
        override fun onPositionSelected(wgsLng: Double, wgsLat: Double, alt: Double) {
            setPositionWgs84(wgsLat, wgsLng, alt)
        }
        override fun onSpeedChanged(speedMs: Float) {
            movementController.setSpeed(speedMs)
        }
    }

    /** 初始化悬浮窗管理器（创建 + 绑定监听器 + init） */
    private fun initFloatingWindow() {
        // ✅ 修复：传入 serviceScope，确保生命周期一致
        floatingWindowManager = FloatingWindowManager(this, serviceScope)
        floatingWindowManager?.setListener(createFloatingWindowListener())
        try {
            if (Settings.canDrawOverlays(this)) {
                floatingWindowManager?.init()
            }
        } catch (e: Exception) {
            Timber.e(e, "FloatingWindowManager init failed")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            }
            ACTION_STOP -> stopSimulation()
            ACTION_UPDATE -> {
                val (curLat, curLng) = positionInjector.getCurrentPosition()
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, curLat)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, curLng)
                val isGcj02 = intent.getBooleanExtra(EXTRA_COORD_GCJ02, true)
                if (isGcj02) {
                    val wgs = MapUtils.gcj02ToWgs84(longitude, latitude)
                    updateTargetLocation(wgs[1], wgs[0])
                } else {
                    updateTargetLocation(latitude, longitude)
                }
                try {
                    positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to inject location manually")
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /** 用户从最近任务中滑动删除：停止模拟、关闭悬浮窗、停止服务 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // ✅ 修复：防止重复调用导致的竞态条件
        if (isCleaningUp) {
            Timber.w("onTaskRemoved already in progress, skipping")
            return
        }
        isCleaningUp = true
        
        try {
            if (isRunning) stopSimulation()
            // ✅ 修复：不仅隐藏，还要彻底销毁悬浮窗管理器，防止内存泄漏
            if (isJoystickVisible || floatingWindowManager != null) {
                floatingWindowManager?.destroy()
                isJoystickVisible = false
                floatingWindowManager = null
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Timber.e(e, "onTaskRemoved cleanup failed")
        } finally {
            isCleaningUp = false
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 无论悬浮窗是否可见都要同步主题，否则下次打开会用旧 Context
        floatingWindowManager?.syncMapWithSystemTheme()
    }

    override fun onDestroy() {
        isRunning = false
        staticIsRunning = false
        _simulationState.update { SimulationState() }
        moveJob?.cancel()
        serviceScope.cancel()
        movementController.shutdown()
        floatingWindowManager?.setListener(null)
        floatingWindowManager?.destroy()
        isJoystickVisible = false
        floatingWindowManager = null
        routePlaybackEngine?.destroy()
        routePlaybackEngine = null
        positionInjector.removeTestProviders()
        try { unregisterReceiver(noteActionReceiver) } catch (e: Exception) { Timber.w(e) }
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        poiSearchHelper = null
        super.onDestroy()
    }

    // ── 位置更新循环 ────────────────────────────────────────────

    private fun initLocationUpdateLoop() {
        moveJob?.cancel()
        moveJob = serviceScope.launch {
            var loopCount = 0
            while (isActive) {
                loopCount++
                val interval = prefs.getLong(PrefsConfig.Settings.KEY_LOCATION_UPDATE_INTERVAL, DEFAULT_LOCATION_UPDATE_INTERVAL_MS)
                delay(interval)
                if (isRunning) {
                    positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                    if (loopCount % 10 == 0) Timber.d("位置更新循环 #$loopCount: isRunning=$isRunning")
                }
            }
        }
    }

    // ── 模拟控制 ────────────────────────────────────────────────

    private fun startSimulation(latitude: Double, longitude: Double) {
        // ✅ 修复：使用 Mutex 保护 isRunning 检查和位置更新，防止 TOCTOU 竞态
        serviceScope.launch(Dispatchers.IO) {
            positionLock.withLock {
                if (isRunning) { 
                    updateTargetLocation(latitude, longitude)
                    return@withLock 
                }
                positionInjector.updatePosition(latitude, longitude)
                isRunning = true
                staticIsRunning = true
                _simulationState.update { it.copy(isSimulating = true) }
                positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                if (moveJob?.isActive != true) initLocationUpdateLoop()
                saveLastLocation()
            }
        }
    }

    private fun stopSimulation() {
        // ✅ 修复：使用 Mutex 保护 isRunning 检查和状态修改
        serviceScope.launch(Dispatchers.IO) {
            positionLock.withLock {
                if (!isRunning) return@withLock
                isRunning = false
                staticIsRunning = false
                _simulationState.update { it.copy(isSimulating = false, isAutoMoving = false) }
                moveJob?.cancel()
                movementController.cancelAutoMove()
                floatingWindowManager?.hide()
                isJoystickVisible = false
                stopSelf()
            }
        }
    }

    /** 保存最后位置到 SP（用于开机自启恢复），坐标系 GCJ-02 */
    private fun saveLastLocation() {
        try {
            val (lat, lng, alt) = positionInjector.getPositionTriple()
            val gcj = MapUtils.wgs84ToGcj02(lng, lat)
            prefs.edit()
                .putString(PrefsConfig.Settings.KEY_LAST_LAT, gcj[1].toString())
                .putString(PrefsConfig.Settings.KEY_LAST_LNG, gcj[0].toString())
                .putString(PrefsConfig.Settings.KEY_LAST_ALT, alt.toString())
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save last location")
        }
    }

    private fun updateTargetLocation(latitude: Double, longitude: Double) = positionInjector.updatePosition(latitude, longitude)

    // ── 速度模式（委托给 MovementController）────────────────────

    /** 切换速度模式（walk/run/bike），同时更新 SimulationState */
    fun setSpeedMode(mode: String) {
        movementController.setSpeedMode(mode) { newMode ->
            _simulationState.update { it.copy(speedMode = newMode) }
        }
    }

    fun setSpeed(speed: Float) = movementController.setSpeed(speed)
    private fun getAltitudeFromPrefs(): Double = prefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 0f).toDouble()

    /** 设置模拟位置（WGS-84 坐标系），未启动时自动启动 */
    fun setPositionWgs84(lat: Double, lng: Double, alt: Double) {
        serviceScope.launch(Dispatchers.IO) {
            // ✅ 修复：使用 Mutex 保护 isRunning 检查和位置更新，防止 TOCTOU 竞态
            positionLock.withLock {
                if (!isRunning) {
                    positionInjector.updatePosition(lat, lng, alt)
                    isRunning = true
                    staticIsRunning = true
                    _simulationState.update { it.copy(isSimulating = true) }
                    positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                    if (moveJob?.isActive != true) initLocationUpdateLoop()
                    saveLastLocation()
                } else {
                    positionInjector.updatePosition(lat, lng, alt)
                    positionInjector.setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    positionInjector.setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                }
            }
        }
    }

    /** 更新路线播放位置（GCJ-02 → WGS-84 转换后更新坐标，注入由循环统一执行） */
    fun updatePlaybackPosition(gcjLng: Double, gcjLat: Double, alt: Double, bearing: Float) {
        // 同步更新 sharedMapState，使主界面 BottomSheet 能实时显示路线模拟位置
        _sharedMapState.update { it.copy(centerLat = gcjLat, centerLng = gcjLng) }
        serviceScope.launch(Dispatchers.IO) {
            val wgs = MapUtils.gcj02ToWgs84(gcjLng, gcjLat)
            if (kotlin.math.abs(wgs[1]) > 90 || kotlin.math.abs(wgs[0]) > 180) return@launch
            positionInjector.updatePosition(wgs[1], wgs[0], alt)
            positionInjector.updateBearing(bearing)
        }
    }

    fun getCurrentLocation(): Pair<Double, Double> = positionInjector.getCurrentPosition()
    fun getCurrentLocationGcj02(): Pair<Double, Double> = positionInjector.getCurrentPositionGcj02()
    

    // ── 日志控制 ────────────────────────────────────────────────

    private var loggingEnabled = true

    private fun updateLoggingTree(enabled: Boolean) {
        loggingEnabled = enabled
        if (enabled) {
            if (Timber.forest().none { it is Timber.DebugTree }) Timber.plant(Timber.DebugTree())
        } else {
            Timber.forest().filterIsInstance<Timber.DebugTree>().forEach { Timber.uproot(it) }
        }
    }

    private fun initLoggingFromPrefs() {
        loggingEnabled = prefs.getBoolean(PrefsConfig.Settings.KEY_LOGGING, true)
        if (!loggingEnabled) updateLoggingTree(false)
    }

    // ── 历史记录清理 ────────────────────────────────────────────

    private val locationRepository by lazy {
        val db = com.mockloc.VirtualLocationApp.getDatabase()
        com.mockloc.data.repository.LocationRepository(db.historyLocationDao(), db.favoriteLocationDao())
    }

    private fun cleanupExpiredHistory() {
        val expiryDays = prefs.getInt(PrefsConfig.Settings.KEY_HISTORY_EXPIRY, 30)
        if (expiryDays <= 0) return
        val cutoffTime = System.currentTimeMillis() - (expiryDays.toLong() * 24 * 60 * 60 * 1000)
        serviceScope.launch(Dispatchers.IO) {
            try { locationRepository.deleteHistoryOlderThan(cutoffTime) } catch (e: Exception) { Timber.w(e) }
        }
    }

    // ── 通知栏 ──────────────────────────────────────────────────

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
        val clickPI = PendingIntent.getActivity(this, 1,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
            val filter = IntentFilter().apply { addAction(NOTE_ACTION_SHOW); addAction(NOTE_ACTION_HIDE) }
            ContextCompat.registerReceiver(this, noteActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Timber.w(t = e, message = "registerNoteActionReceiver failed")
        }
    }

    inner class NoteActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NOTE_ACTION_SHOW -> { floatingWindowManager?.show(); isJoystickVisible = true }
                NOTE_ACTION_HIDE -> { floatingWindowManager?.hide(); isJoystickVisible = false }
            }
        }
    }
}

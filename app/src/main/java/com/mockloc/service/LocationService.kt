package com.mockloc.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import java.util.concurrent.Executors
import androidx.core.app.NotificationCompat
import com.mockloc.R
import com.mockloc.ui.main.MainActivity
import com.mockloc.util.MapUtils
import com.mockloc.util.UIFeedbackHelper
import com.mockloc.widget.JoystickView
import timber.log.Timber
import com.mockloc.VirtualLocationApp

/**
 * 虚拟定位服务（前台服务）
 * 
 * 核心职责：
 * 1. 注册 TestProvider (GPS + Network) 并持续注入模拟位置
 * 2. 坐标系转换：高德地图使用 GCJ-02，Mock Location 注入 WGS-84
 * 3. 支持摇杆控制移动（方向+速度）— 摇杆悬浮窗直接在本服务中创建
 * 4. 前台服务 + 通知栏操作（显示/隐藏摇杆）
 * 5. 服务被系统回收后自动重启
 * 
 * 参考项目架构：摇杆直接在 ServiceGo（前台服务）中创建，不需要单独的悬浮窗服务。
 * 悬浮窗管理委托给 FloatingWindowManager（参考项目 JoyStick.java 的职责拆分）。
 */
class LocationService : Service() {

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
        private const val GPS_SATELLITE_COUNT = 7

        private const val METERS_PER_DEGREE_LAT = 110.574
        private const val METERS_PER_DEGREE_LNG_EQUATOR = 111.320

        // 速度限制（m/s）
        private const val MAX_SPEED_MS = 33.33f   // 120 km/h（高速公路限速）
        private const val MIN_SPEED_MS = 0.1f     // 0.36 km/h（避免完全静止）

        private const val NOTE_ACTION_SHOW = "com.mockloc.action.SHOW_JOYSTICK"
        private const val NOTE_ACTION_HIDE = "com.mockloc.action.HIDE_JOYSTICK"

        private const val JOYSTICK_MOVE_INTERVAL_MS = 1000L

        /** 静态标志，供外部查询服务是否正在运行 */
        @Volatile
        private var staticIsRunning = false

        /** 外部查询服务是否正在模拟 */
        fun isSimulating(): Boolean = staticIsRunning
    }

    private lateinit var locationManager: LocationManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    @Volatile private var isRunning = false
    @Volatile private var currentLatitude = 0.0
    @Volatile private var currentLongitude = 0.0
    @Volatile private var altitude = 55.0
    @Volatile private var currentSpeed = 1.4f
    @Volatile private var currentBearing = 0f
    
    // 位置更新间隔（可从设置中读取）
    private var locationUpdateInterval = DEFAULT_LOCATION_UPDATE_INTERVAL_MS

    private val binder = LocalBinder()
    private val moveExecutor = Executors.newSingleThreadExecutor()
    private lateinit var noteActionReceiver: NoteActionReceiver
    private lateinit var prefs: SharedPreferences

    // 当前速度模式（持久化到 SP）
    @Volatile private var currentSpeedMode = "walk"

    // SP 变更监听器：设置页修改后即时生效
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "altitude" -> {
                altitude = prefs.getFloat("altitude", 55.0f).toDouble()
                Timber.d("Altitude updated from settings: $altitude")
            }
            "walk_speed", "run_speed", "bike_speed" -> {
                // 当前模式的速度值被修改了，重新应用
                applySpeedMode(currentSpeedMode)
            }
            "joystick_type" -> {
                // 摇杆类型变化，通知悬浮窗切换
                floatingWindowManager.onJoystickTypeChanged()
            }
            "logging" -> {
                val enabled = prefs.getBoolean("logging", true)
                updateLoggingTree(enabled)
                Timber.d("Logging updated from settings: $enabled")
            }
            "location_update_interval" -> {
                // 位置更新间隔变化
                locationUpdateInterval = prefs.getLong("location_update_interval", DEFAULT_LOCATION_UPDATE_INTERVAL_MS)
                Timber.d("Location update interval updated: ${locationUpdateInterval}ms")
            }
            "history_expiry" -> {
                // 历史有效期变化，立即清理过期记录
                cleanupExpiredHistory()
            }
        }
    }

    // ==================== 悬浮窗管理 ====================
    private lateinit var floatingWindowManager: FloatingWindowManager
    private var isJoystickVisible = false

    // ==================== 摇杆自动移动 ====================
    private var autoMoveTimer: android.os.CountDownTimer? = null
    private var isAutoMoving = false
    private var joystickAngle = 0.0
    private var joystickR = 0.0

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    // ==================== 生命周期 ====================

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        altitude = prefs.getFloat("altitude", 55.0f).toDouble()
        currentSpeedMode = prefs.getString("speed_mode", "walk") ?: "walk"
        
        // 读取位置更新间隔设置（毫秒）
        locationUpdateInterval = prefs.getLong("location_update_interval", DEFAULT_LOCATION_UPDATE_INTERVAL_MS)
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
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            registerTestProviders()
            initLocationUpdateLoop()
            registerNoteActionReceiver()
            Timber.d("LocationService created, TestProviders registered")
        } catch (e: Exception) {
            Timber.e(e, "LocationService onCreate initialization failed")
        }

        // 初始化悬浮窗管理器
        floatingWindowManager = FloatingWindowManager(this)
        floatingWindowManager.setListener(object : FloatingWindowManager.FloatingWindowListener {
            override fun onDirection(auto: Boolean, angle: Double, r: Double) {
                processDirection(auto, angle, r)
            }
            override fun onPositionSelected(wgsLng: Double, wgsLat: Double, alt: Double) {
                setPositionWgs84(wgsLng, wgsLat, alt)
            }
            override fun onSpeedChanged(speedMs: Float) {
                currentSpeed = speedMs
                Timber.d("Speed changed to: $speedMs m/s")
            }
        })

        // 参考项目 ServiceGo.onCreate(): initJoyStick() → mJoyStick.show()
        // 注意：不应在 onCreate 中自动显示悬浮窗，只初始化，在 ACTION_START 时显示
        try {
            if (Settings.canDrawOverlays(this)) {
                floatingWindowManager.init()
                Timber.d("FloatingWindow initialized (not shown yet)")
            } else {
                Timber.w("No overlay permission, floating window not shown")
            }
        } catch (e: Exception) {
            Timber.e(e, "FloatingWindowManager init failed")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                altitude = intent.getDoubleExtra(EXTRA_ALTITUDE, 55.0)
                val isGcj02 = intent.getBooleanExtra(EXTRA_COORD_GCJ02, true)

                if (isGcj02 && latitude != 0.0 && longitude != 0.0) {
                    val wgs = MapUtils.gcj02ToWgs84(longitude, latitude)
                    startSimulation(wgs[1], wgs[0])
                } else {
                    startSimulation(latitude, longitude)
                }

                if (!isJoystickVisible && Settings.canDrawOverlays(this)) {
                    try {
                        floatingWindowManager.show()
                        isJoystickVisible = true
                    } catch (e: Exception) {
                        Timber.w(e, "show joystick failed")
                    }
                }
            }
            ACTION_STOP -> {
                stopSimulation()
            }
            ACTION_UPDATE -> {
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, currentLatitude)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, currentLongitude)
                val isGcj02 = intent.getBooleanExtra(EXTRA_COORD_GCJ02, true)

                if (isGcj02) {
                    val wgs = MapUtils.gcj02ToWgs84(longitude, latitude)
                    updateTargetLocation(wgs[1], wgs[0])
                } else {
                    updateTargetLocation(latitude, longitude)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restartIntent = Intent(this, LocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATITUDE, currentLatitude)
                putExtra(EXTRA_LONGITUDE, currentLongitude)
                putExtra(EXTRA_ALTITUDE, altitude)
                putExtra(EXTRA_COORD_GCJ02, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "onTaskRemoved restart failed")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("Configuration changed, syncing floating window theme")
        floatingWindowManager.syncMapWithSystemTheme()
    }

    override fun onDestroy() {
        isRunning = false
        staticIsRunning = false
        cancelAutoMove()
        floatingWindowManager.destroy()
        isJoystickVisible = false

        handler.removeMessages(HANDLER_MSG_ID)
        handlerThread.quit()
        moveExecutor.shutdown()
        removeTestProviders()

        try { unregisterReceiver(noteActionReceiver) } catch (e: Exception) { Timber.w(e, "unregisterReceiver failed") }
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        stopForeground(STOP_FOREGROUND_REMOVE)

        Timber.d("LocationService destroyed")
        super.onDestroy()
    }

    // ==================== TestProvider ====================

    private fun registerTestProviders() {
        safeRemoveTestProvider(LocationManager.NETWORK_PROVIDER)
        safeRemoveTestProvider(LocationManager.GPS_PROVIDER)
        addTestProvider(LocationManager.NETWORK_PROVIDER, true,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.POWER_USAGE_LOW else Criteria.POWER_LOW,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.ACCURACY_COARSE else Criteria.ACCURACY_COARSE)
        addTestProvider(LocationManager.GPS_PROVIDER, false,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.POWER_USAGE_HIGH else Criteria.POWER_HIGH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.ACCURACY_FINE else Criteria.ACCURACY_FINE)
    }

    private fun removeTestProviders() {
        safeRemoveTestProvider(LocationManager.NETWORK_PROVIDER)
        safeRemoveTestProvider(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("WrongConstant")
    private fun addTestProvider(provider: String, requiresNetwork: Boolean, powerUsage: Int, accuracy: Int) {
        try {
            locationManager.addTestProvider(provider, requiresNetwork, false, true, true, true, true, true, powerUsage, accuracy)
            if (!locationManager.isProviderEnabled(provider)) {
                locationManager.setTestProviderEnabled(provider, true)
            }
        } catch (e: Exception) {
            Timber.w(e, "addTestProvider failed: $provider")
        }
    }

    private fun safeRemoveTestProvider(provider: String) {
        try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.setTestProviderEnabled(provider, false)
            }
            locationManager.removeTestProvider(provider)
        } catch (e: Exception) {
            Timber.w(e, "removeTestProvider failed: $provider")
        }
    }

    // ==================== 位置更新循环 ====================

    private fun initLocationUpdateLoop() {
        handlerThread = HandlerThread("LocationUpdate", Process.THREAD_PRIORITY_FOREGROUND)
        handlerThread.start()
        handler = Handler(handlerThread.looper) {
            try {
                Thread.sleep(locationUpdateInterval)
                if (isRunning) {
                    setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
                    setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
                    handler.sendEmptyMessage(HANDLER_MSG_ID)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            true
        }
    }

    // ==================== 模拟控制 ====================

    private fun startSimulation(latitude: Double, longitude: Double) {
        if (isRunning) {
            updateTargetLocation(latitude, longitude)
            return
        }
        currentLatitude = latitude
        currentLongitude = longitude
        isRunning = true
        staticIsRunning = true
        setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
        setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
        handler.sendEmptyMessage(HANDLER_MSG_ID)
        
        // 保存当前位置信息，用于开机自启恢复
        saveLastLocation()
        
        Timber.d("Simulation started at WGS84 ($latitude, $longitude)")
    }

    private fun stopSimulation() {
        if (!isRunning) return
        
        isRunning = false
        staticIsRunning = false
        handler.removeMessages(HANDLER_MSG_ID)
        cancelAutoMove()
        floatingWindowManager.hide()
        isJoystickVisible = false
        stopSelf()
    }

    /**
     * 保存最后位置信息到 SharedPreferences
     * 用于开机自启时恢复模拟位置
     */
    private fun saveLastLocation() {
        try {
            // 获取GCJ02坐标（高德地图坐标）用于保存
            val gcj = MapUtils.wgs84ToGcj02(currentLongitude, currentLatitude)
            prefs.edit()
                .putFloat("last_lat", gcj[1].toFloat())  // GCJ02纬度
                .putFloat("last_lng", gcj[0].toFloat())  // GCJ02经度
                .putFloat("last_alt", altitude.toFloat())
                .apply()
            Timber.d("Last location saved: GCJ02 (${gcj[1]}, ${gcj[0]}), alt=$altitude")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save last location")
        }
    }

    private fun updateTargetLocation(latitude: Double, longitude: Double) {
        currentLatitude = latitude
        currentLongitude = longitude
    }

    // ==================== 位置注入 ====================

    private fun setLocation(provider: String, accuracy: Int) {
        try {
            val loc = Location(provider).apply {
                this.latitude = currentLatitude
                this.longitude = currentLongitude
                this.altitude = altitude
                this.bearing = currentBearing
                this.speed = currentSpeed
                this.accuracy = accuracy.toFloat()
                this.time = System.currentTimeMillis()
                this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            if (provider == LocationManager.GPS_PROVIDER) {
                val bundle = android.os.Bundle()
                bundle.putInt("satellites", GPS_SATELLITE_COUNT)
                loc.extras = bundle
            }
            locationManager.setTestProviderLocation(provider, loc)
        } catch (e: Exception) {
            Timber.w(e, "setLocation failed: $provider")
        }
    }

    // ==================== 摇杆移动 ====================

    private fun processDirection(auto: Boolean, angle: Double, r: Double) {
        joystickAngle = angle
        joystickR = r
        
        if (r <= 0.01) {
            // 摇杆回中或归位，停止移动
            cancelAutoMove()
        } else {
            // 无论哪种模式，都使用定时器以保持速度一致
            startAutoMove()
        }
    }

    private fun startAutoMove() {
        if (isAutoMoving) return
        isAutoMoving = true
        autoMoveTimer = object : android.os.CountDownTimer(Long.MAX_VALUE, JOYSTICK_MOVE_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) { performAutoMoveStep() }
            override fun onFinish() { isAutoMoving = false }
        }.start()
    }

    private fun cancelAutoMove() {
        autoMoveTimer?.cancel()
        autoMoveTimer = null
        isAutoMoving = false
    }

    private fun performAutoMoveStep() {
        if (!isRunning || joystickR <= 0.01) return
        moveExecutor.execute {
            val angleRad = Math.toRadians(joystickAngle)
            var distanceLng = currentSpeed * (JOYSTICK_MOVE_INTERVAL_MS / 1000.0) * joystickR *
                    Math.cos(angleRad) / 1000.0
            var distanceLat = currentSpeed * (JOYSTICK_MOVE_INTERVAL_MS / 1000.0) * joystickR *
                    Math.sin(angleRad) / 1000.0
            // 随机偏移
            if (prefs.getBoolean("random_offset", false)) {
                distanceLng += (Math.random() - 0.5) * 5.0 / 1000.0
                distanceLat += (Math.random() - 0.5) * 5.0 / 1000.0
            }
            currentLongitude += distanceLng / (METERS_PER_DEGREE_LNG_EQUATOR * Math.cos(Math.toRadians(currentLatitude)))
            currentLatitude += distanceLat / METERS_PER_DEGREE_LAT
            currentBearing = (90.0f - joystickAngle).toFloat()
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)
        Timber.d("Speed set to: ${currentSpeed} m/s (${String.format("%.1f", currentSpeed * 3.6)} km/h)")
    }
    
    fun setAltitude(alt: Double) { altitude = alt }

    /** 根据速度模式从 SP 读取速度并应用 */
    private fun applySpeedMode(mode: String) {
        currentSpeedMode = mode
        val speedKmh = when (mode) {
            "walk" -> prefs.getInt("walk_speed", 5).coerceIn(1, 15)    // 步行：1-15 km/h
            "run" -> prefs.getInt("run_speed", 12).coerceIn(5, 30)     // 跑步：5-30 km/h
            "bike" -> prefs.getInt("bike_speed", 20).coerceIn(10, 50)  // 骑行：10-50 km/h
            else -> 5
        }
        currentSpeed = (speedKmh / 3.6).toFloat().coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)
        Timber.d("Speed mode applied: $mode = $speedKmh km/h = ${String.format("%.2f", currentSpeed)} m/s")
    }

    /** 切换速度模式（由悬浮窗调用） */
    fun setSpeedMode(mode: String) {
        prefs.edit().putString("speed_mode", mode).apply()
        applySpeedMode(mode)
    }

    fun setPositionGcj02(gcjLng: Double, gcjLat: Double, alt: Double) {
        val wgs = MapUtils.gcj02ToWgs84(gcjLng, gcjLat)
        moveExecutor.execute {
            handler.removeMessages(HANDLER_MSG_ID)
            currentLongitude = wgs[0]
            currentLatitude = wgs[1]
            altitude = alt
            setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
            setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
            if (isRunning) handler.sendEmptyMessage(HANDLER_MSG_ID)
        }
    }

    fun setPositionWgs84(wgsLng: Double, wgsLat: Double, alt: Double) {
        moveExecutor.execute {
            handler.removeMessages(HANDLER_MSG_ID)
            currentLongitude = wgsLng
            currentLatitude = wgsLat
            altitude = alt
            setLocation(LocationManager.NETWORK_PROVIDER, Criteria.ACCURACY_COARSE)
            setLocation(LocationManager.GPS_PROVIDER, Criteria.ACCURACY_FINE)
            if (isRunning) handler.sendEmptyMessage(HANDLER_MSG_ID)
        }
    }

    fun getCurrentLocation(): Pair<Double, Double> = Pair(currentLatitude, currentLongitude)
    fun getCurrentLocationGcj02(): Pair<Double, Double> {
        val gcj = MapUtils.wgs84ToGcj02(currentLongitude, currentLatitude)
        return Pair(gcj[1], gcj[0])
    }
    
    /**
     * 更新模拟位置（GCJ02坐标）
     * 用于在模拟过程中动态传送到新位置
     */
    fun updatePosition(gcjLat: Double, gcjLng: Double, altitude: Double) {
        // GCJ02 -> WGS84
        val wgs = MapUtils.gcj02ToWgs84(gcjLng, gcjLat)
        setPositionWgs84(wgs[0], wgs[1], altitude)
        Timber.d("Position updated to: $gcjLat, $gcjLng (GCJ02)")
    }
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
            // 移除所有 Tree（Debug 模式下只有 DebugTree）
            Timber.forest().forEach { Timber.uproot(it) }
        }
    }

    private fun initLoggingFromPrefs() {
        loggingEnabled = prefs.getBoolean("logging", true)
        if (!loggingEnabled) {
            updateLoggingTree(false)
        }
    }

    // ==================== 历史记录清理 ====================

    private fun cleanupExpiredHistory() {
        val expiryDays = prefs.getInt("history_expiry", 30)
        if (expiryDays <= 0) return  // -1 = 永久保存
        val cutoffTime = System.currentTimeMillis() - (expiryDays.toLong() * 24 * 60 * 60 * 1000)
        moveExecutor.execute {
            try {
                val db = com.mockloc.VirtualLocationApp.getDatabase()
                kotlinx.coroutines.runBlocking {
                    db.historyLocationDao().deleteOlderThan(cutoffTime)
                }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noteActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noteActionReceiver, filter)
            }
        } catch (e: Exception) {
            Timber.w(e, "registerNoteActionReceiver failed")
        }
    }

    inner class NoteActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NOTE_ACTION_SHOW -> {
                    floatingWindowManager.show()
                    isJoystickVisible = true
                }
                NOTE_ACTION_HIDE -> {
                    floatingWindowManager.hide()
                    isJoystickVisible = false
                }
            }
        }
    }
}

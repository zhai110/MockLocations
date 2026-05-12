package com.mockloc.service

import android.content.SharedPreferences
import com.mockloc.util.PrefsConfig
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 摇杆移动控制器
 *
 * 职责：
 * - 处理摇杆方向输入，控制虚拟位置移动
 * - 管理自动移动定时器（CountDownTimer）
 * - 速度模式和速度设置
 * - 随机偏移（模拟真实行走轨迹）
 *
 * 设计说明：
 * - 使用 moveExecutor 单线程执行器确保移动计算的线程安全
 * - 通过 PositionInjector 读写位置数据（WGS-84 坐标系）
 * - CountDownTimer 在主线程回调，移动计算在 moveExecutor 线程执行
 * - 坐标计算：根据摇杆角度和半径计算经纬度增量，考虑了纬度对经度距离的影响
 * - 距离单位统一使用米，常量 METERS_PER_DEGREE_* 为米/度
 *
 * Phase 3: 从 LocationService 中提取，实现单一职责
 */
class MovementController(
    private val positionInjector: PositionInjector,
    private val prefs: SharedPreferences
) {
    private val moveExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var joystickAngle = 0.0
    private var joystickR = 0.0
    private var isAutoMoving = false
    private var autoMoveTimer: android.os.CountDownTimer? = null

    /** 当前移动速度（m/s） */
    var currentSpeed = 1.4f
        private set

    /** 当前速度模式（walk/run/bike） */
    private var currentSpeedMode = "walk"

    companion object {
        /** 摇杆移动间隔（毫秒） */
        const val JOYSTICK_MOVE_INTERVAL_MS = 200L
        /** 1度纬度对应的米数 */
        const val METERS_PER_DEGREE_LAT = 110574.0
        /** 1度经度在赤道处对应的米数 */
        const val METERS_PER_DEGREE_LNG_EQUATOR = 111320.0
        /** 最小速度（m/s） */
        const val MIN_SPEED_MS = 0.1f
        /** 最大速度（m/s） */
        const val MAX_SPEED_MS = 100f
    }

    /**
     * 处理摇杆方向输入
     * @param auto 是否为自动移动模式
     * @param angle 摇杆角度（0=东，90=北，逆时针）
     * @param r 摇杆半径（0~1，0=回中，1=满偏）
     */
    fun processDirection(auto: Boolean, angle: Double, r: Double) {
        joystickAngle = angle
        joystickR = r

        if (r <= 0.01) {
            cancelAutoMove()
        } else {
            startAutoMove()
        }
    }

    /**
     * 启动自动移动
     * 使用 CountDownTimer 以固定间隔触发移动步骤
     */
    private fun startAutoMove() {
        if (isAutoMoving) return
        isAutoMoving = true
        autoMoveTimer = object : android.os.CountDownTimer(Long.MAX_VALUE, JOYSTICK_MOVE_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) { performAutoMoveStep() }
            override fun onFinish() { isAutoMoving = false }
        }.start()
    }

    /**
     * 取消自动移动
     */
    fun cancelAutoMove() {
        autoMoveTimer?.cancel()
        autoMoveTimer = null
        isAutoMoving = false
    }

    /**
     * 执行一步自动移动
     *
     * 计算逻辑：
     * 1. 根据摇杆角度和半径计算位移（米）
     * 2. 可选添加随机偏移（模拟真实行走轨迹，偏移量按时间间隔缩放）
     * 3. 米→度转换，通过 PositionInjector 更新位置和方向角
     *
     * 坐标计算注意事项：
     * - 经度距离受纬度影响：1度经度 = 111320 * cos(纬度) 米
     * - 纬度距离恒定：1度纬度 ≈ 110574 米
     * - 方向角 = 90° - 摇杆角度（因为摇杆0°=东，方向角0°=北）
     */
    private fun performAutoMoveStep() {
        if (joystickR <= 0.01) return
        moveExecutor.execute {
            val angleRad = Math.toRadians(joystickAngle)
            val intervalSec = JOYSTICK_MOVE_INTERVAL_MS / 1000.0
            var distanceLngMeters = currentSpeed * intervalSec * joystickR * Math.cos(angleRad)
            var distanceLatMeters = currentSpeed * intervalSec * joystickR * Math.sin(angleRad)

            if (prefs.getBoolean(PrefsConfig.Settings.KEY_RANDOM_OFFSET, false)) {
                distanceLngMeters += (Math.random() - 0.5) * 5.0 * intervalSec
                distanceLatMeters += (Math.random() - 0.5) * 5.0 * intervalSec
            }

            val (curLat, curLng) = positionInjector.getCurrentPosition()
            val newLng = curLng + distanceLngMeters / (METERS_PER_DEGREE_LNG_EQUATOR * Math.cos(Math.toRadians(curLat)))
            val newLat = curLat + distanceLatMeters / METERS_PER_DEGREE_LAT

            if (kotlin.math.abs(newLat) <= 90 && kotlin.math.abs(newLng) <= 180) {
                positionInjector.updatePosition(newLat, newLng)
                positionInjector.updateBearing((90.0f - joystickAngle).toFloat())
            } else {
                Timber.w("performAutoMoveStep: 计算出的坐标超出范围 lat=$newLat, lng=$newLng，跳过更新")
            }
        }
    }

    /**
     * 设置移动速度
     * @param speed 速度（m/s），会被限制在 MIN_SPEED_MS ~ MAX_SPEED_MS 范围内
     */
    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)
        positionInjector.updateSpeed(currentSpeed)
        Timber.d("Speed set to: ${currentSpeed} m/s (${String.format("%.1f", currentSpeed * 3.6)} km/h)")
    }

    /**
     * 应用速度模式
     * 从 SharedPreferences 读取对应模式的速度值
     * @param mode 速度模式（walk/run/bike）
     */
    fun applySpeedMode(mode: String) {
        currentSpeedMode = mode
        val speedKmh = when (mode) {
            "walk" -> prefs.getInt(PrefsConfig.Settings.KEY_WALK_SPEED, 5).coerceIn(1, 15)
            "run" -> prefs.getInt(PrefsConfig.Settings.KEY_RUN_SPEED, 12).coerceIn(5, 30)
            "bike" -> prefs.getInt(PrefsConfig.Settings.KEY_BIKE_SPEED, 20).coerceIn(10, 50)
            else -> 5
        }
        currentSpeed = (speedKmh / 3.6).toFloat().coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)
        positionInjector.updateSpeed(currentSpeed)
        Timber.d("Speed mode applied: $mode = $speedKmh km/h = ${String.format("%.2f", currentSpeed)} m/s")
    }

    /**
     * 切换速度模式（由悬浮窗调用）
     * 同时更新 SharedPreferences 和 SimulationState
     * @param mode 速度模式（walk/run/bike）
     * @param onStateChanged 状态变更回调，参数为新的速度模式
     */
    fun setSpeedMode(mode: String, onStateChanged: ((String) -> Unit)? = null) {
        prefs.edit().putString(PrefsConfig.Settings.KEY_SPEED_MODE, mode).apply()
        applySpeedMode(mode)
        onStateChanged?.invoke(mode)
    }

    /**
     * 获取当前速度模式
     */
    fun getCurrentSpeedMode(): String = currentSpeedMode

    /**
     * 获取当前是否正在自动移动
     */
    fun isAutoMoving(): Boolean = isAutoMoving

    /**
     * 关闭控制器，释放资源
     * 取消定时器并关闭执行器线程
     */
    fun shutdown() {
        cancelAutoMove()
        moveExecutor.shutdown()
        try {
            if (!moveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                moveExecutor.shutdownNow()
                Timber.w("moveExecutor forced to shutdown")
            } else {
                Timber.d("moveExecutor shutdown successfully")
            }
        } catch (e: InterruptedException) {
            moveExecutor.shutdownNow()
            Thread.currentThread().interrupt()
            Timber.e(e, "moveExecutor shutdown interrupted")
        }
    }
}

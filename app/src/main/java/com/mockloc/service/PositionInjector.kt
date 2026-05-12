package com.mockloc.service

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.location.provider.ProviderProperties
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 位置注入器
 *
 * 职责：
 * - 注册/移除测试 LocationProvider（GPS + Network）
 * - 构建 Location 对象并注入到 TestProvider
 * - 管理位置数据的线程安全读写
 *
 * 设计说明：
 * - 使用 ReentrantLock 保护位置数据的读写，防止与 moveExecutor 线程竞态
 * - 坐标系：内部存储 WGS-84，注入时直接使用（系统 Mock Location 使用 WGS-84）
 * - GPS Provider 额外注入卫星数量信息（extras bundle）
 *
 * Phase 3: 从 LocationService 中提取，实现单一职责
 */
class PositionInjector(
    private val locationManager: LocationManager
) {
    /** 位置数据锁，保护 currentLatitude/currentLongitude/altitude/currentBearing/currentSpeed 的读写 */
    private val locationLock = ReentrantLock()

    var currentLatitude = 0.0
        private set
    var currentLongitude = 0.0
        private set
    var altitude = 55.0
        private set
    var currentBearing = 0f
        private set
    var currentSpeed = 1.4f
        private set

    companion object {
        const val GPS_SATELLITE_COUNT = 12
    }

    // ── TestProvider 管理 ──────────────────────────────────────────

    /**
     * 注册测试 LocationProvider（GPS + Network）
     * 先移除已有的测试 Provider，再重新添加
     */
    fun registerTestProviders() {
        safeRemoveTestProvider(LocationManager.NETWORK_PROVIDER)
        safeRemoveTestProvider(LocationManager.GPS_PROVIDER)
        addTestProvider(LocationManager.NETWORK_PROVIDER, true,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.POWER_USAGE_LOW else Criteria.POWER_LOW,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.ACCURACY_COARSE else Criteria.ACCURACY_COARSE)
        addTestProvider(LocationManager.GPS_PROVIDER, false,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.POWER_USAGE_HIGH else Criteria.POWER_HIGH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ProviderProperties.ACCURACY_FINE else Criteria.ACCURACY_FINE)
    }

    /**
     * 移除测试 LocationProvider（GPS + Network）
     */
    fun removeTestProviders() {
        safeRemoveTestProvider(LocationManager.NETWORK_PROVIDER)
        safeRemoveTestProvider(LocationManager.GPS_PROVIDER)
    }

    /**
     * 添加单个测试 LocationProvider
     * API 31+ 使用 ProviderProperties 常量，API 31 以下使用 Criteria 常量
     */
    private fun addTestProvider(provider: String, requiresNetwork: Boolean, powerUsage: Int, accuracy: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(provider, requiresNetwork, false, true, true, true, true, true, powerUsage, accuracy)
            } else {
                @Suppress("WrongConstant")
                locationManager.addTestProvider(provider, requiresNetwork, false, true, true, true, true, true, powerUsage, accuracy)
            }
            if (!locationManager.isProviderEnabled(provider)) {
                locationManager.setTestProviderEnabled(provider, true)
            }
        } catch (e: Exception) {
            Timber.w(e, "addTestProvider failed: $provider")
        }
    }

    /**
     * 安全移除单个测试 LocationProvider
     * 先禁用再移除，捕获异常防止崩溃
     */
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

    // ── 位置注入 ──────────────────────────────────────────────────

    /**
     * 构建并注入 Location 对象到指定 Provider
     *
     * 步骤：
     * 1. 加锁读取位置数据
     * 2. 验证坐标有效性（非零、非 NaN、在范围内）
     * 3. 构建 Location 对象并设置属性
     * 4. GPS Provider 额外注入卫星数量
     * 5. 调用 setTestProviderLocation 注入
     *
     * @param provider LocationProvider 名称（GPS_PROVIDER 或 NETWORK_PROVIDER）
     * @param accuracy 精度值（Criteria.ACCURACY_FINE 或 Criteria.ACCURACY_COARSE）
     */
    fun setLocation(provider: String, accuracy: Int) {
        try {
            val lat: Double
            val lng: Double
            val alt: Double
            locationLock.withLock {
                lat = currentLatitude
                lng = currentLongitude
                alt = altitude
            }

            if (lat == 0.0 && lng == 0.0) {
                Timber.w("setLocation($provider): 坐标无效 (0.0, 0.0)，跳过注入")
                return
            }
            if (lat.isNaN() || lng.isNaN()) {
                Timber.w("setLocation($provider): 坐标为 NaN，跳过注入")
                return
            }
            if (kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180) {
                Timber.w("setLocation($provider): 坐标超出范围 lat=$lat, lng=$lng，跳过注入")
                return
            }

            val loc = locationLock.withLock {
                Location(provider).apply {
                    latitude = currentLatitude
                    longitude = currentLongitude
                    altitude = this@PositionInjector.altitude
                    bearing = currentBearing
                    speed = currentSpeed
                    this.accuracy = accuracy.toFloat()
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            }
            if (provider == LocationManager.GPS_PROVIDER) {
                val bundle = android.os.Bundle()
                bundle.putInt("satellites", GPS_SATELLITE_COUNT)
                loc.extras = bundle
            }
            locationManager.setTestProviderLocation(provider, loc)
            Timber.d("setLocation($provider): lat=$lat, lng=$lng, alt=$alt")
        } catch (e: Exception) {
            Timber.w(e, "setLocation failed: $provider")
        }
    }

    // ── 位置数据操作 ──────────────────────────────────────────────

    /**
     * 更新目标位置（WGS-84 坐标）
     * @param latitude 纬度
     * @param longitude 经度
     */
    fun updatePosition(latitude: Double, longitude: Double) {
        locationLock.withLock {
            currentLatitude = latitude
            currentLongitude = longitude
        }
    }

    /**
     * 更新目标位置含海拔（WGS-84 坐标）
     * @param latitude 纬度
     * @param longitude 经度
     * @param alt 海拔（米）
     */
    fun updatePosition(latitude: Double, longitude: Double, alt: Double) {
        locationLock.withLock {
            currentLatitude = latitude
            currentLongitude = longitude
            altitude = alt
        }
    }

    /**
     * 更新海拔
     * @param alt 海拔（米）
     */
    fun updateAltitude(alt: Double) {
        locationLock.withLock {
            altitude = alt
        }
    }

    /**
     * 更新方向角
     * @param bearing 方向角（度）
     */
    fun updateBearing(bearing: Float) {
        locationLock.withLock {
            currentBearing = bearing
        }
    }

    /**
     * 更新速度
     * @param speed 速度（m/s）
     */
    fun updateSpeed(speed: Float) {
        locationLock.withLock {
            currentSpeed = speed.coerceIn(0.1f, 100f)
        }
    }

    /**
     * 获取当前位置（WGS-84）
     * @return Pair(latitude, longitude)
     */
    fun getCurrentPosition(): Pair<Double, Double> = locationLock.withLock {
        Pair(currentLatitude, currentLongitude)
    }

    /**
     * 获取当前位置（GCJ-02，高德地图坐标系）
     * @return Pair(latitude, longitude)
     */
    fun getCurrentPositionGcj02(): Pair<Double, Double> {
        val (lat, lng) = locationLock.withLock { Pair(currentLatitude, currentLongitude) }
        val gcj = com.mockloc.util.MapUtils.wgs84ToGcj02(lng, lat)
        return Pair(gcj[1], gcj[0])
    }

    /**
     * 获取位置数据三元组（用于保存最后位置）
     * @return Triple(latitude, longitude, altitude)
     */
    fun getPositionTriple(): Triple<Double, Double, Double> = locationLock.withLock {
        Triple(currentLatitude, currentLongitude, altitude)
    }

    /**
     * 重置所有位置数据
     */
    fun reset() {
        locationLock.withLock {
            currentLatitude = 0.0
            currentLongitude = 0.0
            altitude = 55.0
            currentBearing = 0f
            currentSpeed = 1.4f
        }
    }
}

package com.mockloc.service

import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class RoutePoint(
    val latLng: LatLng,
    val timestamp: Long = System.currentTimeMillis()
)

data class RoutePlaybackState(
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val speedMultiplier: Float = 1f,
    val currentIndex: Int = 0,
    val totalPoints: Int = 0,
    val progress: Float = 0f
)

class RoutePlaybackEngine(
    private val scope: CoroutineScope,
    private val onPositionUpdate: (LatLng, Float) -> Unit
) {
    private val _state = MutableStateFlow(RoutePlaybackState())
    val state: StateFlow<RoutePlaybackState> = _state.asStateFlow()

    private val pointsLock = ReentrantLock()
    private var routePoints: List<RoutePoint> = emptyList()
    private var playbackJob: Job? = null

    private val baseIntervalMs = 100L  // 高频插值：100ms，确保每段路径平滑移动

    fun setRoute(points: List<RoutePoint>) {
        if (_state.value.isPlaying) stop()
        pointsLock.withLock { routePoints = points }
        _state.update { it.copy(totalPoints = points.size) }
        Timber.d("Route set: ${points.size} points")
    }

    fun addPoint(point: RoutePoint) {
        pointsLock.withLock { routePoints = routePoints + point }
        _state.update { it.copy(totalPoints = pointsLock.withLock { routePoints.size }) }
        Timber.d("Route point added: ${point.latLng}, total=${_state.value.totalPoints}")
    }

    fun removeLastPoint(): RoutePoint? {
        val removed = pointsLock.withLock {
            if (routePoints.isEmpty()) return null
            val r = routePoints.last()
            routePoints = routePoints.dropLast(1)
            r
        }
        _state.update { it.copy(totalPoints = pointsLock.withLock { routePoints.size }) }
        return removed
    }

    /**
     * 删除指定位置的点
     * @param index 点的索引（从 0 开始）
     * @return 被删除的点，如果索引无效则返回 null
     */
    fun removePointAt(index: Int): RoutePoint? {
        return pointsLock.withLock {
            if (index < 0 || index >= routePoints.size) {
                Timber.w("Invalid index: $index, size: ${routePoints.size}")
                return@withLock null
            }
            val removed = routePoints[index]
            routePoints = routePoints.toMutableList().apply { removeAt(index) }
            _state.update { it.copy(totalPoints = routePoints.size) }
            Timber.d("Route point removed at index: $index")
            removed
        }
    }

    /**
     * 在指定位置插入点
     * @param index 插入位置（从 0 开始，size 表示追加到末尾）
     * @param point 要插入的点
     */
    fun insertPointAt(index: Int, point: RoutePoint) {
        pointsLock.withLock {
            if (index < 0 || index > routePoints.size) {
                Timber.w("Invalid index: $index, size: ${routePoints.size}")
                return
            }
            routePoints = routePoints.toMutableList().apply { add(index, point) }
            _state.update { it.copy(totalPoints = routePoints.size) }
            Timber.d("Route point inserted at index: $index")
        }
    }

    fun clearRoute() {
        stop()
        pointsLock.withLock { routePoints = emptyList() }
        _state.value = RoutePlaybackState()
        Timber.d("Route cleared")
    }

    fun getPoints(): List<RoutePoint> = pointsLock.withLock { routePoints.toList() }

    fun play() {
        val points = pointsLock.withLock { routePoints }
        if (points.size < 2) {
            Timber.w("Need at least 2 points to play route")
            return
        }
        if (_state.value.isPlaying) return

        val resumeIndex = _state.value.currentIndex
        val resumeProgress = _state.value.progress
        _state.update { it.copy(isPlaying = true) }
        Timber.d("🎬 RoutePlaybackEngine.play() started: resumeIndex=$resumeIndex, resumeProgress=$resumeProgress, totalPoints=${points.size}")
        playbackJob = scope.launch(Dispatchers.Default) {
            try {
                var isFirstLoop = true
                do {
                    val pts = pointsLock.withLock { routePoints }
                    // ✅ 关键修复：只有第一圈才从 resumeIndex 开始，后续循环必须从 0 开始以实现真正的闭环
                    val startIndex = if (isFirstLoop) resumeIndex else 0
                    Timber.d("🎬 Starting loop from index: $startIndex (isFirstLoop=$isFirstLoop)")
                    isFirstLoop = false
                    
                    for (i in startIndex until pts.size - 1) {
                        if (!isActive) break
                        val from = pts[i].latLng
                        val to = pts[i + 1].latLng
                        val currentSpeed = _state.value.speedMultiplier
                        val segmentSteps = calculateSteps(from, to, currentSpeed)
                        for (step in 0..segmentSteps) {
                            if (!isActive) break
                            val fraction = if (segmentSteps == 0) 1f else step.toFloat() / segmentSteps
                            val interpolated = interpolate(from, to, fraction)
                            val bearing = calculateBearing(from, to)
                            Timber.d("🎬 Calling onPositionUpdate: $interpolated, bearing=$bearing")
                            onPositionUpdate(interpolated, bearing)
                            _state.update { s ->
                                s.copy(
                                    currentIndex = i,
                                    progress = (i + fraction) / (pts.size - 1)
                                )
                            }
                            delay((baseIntervalMs / _state.value.speedMultiplier).toLong())
                        }
                    }
                } while (_state.value.isLooping && isActive)
            } catch (e: CancellationException) {
                Timber.d("Route playback cancelled")
            } finally {
                _state.update { it.copy(isPlaying = false) }
            }
        }
        Timber.d("Route playback started")
    }

    fun pause() {
        val pausedIndex = _state.value.currentIndex
        val pausedProgress = _state.value.progress
        playbackJob?.cancel()
        playbackJob = null
        _state.update { it.copy(isPlaying = false) }
        Timber.d("Route playback paused at index=$pausedIndex, progress=$pausedProgress")
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { it.copy(isPlaying = false, currentIndex = 0, progress = 0f) }
        Timber.d("Route playback stopped")
    }

    fun setLooping(loop: Boolean) {
        _state.update { it.copy(isLooping = loop) }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        _state.update { it.copy(speedMultiplier = multiplier.coerceIn(0.5f, 4f)) }
    }

    private fun calculateSteps(from: LatLng, to: LatLng, speedMultiplier: Float): Int {
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude, distance
        )

        // 基础速度 20 km/h（合理导航速度），速度 * 倍率 = 每秒移动米数
        // baseIntervalMs = 100ms，步长 = 速度 * 0.1 秒
        val baseSpeedKmh = 20.0f
        val currentSpeedKmh = baseSpeedKmh * speedMultiplier
        val metersPerSecond = currentSpeedKmh * 1000 / 3600

        // 每步距离（基于高频插值间隔）
        val metersPerStep = metersPerSecond * (baseIntervalMs / 1000.0)

        val steps = (distance[0] / metersPerStep.coerceAtLeast(1.0)).toInt()
        return steps.coerceIn(1, 1000)
    }

    private fun interpolate(from: LatLng, to: LatLng, fraction: Float): LatLng {
        return LatLng(
            from.latitude + (to.latitude - from.latitude) * fraction,
            from.longitude + (to.longitude - from.longitude) * fraction
        )
    }

    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val y = Math.sin(dLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    }

    fun destroy() {
        stop()
        pointsLock.withLock { routePoints = emptyList() }
    }
}

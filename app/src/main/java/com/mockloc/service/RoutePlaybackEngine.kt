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

    private val baseIntervalMs = 1000L

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
        playbackJob = scope.launch(Dispatchers.Default) {
            try {
                do {
                    val pts = pointsLock.withLock { routePoints }
                    for (i in resumeIndex until pts.size - 1) {
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
        playbackJob?.cancel()
        playbackJob = null
        _state.update { it.copy(isPlaying = false) }
        Timber.d("Route playback paused at index=${_state.value.currentIndex}")
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
        
        // 根据速度动态调整步长
        // 假设基础速度为 5 km/h（步行），每秒更新 1 次
        val baseSpeedKmh = 5.0f
        val currentSpeedKmh = baseSpeedKmh * speedMultiplier
        val metersPerSecond = currentSpeedKmh * 1000 / 3600
        
        // 每步距离 = 速度 * 更新间隔（1秒）
        val metersPerStep = metersPerSecond * (baseIntervalMs / 1000.0)
        
        val steps = (distance[0] / metersPerStep.coerceAtLeast(1.0)).toInt()
        return steps.coerceIn(1, 1000)  // 提高上限到 1000
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

package com.mockloc.core.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.mockloc.service.LocationService
import com.mockloc.service.RoutePlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

/**
 * LocationService 连接抽象层
 *
 * 解决的核心问题：
 * 1. ViewModel 不再直接持有 LocationService? 可空引用
 * 2. 500ms 轮询被 flatMapLatest 响应式订阅替代
 * 3. Service 断开/重连时自动回退默认值 + 自动重新订阅
 * 4. execute/query 安全执行，无需 ?. 判空
 */
class LocationServiceConnector(private val context: Context) {

    // ==================== 连接状态 ====================

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val service: LocationService) : ConnectionState()
    }

    private val connectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var serviceConnection: ServiceConnection? = null
    private var boundService: LocationService? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ==================== 透传 Service 状态（断开时自动回退默认值）====================

    /**
     * 模拟状态：从 LocationService.simulationState 透传
     * Service 断开时自动回退默认值（isSimulating=false）
     */
    val simulationState: StateFlow<LocationService.SimulationState> = _connectionState
        .flatMapLatest { state ->
            when (state) {
                is ConnectionState.Connected -> state.service.simulationState
                else -> flowOf(LocationService.SimulationState())
            }
        }.stateIn(connectorScope, SharingStarted.Eagerly, LocationService.SimulationState())

    /**
     * 路线播放状态：从 LocationService.routePlaybackState 透传
     * Service 断开时自动回退默认值（isPlaying=false）
     */
    val routePlaybackState: StateFlow<RoutePlaybackState> = _connectionState
        .flatMapLatest { state ->
            when (state) {
                is ConnectionState.Connected -> state.service.routePlaybackState
                else -> flowOf(RoutePlaybackState())
            }
        }.stateIn(connectorScope, SharingStarted.Eagerly, RoutePlaybackState())

    /**
     * 地图共享状态：从 LocationService.sharedMapState 透传
     * Service 断开时自动回退默认值
     */
    val sharedMapState: StateFlow<com.mockloc.service.LocationService.SharedMapState> = _connectionState
        .flatMapLatest { state ->
            when (state) {
                is ConnectionState.Connected -> state.service.sharedMapState
                else -> flowOf(com.mockloc.service.LocationService.SharedMapState())
            }
        }.stateIn(connectorScope, SharingStarted.Eagerly, com.mockloc.service.LocationService.SharedMapState())

    // ==================== 安全执行 Service 操作 ====================

    /**
     * 安全执行 Service 操作（无返回值）
     * Service 未连接时静默跳过
     */
    fun execute(action: LocationService.() -> Unit) {
        val service = (connectionState.value as? ConnectionState.Connected)?.service
        if (service != null) {
            action(service)
        } else {
            Timber.w("ServiceConnector: execute called but service not connected")
        }
    }

    /**
     * 安全执行 Service 查询（有返回值）
     * Service 未连接时返回 null
     */
    fun <T> query(action: LocationService.() -> T): T? {
        val service = (connectionState.value as? ConnectionState.Connected)?.service
        return service?.action()
    }

    // ==================== 绑定/解绑 ====================

    fun bind() {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            Timber.d("ServiceConnector: already bound or connecting, skip")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as LocationService.LocalBinder
                boundService = binder.getService()
                _connectionState.value = ConnectionState.Connected(boundService!!)
                Timber.d("ServiceConnector: service connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
                _connectionState.value = ConnectionState.Disconnected
                Timber.d("ServiceConnector: service disconnected")
            }
        }
        serviceConnection = connection
        val intent = Intent(context, LocationService::class.java)
        try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Timber.d("ServiceConnector: binding service")
        } catch (e: Exception) {
            Timber.e(e, "ServiceConnector: failed to bind service")
            _connectionState.value = ConnectionState.Disconnected
            serviceConnection = null
        }
    }

    fun unbind() {
        serviceConnection?.let {
            try {
                context.unbindService(it)
            } catch (e: Exception) {
                Timber.w(e, "ServiceConnector: error unbinding service")
            }
        }
        boundService = null
        serviceConnection = null
        _connectionState.value = ConnectionState.Disconnected
        Timber.d("ServiceConnector: service unbound")
    }

    // ==================== 便捷属性 ====================

    /** 当前是否已连接 Service */
    val isConnected: Boolean
        get() = connectionState.value is ConnectionState.Connected
}

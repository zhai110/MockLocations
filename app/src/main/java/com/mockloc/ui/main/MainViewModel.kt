package com.mockloc.ui.main

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.model.LatLng
import com.mockloc.core.common.AppResult
import com.mockloc.core.service.LocationServiceConnector
import com.mockloc.data.repository.LocationRepository
import com.mockloc.data.repository.RouteRepository
import com.mockloc.data.repository.SearchRepository
import com.mockloc.repository.PoiSearchHelper
import com.mockloc.service.LocationService
import com.mockloc.service.RoutePlaybackState
import com.mockloc.service.RoutePoint
import com.mockloc.util.PrefsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 主界面ViewModel
 * 
 * 职责：
 * - 管理所有UI状态（地图、搜索、模拟、底部面板）
 * - 处理业务逻辑（位置传送、地图配置保存/恢复）
 * - 提供StateFlow供Fragment观察
 */
class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ==================== 状态定义 ====================

    /** 地图状态 */
    data class MapState(
        val center: LatLng? = null,
        val zoom: Float = 15f,
        val markedPosition: LatLng? = null,
        val isPositionPending: Boolean = false,
        val currentLocation: LatLng? = null,
        val address: String = "",
        val shouldMoveCamera: Boolean = false,  // 是否需要移动相机到标记位置
        val shouldMoveToCurrentLocation: Boolean = false  // ✅ 新增：是否需要移动相机到当前位置
    )

    /** 搜索状态 */
    data class SearchState(
        val query: String = "",
        val results: List<PoiSearchHelper.PlaceItem> = emptyList(),
        val isVisible: Boolean = false,
        val isLoading: Boolean = false
    )

    /** 模拟状态 */
    data class SimulationState(
        val isSimulating: Boolean = false,
        val speedMode: String = "walk",
        val currentSpeed: Float = 1.4f,
        val hasNewPosition: Boolean = false
    )

    /** 路线模式状态 */
    data class RouteState(
        val isRouteMode: Boolean = false,
        val routePoints: List<RoutePoint> = emptyList(),
        val playbackState: RoutePlaybackState = RoutePlaybackState(),
        val currentPlaybackPosition: LatLng? = null,
        val movementMode: MovementMode = MovementMode.WALK  // ✅ 新增：移动模式
    )
    
    /** 移动模式枚举 */
    enum class MovementMode {
        WALK,   // 步行
        RUN,    // 跑步
        BIKE    // 骑车
    }

    /** 底部面板状态 */
    data class BottomSheetState(
        val isExpanded: Boolean = false,
        val selectedSpeedMode: String = "walk"
    )

    // ==================== StateFlow ====================

    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _simulationState = MutableStateFlow(SimulationState())
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    private val _bottomSheetState = MutableStateFlow(BottomSheetState())
    val bottomSheetState: StateFlow<BottomSheetState> = _bottomSheetState.asStateFlow()

    private val _routeState = MutableStateFlow(RouteState())
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    private val _favoriteResult = MutableSharedFlow<String>()
    val favoriteResult: SharedFlow<String> = _favoriteResult.asSharedFlow()

    // ==================== 依赖 ====================

    // ✅ Phase 0: ServiceConnector 替代 locationService? + setLocationService() + 500ms轮询
    private val serviceConnector = LocationServiceConnector(application)

    // ✅ Phase 1: Repository 替代直接 DAO 访问
    private val db by lazy { com.mockloc.VirtualLocationApp.getDatabase() }
    private val locationRepository by lazy { LocationRepository(db.historyLocationDao(), db.favoriteLocationDao()) }
    private val searchRepository by lazy { SearchRepository(db.searchHistoryDao(), PoiSearchHelper(application)) }
    private val routeRepository by lazy { RouteRepository(db.savedRouteDao()) }

    /** Service 连接状态（供 Activity 观察绑定） */
    val connectionState = serviceConnector.connectionState

    /** Service 模拟状态（响应式，替代 staticIsRunning + 500ms轮询） */
    val serviceSimulationState = serviceConnector.simulationState

    /** Service 路线播放状态（响应式，替代 startRouteStateSync 轮询） */
    val serviceRoutePlaybackState = serviceConnector.routePlaybackState

    private var locationClient: AMapLocationClient? = null
    private val prefs: SharedPreferences = getApplication<Application>().getSharedPreferences(PrefsConfig.MAP_STATE, Application.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = getApplication<Application>().getSharedPreferences(PrefsConfig.SETTINGS, Application.MODE_PRIVATE)
    private val prefsAltitude: Float
        get() = settingsPrefs.getFloat(PrefsConfig.Settings.KEY_ALTITUDE, 0f)
    
    // 定位超时任务
    private var locationTimeoutJob: Job? = null
    
    companion object {
        private const val LOCATION_TIMEOUT_MS = 10000L  // 定位超时时间：10秒
    }

    // ==================== 前后台状态监听 ====================
    
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            // ✅ App 回到前台：隐藏所有悬浮窗
            Timber.d("App entered foreground")
            hideRouteControlIfNeeded()
            hideJoystickIfNeeded()
        }
        
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            // ✅ App 进入后台：根据模式显示对应的悬浮窗
            Timber.d("App entered background")
            showRouteControlIfNeeded()
            showJoystickIfNeeded()
        }
    }
    
    init {
        // ✅ 使用 ProcessLifecycleOwner 监听应用级别的前后台状态
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        Timber.d("Registered app lifecycle observer for route control window")
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化搜索功能
     */
    fun initSearch() {
        // ✅ Phase 1: searchRepository 在 lazy 中自动初始化 PoiSearchHelper，无需手动创建
    }

    /**
     * 初始化定位客户端
     */
    fun initLocation() {
        try {
            // 取消之前的超时任务（如果有）
            locationTimeoutJob?.cancel()
            
            locationClient = AMapLocationClient(getApplication())
            locationClient?.setLocationOption(AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isNeedAddress = true
                // 设置定位超时时间（单位：毫秒）
                httpTimeOut = LOCATION_TIMEOUT_MS
            })
            
            locationClient?.setLocationListener { location ->
                handleLocationResult(location)
            }
            
            locationClient?.startLocation()
            Timber.d("定位客户端已初始化，超时时间: ${LOCATION_TIMEOUT_MS}ms")
            
            // 启动超时保护
            locationTimeoutJob = viewModelScope.launch {
                delay(LOCATION_TIMEOUT_MS)
                // 如果超时后仍然没有获取到位置，记录警告
                if (_mapState.value.currentLocation == null) {
                    Timber.w("定位超时，使用默认位置或上次缓存的位置")
                    // 可选：设置一个默认位置（例如北京）
                    // _mapState.update { it.copy(currentLocation = LatLng(39.9042, 116.4074)) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "初始化定位客户端失败")
        }
    }

    /**
     * 处理定位结果
     */
    private fun handleLocationResult(location: AMapLocation?) {
        // 取消超时任务
        locationTimeoutJob?.cancel()
        locationTimeoutJob = null
        
        if (location != null && location.errorCode == 0) {
            val latLng = LatLng(location.latitude, location.longitude)
            Timber.d("定位成功: ${latLng.latitude}, ${latLng.longitude}")
            
            _mapState.update { state ->
                state.copy(
                    currentLocation = latLng,
                    address = location.address ?: "",
                    zoom = 15f,  // ✅ 定位成功后缩放到街道级别（15级）
                    shouldMoveToCurrentLocation = true  // ✅ 标记需要移动相机到当前位置
                )
            }
        } else {
            Timber.e("定位失败: ${location?.errorInfo}")
            // 定位失败时，保持当前位置不变（使用缓存或默认值）
        }
    }
    
    /**
     * 重置移动到当前位置的标志
     */
    fun resetShouldMoveToCurrentLocation() {
        _mapState.update { it.copy(shouldMoveToCurrentLocation = false) }
    }

    // ==================== 地图状态管理 ====================

    /**
     * 保存地图状态
     * @param center 当前地图中心点（可选，如果不传则使用 currentState.center）
     * @param zoom 当前缩放级别（可选，如果不传则使用 currentState.zoom）
     */
    fun saveMapState(center: com.amap.api.maps.model.LatLng? = null, zoom: Float? = null) {
        val currentState = _mapState.value
        val centerToSave = center ?: currentState.center
        val zoomToSave = zoom ?: currentState.zoom
        
        // 更新内部状态
        _mapState.update { state ->
            state.copy(
                center = centerToSave,
                zoom = zoomToSave
            )
        }
        
        prefs.edit().apply {
            centerToSave?.let {
                putFloat(PrefsConfig.MapState.KEY_LATITUDE, it.latitude.toFloat())
                putFloat(PrefsConfig.MapState.KEY_LONGITUDE, it.longitude.toFloat())
            }
            putFloat(PrefsConfig.MapState.KEY_ZOOM, zoomToSave)
            
            // ✅ 保存标记位置
            currentState.markedPosition?.let {
                putFloat(PrefsConfig.MapState.KEY_MARKED_LAT, it.latitude.toFloat())
                putFloat(PrefsConfig.MapState.KEY_MARKED_LNG, it.longitude.toFloat())
            }
            
            apply()
        }
        Timber.d("地图状态已保存: center=$centerToSave, zoom=$zoomToSave")
    }

    /**
     * 恢复地图状态
     */
    fun restoreMapState(): LatLng? {
        val lat = prefs.getFloat(PrefsConfig.MapState.KEY_LATITUDE, -1f)
        val lng = prefs.getFloat(PrefsConfig.MapState.KEY_LONGITUDE, -1f)
        val zoom = prefs.getFloat(PrefsConfig.MapState.KEY_ZOOM, 15f)
        
        return if (lat > 0 && lng > 0) {
            val center = LatLng(lat.toDouble(), lng.toDouble())
            _mapState.update { state ->
                state.copy(center = center, zoom = zoom)
            }
            Timber.d("恢复地图状态: $center, zoom=$zoom")
            center
        } else {
            // 无上次状态，显示中国地图（城市级别）
            val chinaCenter = LatLng(35.8617, 104.1954)
            _mapState.update { state ->
                state.copy(center = chinaCenter, zoom = 12f)  // ✅ 修改为城市级别（12级）
            }
            Timber.d("显示默认视图: 中国地图（城市级别，zoom=12）")
            chinaCenter
        }
    }
    
    /**
     * 恢复标记位置
     */
    fun restoreMarkedPosition(): LatLng? {
        val lat = prefs.getFloat(PrefsConfig.MapState.KEY_MARKED_LAT, -1f)
        val lng = prefs.getFloat(PrefsConfig.MapState.KEY_MARKED_LNG, -1f)
        
        return if (lat > 0 && lng > 0) {
            val marked = LatLng(lat.toDouble(), lng.toDouble())
            _mapState.update { state ->
                state.copy(markedPosition = marked)
            }
            Timber.d("恢复标记位置: $marked")
            marked
        } else {
            null
        }
    }

    // ==================== 位置选择 ====================

    /**
     * 选择地图位置
     * @param latLng 位置坐标
     * @param moveCamera 是否移动相机到该位置（默认 false）
     * @param clearAddress 是否清空地址（默认 true，从历史/收藏返回时传 false）
     */
    fun selectPosition(latLng: LatLng, moveCamera: Boolean = false, clearAddress: Boolean = true) {
        Timber.d("selectPosition called: latLng=$latLng, moveCamera=$moveCamera, clearAddress=$clearAddress")
        _mapState.update { state ->
            state.copy(
                markedPosition = latLng,
                isPositionPending = true,
                shouldMoveCamera = moveCamera,
                address = if (clearAddress) "" else state.address
            )
        }
        Timber.d("selectPosition: markedPosition updated to $latLng")
    }

    /**
     * 更新位置信息
     */
    fun updateLocationInfo(latLng: LatLng, address: String = "") {
        _mapState.update { state ->
            state.copy(
                currentLocation = latLng,
                address = address
            )
        }
    }

    /**
     * 清除待传送标记
     */
    fun clearPositionPending() {
        _mapState.update { state ->
            state.copy(isPositionPending = false)
        }
    }

    /**
     * 重置 shouldMoveCamera 标志
     */
    fun resetShouldMoveCamera() {
        _mapState.update { state ->
            state.copy(shouldMoveCamera = false)
        }
    }

    // ==================== 模拟控制 ====================

    /**
     * 绑定 LocationService（通过 ServiceConnector）
     * 由 MainActivity.onStart() 调用
     */
    fun bindService() {
        serviceConnector.bind()
    }

    /**
     * 解绑 LocationService（通过 ServiceConnector）
     * 由 MainActivity.onDestroy() 调用
     */
    fun unbindService() {
        serviceConnector.unbind()
    }

    data class SimulationControlEvent(
        val eventType: EventType,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val altitude: Float = 0f,
        val isTeleport: Boolean = false // ✅ 新增：标记是否为主动传送
    ) {
        enum class EventType {
            START_SIMULATION, STOP_SIMULATION, UPDATE_POSITION
        }
    }
    
    private val _simulationControlEvents = MutableSharedFlow<SimulationControlEvent>(extraBufferCapacity = 1)
    val simulationControlEvents: SharedFlow<SimulationControlEvent> = _simulationControlEvents.asSharedFlow()
    
    /**
     * 发送模拟控制事件
     */
    private fun sendSimulationControlEvent(event: SimulationControlEvent) {
        viewModelScope.launch {
            _simulationControlEvents.emit(event)
        }
    }
    
    /**
     * 确认传送/启动模拟
     */
    fun confirmSimulation(altitude: Float = prefsAltitude): Boolean {
        val currentState = _mapState.value
        val simulationState = _simulationState.value
        
        Timber.d("confirmSimulation called: isSimulating=${simulationState.isSimulating}, markedPosition=${currentState.markedPosition}, isPositionPending=${currentState.isPositionPending}")
            
        return if (simulationState.isSimulating) {
            if (currentState.isPositionPending && currentState.markedPosition != null) {
                val marked = currentState.markedPosition
                // 模拟中且有新位置：发送更新位置事件
                sendSimulationControlEvent(
                    SimulationControlEvent(
                        eventType = SimulationControlEvent.EventType.UPDATE_POSITION,
                        latitude = marked.latitude,
                        longitude = marked.longitude,
                        altitude = altitude,
                        isTeleport = true
                    )
                )
                    
                Timber.d("Clearing markedPosition after position update")
                _mapState.update { state ->
                    state.copy(isPositionPending = false, markedPosition = null)
                }
                    
                Timber.d("传送到新位置: ${'$'}{currentState.markedPosition}")
                true
            } else {
                // 无新位置：发送停止模拟事件
                sendSimulationControlEvent(
                    SimulationControlEvent(
                        eventType = SimulationControlEvent.EventType.STOP_SIMULATION
                    )
                )
                    
                _simulationState.update { state ->
                    state.copy(isSimulating = false)
                }
                Timber.d("停止模拟")
                true
            }
        } else {
            // 未模拟：发送启动模拟事件
            if (currentState.markedPosition != null) {
                val marked = currentState.markedPosition
                Timber.d("Starting simulation at: $marked")
                sendSimulationControlEvent(
                    SimulationControlEvent(
                        eventType = SimulationControlEvent.EventType.START_SIMULATION,
                        latitude = marked.latitude,
                        longitude = marked.longitude,
                        altitude = altitude
                    )
                )
                    
                _simulationState.update { state ->
                    state.copy(isSimulating = true)
                }
                _mapState.update { state ->
                    state.copy(
                        isPositionPending = false,
                        shouldMoveCamera = true  // ✅ 启动模拟时移动相机到标记位置
                    )
                }
                    
                Timber.d("启动模拟: ${'$'}{currentState.markedPosition}")
                true
            } else {
                Timber.w("❌ 未选择位置，无法启动模拟 (markedPosition is null)")
                false
            }
        }
    }

    /**
     * 更新模拟速度
     */
    fun updateSpeedMode(mode: String) {
        _simulationState.update { state ->
            state.copy(speedMode = mode)
        }
        serviceConnector.execute { setSpeedMode(mode) }
        Timber.d("更新速度模式: $mode")
    }

    // ==================== 搜索功能 ====================

    /**
     * 搜索地点
     */
    fun searchPlaces(query: String, centerLat: Double? = null, centerLng: Double? = null) {
        if (query.isBlank()) {
            _searchState.update { state ->
                state.copy(results = emptyList(), isVisible = false, isLoading = false)
            }
            return
        }
        
        // 显示加载状态
        _searchState.update { state ->
            state.copy(isLoading = true)
        }
        
        searchRepository.searchPlace(query, centerLat = centerLat, centerLng = centerLng) { results ->
            _searchState.update { state ->
                state.copy(
                    query = query,
                    results = results,
                    isVisible = results.isNotEmpty(),
                    isLoading = false
                )
            }
        }
    }

    /**
     * 隐藏搜索结果
     */
    fun hideSearchResults() {
        _searchState.update { state ->
            state.copy(isVisible = false)
        }
    }

    /**
     * 显示搜索结果
     */
    fun showSearchResults() {
        _searchState.update { state ->
            state.copy(isVisible = true)
        }
    }

    /**
     * 选择搜索结果
     */
    fun selectSearchResult(place: PoiSearchHelper.PlaceItem) {
        val latLng = LatLng(place.lat, place.lng)
        selectPosition(latLng, moveCamera = true)  // 搜索结果需要移动相机
        updateLocationInfo(latLng, place.name)
        hideSearchResults()
        
        // ✅ 保存到搜索历史
        saveToSearchHistory(place)
    }
    
    /**
     * 保存搜索历史（带去重）— ✅ Phase 1: 通过 SearchRepository
     */
    private fun saveToSearchHistory(place: PoiSearchHelper.PlaceItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = searchRepository.saveToSearchHistory(
                keyword = place.name,
                name = place.name,
                address = place.address,
                latitude = place.lat,
                longitude = place.lng
            )
            if (result is AppResult.Error) {
                Timber.e(result.exception, "Failed to save search history")
            }
        }
    }

    // ==================== 底部面板 ====================

    /**
     * 更新底部面板状态
     */
    fun updateBottomSheetState(isExpanded: Boolean, speedMode: String? = null) {
        _bottomSheetState.update { state ->
            state.copy(
                isExpanded = isExpanded,
                selectedSpeedMode = speedMode ?: state.selectedSpeedMode
            )
        }
    }

    // ==================== 路线模拟 ====================

    fun setRouteMode(enabled: Boolean) {
        _routeState.update { it.copy(isRouteMode = enabled) }
        if (!enabled) {
            // 切换回单点模式：停止路线播放，隐藏路线控制悬浮窗
            serviceConnector.execute { stopRoute() }
            serviceConnector.execute { hideRouteControlWindow() }
        } else {
            // ✅ 切换到路线模式：隐藏摇杆悬浮窗
            serviceConnector.execute { hideFloatingWindow() }
            // locationService?.showRouteControlWindow()  // ❌ 不在前台显示
        }
        Timber.d("Route mode: $enabled")
    }

    fun addRoutePoint(latLng: LatLng) {
        if (!_routeState.value.isRouteMode) return
        serviceConnector.execute { addRoutePoint(RoutePoint(latLng)) }
        _routeState.update { it.copy(routePoints = serviceConnector.query { getRoutePoints() } ?: emptyList()) }
    }

    fun removeLastRoutePoint() {
        serviceConnector.execute { removeLastRoutePoint() }
        _routeState.update { it.copy(routePoints = serviceConnector.query { getRoutePoints() } ?: emptyList()) }
    }

    /**
     * 删除指定位置的路线点
     * @param index 点的索引（从 0 开始）
     */
    fun removeRoutePointAt(index: Int) {
        serviceConnector.execute { removeRoutePointAt(index) }
        _routeState.update { it.copy(routePoints = serviceConnector.query { getRoutePoints() } ?: emptyList()) }
    }

    /**
     * 在指定位置插入路线点
     * @param index 插入位置
     * @param latLng 经纬度
     */
    fun insertRoutePointAt(index: Int, latLng: LatLng) {
        serviceConnector.execute { insertRoutePointAt(index, RoutePoint(latLng)) }
        _routeState.update { it.copy(routePoints = serviceConnector.query { getRoutePoints() } ?: emptyList()) }
    }

    fun clearRoute() {
        serviceConnector.execute { clearRoute() }
        _routeState.update { it.copy(routePoints = emptyList(), playbackState = RoutePlaybackState()) }
    }

    fun toggleRoutePlayback() {
        val state = serviceConnector.query { getRoutePlaybackState() } ?: RoutePlaybackState()
        if (state.isPlaying) {
            // 暂停：只暂停播放，不重置位置
            serviceConnector.execute { pauseRoute() }
        } else {
            // 播放/恢复
            val isFirstStart = !_simulationState.value.isSimulating && state.currentIndex == 0 && state.progress == 0f
            if (isFirstStart && _routeState.value.routePoints.size >= 2) {
                val firstPoint = _routeState.value.routePoints.first().latLng
                startRouteSimulation(firstPoint.latitude, firstPoint.longitude)
            }
            serviceConnector.execute { playRoute() }
        }
    }
    
    /**
     * ✅ 停止路线播放（重置进度和模拟状态）
     */
    fun stopRoutePlayback() {
        serviceConnector.execute { stopRoutePlayback() }
        _simulationState.update { it.copy(isSimulating = false) }
        Timber.d("Route playback stopped and simulation state reset")
    }
    
    /**
     * ✅ 显示路线控制悬浮窗（用于后台控制）
     */
    fun showRouteControlIfNeeded() {
        if (_routeState.value.isRouteMode && _routeState.value.playbackState.isPlaying) {
            serviceConnector.execute { showRouteControlWindow() }
            Timber.d("Showing route control window for background control")
        }
    }
    
    /**
     * ✅ 隐藏路线控制悬浮窗（回到前台时）
     */
    fun hideRouteControlIfNeeded() {
        if (_routeState.value.isRouteMode) {
            serviceConnector.execute { hideRouteControlWindow() }
            Timber.d("Hiding route control window when returning to foreground")
        }
    }
    
    /**
     * ✅ 显示摇杆悬浮窗（进入后台时，单点模式）
     */
    private fun showJoystickIfNeeded() {
        // 只在单点模式且正在模拟时显示
        if (!_routeState.value.isRouteMode && _simulationState.value.isSimulating) {
            serviceConnector.execute { showFloatingWindow() }
            Timber.d("Showing joystick window for background control (single-point mode)")
        }
    }
    
    /**
     * ✅ 隐藏摇杆悬浮窗（回到前台时，单点模式）
     */
    private fun hideJoystickIfNeeded() {
        // 只在单点模式下隐藏
        if (!_routeState.value.isRouteMode) {
            serviceConnector.execute { hideFloatingWindow() }
            Timber.d("Hiding joystick window when returning to foreground (single-point mode)")
        }
    }

    private fun startRouteSimulation(latitude: Double, longitude: Double) {
        Timber.d("🚀 startRouteSimulation called: lat=$latitude, lng=$longitude")
        sendSimulationControlEvent(
            SimulationControlEvent(
                eventType = SimulationControlEvent.EventType.START_SIMULATION,
                latitude = latitude,
                longitude = longitude
            )
        )
        _simulationState.update { it.copy(isSimulating = true) }
    }

    fun setRouteLooping(loop: Boolean) {
        serviceConnector.execute { setRouteLooping(loop) }
    }

    fun setRouteSpeedMultiplier(multiplier: Float) {
        serviceConnector.execute { setRouteSpeedMultiplier(multiplier) }
    }
    
    /**
     * ✅ 切换移动模式（步行 → 跑步 → 骑车 → 步行）
     */
    fun toggleMovementMode() {
        val currentMode = _routeState.value.movementMode
        val nextMode = when (currentMode) {
            MovementMode.WALK -> MovementMode.RUN
            MovementMode.RUN -> MovementMode.BIKE
            MovementMode.BIKE -> MovementMode.WALK
        }
        _routeState.update { it.copy(movementMode = nextMode) }
        Timber.d("Movement mode changed to: $nextMode")
    }

    fun saveRouteToDb(name: String) {
        val points = serviceConnector.query { getRoutePoints() } ?: emptyList()
        if (points.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = routeRepository.saveRoute(name, points)
            if (result is AppResult.Error) {
                Timber.e(result.exception, "Failed to save route")
            }
        }
    }

    fun loadRouteFromDb(group: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = routeRepository.loadRoute(group)
            when (result) {
                is AppResult.Success -> {
                    val points = result.data
                    if (points.isEmpty()) return@launch
                    serviceConnector.execute { setRoute(points) }
                    _routeState.update {
                        it.copy(routePoints = points, isRouteMode = true)
                    }
                    Timber.d("Route loaded: $group, ${points.size} points")
                }
                is AppResult.Error -> Timber.e(result.exception, "Failed to load route")
            }
        }
    }

    /**
     * 获取所有路线组名称 — ✅ Phase 1: 通过 RouteRepository
     */
    suspend fun getSavedRouteGroups(): List<String> {
        return routeRepository.getAllGroups()
    }

    // ==================== Phase 1: Repository 代理方法（供 Fragment 调用） ====================

    /**
     * 逆地理编码 — 通过 SearchRepository，复用单例 PoiSearchHelper
     */
    fun reverseGeocode(lat: Double, lng: Double, callback: (name: String, address: String) -> Unit) {
        searchRepository.reverseGeocode(lat, lng, callback)
    }

    /**
     * 保存到历史记录 — 通过 LocationRepository
     */
    suspend fun saveToHistory(name: String, address: String, latitude: Double, longitude: Double): AppResult<Int> {
        return locationRepository.saveToHistory(name, address, latitude, longitude)
    }

    /**
     * 添加到收藏 — 通过 LocationRepository
     * @return true=新增成功, false=已存在
     */
    suspend fun addToFavorite(name: String, address: String, latitude: Double, longitude: Double): Boolean {
        return locationRepository.addToFavorite(name, address, latitude, longitude)
            .getOrDefault(false)
    }

    /**
     * 检查是否已收藏 — 通过 LocationRepository
     */
    suspend fun isFavorite(latitude: Double, longitude: Double): Boolean {
        return locationRepository.isFavorite(latitude, longitude)
    }

    /**
     * 保存位置到历史记录（异步）
     * 使用高德逆地理编码获取地址名称，通过 LocationRepository 保存
     * @param latitude 纬度（GCJ-02）
     * @param longitude 经度（GCJ-02）
     */
    fun saveToHistoryAsync(latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (resolvedName, resolvedAddress) = suspendCancellableCoroutine<Pair<String, String>> { cont ->
                    reverseGeocode(latitude, longitude) { name, fullAddress ->
                        if (cont.isActive) cont.resume(Pair(name, fullAddress)) {}
                    }
                }

                val name = if (resolvedName.isNotEmpty()) {
                    resolvedName
                } else {
                    val latDir = if (latitude >= 0) "N" else "S"
                    val lngDir = if (longitude >= 0) "E" else "W"
                    String.format("%.4f°%s, %.4f°%s", Math.abs(latitude), latDir, Math.abs(longitude), lngDir)
                }

                val result = saveToHistory(name, resolvedAddress, latitude, longitude)
                when (result) {
                    is AppResult.Success -> Timber.d("Total history records (after cleanup): ${result.data}")
                    is AppResult.Error -> Timber.e(result.exception, "Failed to save to history")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save to history")
            }
        }
    }

    /**
     * 添加到收藏（异步）
     * 检查是否已收藏，未收藏则通过逆地理编码获取地址后保存
     * @param latitude 纬度（GCJ-02）
     * @param longitude 经度（GCJ-02）
     */
    fun addToFavoriteAsync(latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exists = isFavorite(latitude, longitude)
                if (exists) {
                    _favoriteResult.emit("该位置已在收藏中")
                    return@launch
                }

                val (name, fullAddress) = getAddressFromLocation(latitude, longitude)
                addToFavorite(name, fullAddress, latitude, longitude)
                _favoriteResult.emit("已添加到收藏")
            } catch (e: Exception) {
                Timber.e(e, "添加收藏失败")
                _favoriteResult.emit("添加收藏失败")
            }
        }
    }

    /**
     * 获取地址名称（使用高德逆地理编码）
     * @param latitude 纬度（GCJ-02）
     * @param longitude 经度（GCJ-02）
     * @return Pair(name, fullAddress)
     */
    internal suspend fun getAddressFromLocation(latitude: Double, longitude: Double): Pair<String, String> {
        return try {
            val (name, fullAddress) = suspendCancellableCoroutine<Pair<String, String>> { cont ->
                reverseGeocode(latitude, longitude) { n, addr ->
                    if (cont.isActive) cont.resume(Pair(n, addr)) {}
                }
            }
            val displayName = if (name.isNotEmpty()) name else String.format("%.4f, %.4f", latitude, longitude)
            val displayAddress = if (fullAddress.isNotEmpty()) fullAddress else displayName
            Pair(displayName, displayAddress)
        } catch (e: Exception) {
            Timber.w(e, "逆地理编码失败")
            val fallback = String.format("%.4f, %.4f", latitude, longitude)
            Pair(fallback, fallback)
        }
    }

    // ==================== 生命周期 ====================

    override fun onCleared() {
        super.onCleared()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null

        // ✅ 解绑 ServiceConnector
        serviceConnector.unbind()

        // ✅ 移除应用生命周期监听器，防止内存泄漏
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        Timber.d("Removed app lifecycle observer")

        Timber.d("MainViewModel cleared")
    }
}

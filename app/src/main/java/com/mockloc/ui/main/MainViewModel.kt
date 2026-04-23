package com.mockloc.ui.main

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.model.LatLng
import com.mockloc.repository.PoiSearchHelper
import com.mockloc.service.LocationService
import com.mockloc.util.MapUtils
import com.mockloc.util.PrefsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    // ==================== 依赖 ====================

    private var locationService: LocationService? = null
    private var locationClient: AMapLocationClient? = null
    private var poiSearchHelper: PoiSearchHelper? = null
    private val prefs: SharedPreferences = getApplication<Application>().getSharedPreferences(PrefsConfig.MAP_STATE, Application.MODE_PRIVATE)
    
    // 定位超时任务
    private var locationTimeoutJob: Job? = null
    
    companion object {
        private const val LOCATION_TIMEOUT_MS = 10000L  // 定位超时时间：10秒
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化搜索功能
     */
    fun initSearch() {
        poiSearchHelper = PoiSearchHelper(getApplication())
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
            // 无上次状态，显示中国地图概览
            val chinaCenter = LatLng(35.8617, 104.1954)
            _mapState.update { state ->
                state.copy(center = chinaCenter, zoom = 5f)
            }
            Timber.d("显示默认视图: 中国地图概览")
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
     */
    fun selectPosition(latLng: LatLng, moveCamera: Boolean = false) {
        Timber.d("selectPosition called: latLng=$latLng, moveCamera=$moveCamera")
        _mapState.update { state ->
            state.copy(
                markedPosition = latLng,
                isPositionPending = true,
                shouldMoveCamera = moveCamera
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
     * 设置 LocationService 引用
     */
    fun setLocationService(service: LocationService?) {
        locationService = service
    }

    data class SimulationControlEvent(
        val eventType: EventType,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val altitude: Float = 55.0f
    ) {
        enum class EventType {
            START_SIMULATION, STOP_SIMULATION, UPDATE_POSITION
        }
    }
    
    private val _simulationControlEvents = MutableStateFlow<SimulationControlEvent?>(null)
    val simulationControlEvents: StateFlow<SimulationControlEvent?> = _simulationControlEvents.asStateFlow()
    
    /**
     * 发送模拟控制事件
     */
    private fun sendSimulationControlEvent(event: SimulationControlEvent) {
        _simulationControlEvents.value = event
        // 清除事件，防止重复消费
        // 增加延迟时间到 500ms，确保 Fragment 有足够时间接收
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _simulationControlEvents.value = null
        }
    }
    
    /**
     * 清除待移动相机标志（使用后重置）
     */
    fun clearMoveCameraFlag() {
        _mapState.update { state ->
            state.copy(shouldMoveCamera = false)
        }
    }
    
    /**
     * 确认传送/启动模拟
     */
    fun confirmSimulation(altitude: Float = 55.0f): Boolean {
        val currentState = _mapState.value
        val simulationState = _simulationState.value
        
        Timber.d("confirmSimulation called: isSimulating=${simulationState.isSimulating}, markedPosition=${currentState.markedPosition}, isPositionPending=${currentState.isPositionPending}")
            
        return if (simulationState.isSimulating) {
            if (currentState.isPositionPending && currentState.markedPosition != null) {
                // 模拟中且有新位置：发送更新位置事件
                sendSimulationControlEvent(
                    SimulationControlEvent(
                        eventType = SimulationControlEvent.EventType.UPDATE_POSITION,
                        latitude = currentState.markedPosition!!.latitude,
                        longitude = currentState.markedPosition!!.longitude,
                        altitude = altitude
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
                Timber.d("Starting simulation at: ${'$'}{currentState.markedPosition}")
                sendSimulationControlEvent(
                    SimulationControlEvent(
                        eventType = SimulationControlEvent.EventType.START_SIMULATION,
                        latitude = currentState.markedPosition!!.latitude,
                        longitude = currentState.markedPosition!!.longitude,
                        altitude = altitude
                    )
                )
                    
                _simulationState.update { state ->
                    state.copy(isSimulating = true)
                }
                _mapState.update { state ->
                    state.copy(isPositionPending = false)
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
        locationService?.setSpeedMode(mode)
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
        
        poiSearchHelper?.searchPlace(query, { results ->
            _searchState.update { state ->
                state.copy(
                    query = query,
                    results = results,
                    isVisible = results.isNotEmpty(),
                    isLoading = false
                )
            }
        }, centerLat = centerLat, centerLng = centerLng)
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
     * 保存搜索历史（带去重）
     */
    private fun saveToSearchHistory(place: PoiSearchHelper.PlaceItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.mockloc.VirtualLocationApp.getDatabase()
                
                // 检查是否已存在相同坐标的记录
                val existing = db.searchHistoryDao().findByCoordinates(place.lat, place.lng)
                
                if (existing != null) {
                    // 已存在，更新时间戳
                    db.searchHistoryDao().updateTimestamp(existing.id, System.currentTimeMillis())
                    Timber.d("Updated search history timestamp: ${place.name}")
                } else {
                    // 不存在，插入新记录
                    val searchHistory = com.mockloc.data.db.SearchHistory(
                        keyword = place.name,  // 使用地点名称作为关键词
                        name = place.name,
                        address = place.address,
                        latitude = place.lat,
                        longitude = place.lng
                    )
                    
                    db.searchHistoryDao().insert(searchHistory)
                    Timber.d("Saved to search history: ${place.name}")
                    
                    // 限制最多100条记录
                    db.searchHistoryDao().limitRecords()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save search history")
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

    // ==================== 生命周期 ====================

    override fun onCleared() {
        super.onCleared()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
        Timber.d("MainViewModel cleared")
    }
}

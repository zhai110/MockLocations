package com.mockloc.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import com.mockloc.service.LocationService
import com.mockloc.ui.favorite.FavoriteActivity
import com.mockloc.ui.history.HistoryActivity
import com.mockloc.ui.settings.SettingsActivity
import com.mockloc.util.AnimationConfig
import com.mockloc.util.AnimationHelper
import com.mockloc.util.OnboardingManager
import com.mockloc.util.PermissionHelper
import com.mockloc.util.UIFeedbackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 主界面 Fragment，应用的唯一入口页面。
 *
 * 通过 4 个 Delegate 分离职责：
 * - [SearchDelegate]：搜索栏、搜索结果列表
 * - [SimulationDelegate]：模拟状态 UI、FAB 动画、速度选择
 * - [RouteEditDelegate]：路线点编辑、路线绘制
 * - [ThemeDelegate]：夜间模式、地图类型切换
 *
 * Delegate 间不直接引用，通过 [MainViewModel] 的 StateFlow 中转通信。
 *
 * 坐标系约定：
 * - 高德地图使用 GCJ-02 坐标系（地图显示、标记、点击回调）
 * - Mock Location 注入使用 WGS-84 坐标系（传给 LocationService 时自动转换）
 * - 用户输入支持 WGS-84 / BD-09 / GCJ-02，内部统一转为 GCJ-02
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var aMap: AMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    
    private var currentMarker: Marker? = null
    private var hasFirstLocation = false
    private var isMapDragging = false  // 标记是否正在拖动地图
    private var pendingPositionFromResult = false  // launcher 回调设置了新位置，onResume 不应覆盖
    
    private var currentTabMode: Int = 0  // 0=单点定位, 1=路线模拟
    
    // ✅ Phase 2: Delegate 成员变量
    private lateinit var searchDelegate: com.mockloc.ui.main.delegate.SearchDelegate
    private lateinit var simulationDelegate: com.mockloc.ui.main.delegate.SimulationDelegate
    private lateinit var routeEditDelegate: com.mockloc.ui.main.delegate.RouteEditDelegate
    private lateinit var themeDelegate: com.mockloc.ui.main.delegate.ThemeDelegate

    // 路线点编辑
    private val historyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val name = data.getStringExtra("name") ?: ""
                if (latitude != 0.0 && longitude != 0.0) {
                    pendingPositionFromResult = true
                    val latLng = LatLng(latitude, longitude)
                    viewModel.selectPosition(latLng, moveCamera = true, clearAddress = false)
                    updateLocationInfo(latLng, name)
                }
            }
        }
    }

    // 路线点编辑
    private val favoriteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val name = data.getStringExtra("name") ?: ""
                if (latitude != 0.0 && longitude != 0.0) {
                    pendingPositionFromResult = true
                    val latLng = LatLng(latitude, longitude)
                    viewModel.selectPosition(latLng, moveCamera = true, clearAddress = false)
                    updateLocationInfo(latLng, name)
                }
            }
        }
    }

    // 路线点编辑
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Location permission granted")
            viewModel.initLocation()
        } else {
                // 路线模式：播放/暂停路线
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            
            if (shouldShowRationale) {
                // 用户之前拒绝过，但未选择"不再询问"→ 显示解释对话框

                showPermissionRationaleDialog()
            } else {
            // ✅ 切换到路线模式Tab
                showPermissionDeniedDialog()
            }
        }
    }

    /**
     * 加载布局并初始化 ViewBinding。
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建后的初始化入口。
     *
     * 初始化顺序：
     * 1. 设置沉浸式布局和状态栏偏移
     * 2. 初始化 4 个 Delegate（**必须在 [initMap] 之前**，因为 initMap 会使用 themeDelegate）
     * 3. 初始化地图、底部面板、Tab、底部导航、点击事件
     * 4. 检查权限、观察 ViewModel、恢复地图状态
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置沉浸式布局（需要在 Fragment 中通过 Activity 设置）
        try {
            @Suppress("DEPRECATION")  // setDecorFitsSystemWindows 在 Android 15+ 已弃用，但当前实现仍然有效
            requireActivity().window.setDecorFitsSystemWindows(false)
            requireActivity().window.insetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set immersive layout")
        }

        // 获取状态栏高度并调整搜索栏位置
        try {
            val statusBarHeight = getStatusBarHeight()
            if (statusBarHeight > 0) {
                (binding.searchCard.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { params ->
                    params.topMargin = statusBarHeight + 16.dpToPx()
                    binding.searchCard.layoutParams = params
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to adjust search card position")
        }

        // ✅ Phase 2: Delegate 成员变量 (必须在 initMap() 之前初始化，因为 initMap 会使用 themeDelegate)
        searchDelegate = com.mockloc.ui.main.delegate.SearchDelegate(this, viewModel, binding)
        searchDelegate.onGetSearchCenter = { if (::aMap.isInitialized) aMap.cameraPosition.target else null }
        searchDelegate.init()
        
        simulationDelegate = com.mockloc.ui.main.delegate.SimulationDelegate(this, viewModel, binding)
        simulationDelegate.onPermissionCheckNeeded = { checkPermissions() }
        
        routeEditDelegate = com.mockloc.ui.main.delegate.RouteEditDelegate(this, viewModel, binding)
        routeEditDelegate.onGetAMap = { if (::aMap.isInitialized) aMap else null }
        
        themeDelegate = com.mockloc.ui.main.delegate.ThemeDelegate(this, binding)
        themeDelegate.init()

    // 路线点编辑
        try {
            binding.mapView.onCreate(savedInstanceState)
            initMap()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize map")
            UIFeedbackHelper.showToast(requireContext(), "地图初始化失败")
        }

        // 初始化BottomSheet
        initBottomSheet()

        // 初始化Tab切换
        initPanelTabs()

    // 路线点编辑
        initBottomNavigation()

        // 设置点击事件
        setupClickListeners()

    // 路线点编辑
        checkPermissions()

        // 初始化FAB图标（脉冲动画由 SimulationDelegate 管理）

        binding.fab.setImageResource(R.drawable.ic_position)
        binding.fab.imageTintList = null

        // 观察 ViewModel 状态
        observeViewModel()

    // 路线点编辑
        restoreMapState()
    }

    /**
     * 更新夜间模式状态（委托给 ThemeDelegate）
     * 检测日夜模式变化 → 更新地图类型 → 更新所有 View 颜色
     */
    private fun updateNightModeStatus() {
        if (!::aMap.isInitialized) {
            Timber.d("Map not initialized yet, skip night mode update")
            return
        }
        val themedContext = com.mockloc.util.ThemeUtils.createThemedContext(requireContext()).first
        themeDelegate.handleThemeUpdate(aMap, themedContext)
    }

    /**
     * 观察 ViewModel 的 StateFlow，将状态变化委托给各 Delegate 处理。
     *
     * 观察的状态流：
     * - [MainViewModel.mapState] → [updateMapUI]
     * - [MainViewModel.searchState] → [updateSearchUI]
     * - [MainViewModel.simulationState] → [SimulationDelegate.updateSimulationUI]
     * - [MainViewModel.bottomSheetState] → [updateBottomSheetUI]
     * - [MainViewModel.routeState] → [RouteEditDelegate.updateRouteUI]
     * - [MainViewModel.simulationControlEvents] → [handleSimulationControlEvent]
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察地图状态
                launch {
                    viewModel.mapState.collect { state ->
                        updateMapUI(state)
                    }
                }

    // 路线点编辑
                launch {
                    viewModel.searchState.collect { state ->
                        updateSearchUI(state)
                    }
                }

                launch {
                    viewModel.simulationState.collect { state ->
                        simulationDelegate.updateSimulationUI(state, currentTabMode)
                    }
                }

                launch {
                    viewModel.bottomSheetState.collect { state ->
                        updateBottomSheetUI(state)
                    }
                }

                launch {
                    viewModel.routeState.collect { state ->
                        routeEditDelegate.updateRouteUI(state)
                    }
                }
                
                // 观察模拟控制事件
                launch {
                    viewModel.simulationControlEvents.collect { event ->
                        handleSimulationControlEvent(event)
                    }
                }
            }
        }
    }
    
    /**
     * 处理模拟控制事件，由 [MainViewModel.simulationControlEvents] 触发。
     *
     * 事件类型：
     * - START_SIMULATION → [startSimulation]：启动位置模拟
     * - STOP_SIMULATION → [stopSimulation]：停止位置模拟
     * - UPDATE_POSITION → [teleportToPosition] + [saveToHistory]：模拟中手动传送
     */
    private fun handleSimulationControlEvent(event: MainViewModel.SimulationControlEvent) {
        Timber.d("🔔 handleSimulationControlEvent: eventType=${event.eventType}, lat=${event.latitude}, lng=${event.longitude}")
        
        when (event.eventType) {
            MainViewModel.SimulationControlEvent.EventType.START_SIMULATION -> {
                Timber.d("🚀 Calling startSimulation...")
                startSimulation(event.latitude!!, event.longitude!!, event.altitude)
            }
            MainViewModel.SimulationControlEvent.EventType.STOP_SIMULATION -> {
                Timber.d("⏹️ Calling stopSimulation...")
                stopSimulation()
            }
            MainViewModel.SimulationControlEvent.EventType.UPDATE_POSITION -> {
                Timber.d("📍 Calling teleportToPosition (manual teleport)...")
                // ✅ 修复：模拟中手动传送，坐标来自地图（GCJ-02），需要转换
                teleportToPosition(event.latitude!!, event.longitude!!, event.altitude)
                
                // ✅ 修复：只有主动传送（Teleport）时才保存历史记录
                saveToHistory(event.latitude, event.longitude)
            }
        }
    }
    
    /**
     * 启动位置模拟。
     *
     * 通过 Intent 与 [LocationService] 通信，坐标为 GCJ-02，
     * Service 内部自动转换为 WGS-84 后注入 Mock Location。
     * 同时保存位置到历史记录，并更新地图标记。
     */
    private fun startSimulation(latitude: Double, longitude: Double, altitude: Float) {
        try {
        // 可以在这里更新底部面板的展开/收起状态
            saveToHistory(latitude, longitude)
            
            Timber.d("🚀 Starting simulation: lat=$latitude, lng=$longitude (GCJ-02)")
            
            val intent = android.content.Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_START
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                // ✅ 修复：所有传给Service的坐标都是GCJ-02，需要转换为WGS-84
                putExtra(LocationService.EXTRA_COORD_GCJ02, true)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().startService(intent)
            }
            
    // 路线点编辑
            val targetLatLng = com.amap.api.maps.model.LatLng(latitude, longitude)
            updateMarker(targetLatLng, moveCamera = true)
            
        // 监听输入框变化，动态显示/隐藏清除按钮
            val wgs84 = com.mockloc.util.MapUtils.gcj02ToWgs84(longitude, latitude)
            Timber.d("📍 Red marker (GCJ-02): ($latitude, $longitude)")
            Timber.d("📍 Blue dot expected (WGS-84 injected): (${wgs84[1]}, ${wgs84[0]})")
            Timber.d("📍 Blue dot will display as GCJ-02 by AMap: ($latitude, $longitude)")
            
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_simulation_started))
        } catch (e: Exception) {
            Timber.e(e, "启动模拟失败")
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_simulation_start_failed, e.message))
        }
    }
    
    /**
     * 保存位置到历史记录。
     *
     * 通过 ViewModel → Repository 链路保存，使用高德异步逆地理编码获取地址名称，
     * 避免原生 Geocoder 阻塞 IO 线程。
     */
    private fun saveToHistory(latitude: Double, longitude: Double) {
        Timber.d("saveToHistory called: lat=$latitude, lng=$longitude")
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ Phase 1: 通过 ViewModel 使用 SearchRepository 的单例 PoiSearchHelper
                val (resolvedName, resolvedAddress) = kotlinx.coroutines.suspendCancellableCoroutine<Pair<String, String>> { cont ->
                    viewModel.reverseGeocode(latitude, longitude) { name, fullAddress ->
                        if (cont.isActive) cont.resume(Pair(name, fullAddress)) {}
                    }
                }
                
                // ✅ 关键修复：检查 Fragment 是否仍处于活跃状态，防止回调悬空
                if (!isAdded || view == null) return@launch
                
                val name = if (resolvedName.isNotEmpty()) {
                    resolvedName
                } else {
                    val latDir = if (latitude >= 0) "N" else "S"
                    val lngDir = if (longitude >= 0) "E" else "W"
                    String.format("%.4f°%s, %.4f°%s", Math.abs(latitude), latDir, Math.abs(longitude), lngDir)
                }
                
                Timber.d("Saving to history via Repository: name='$name', address='$resolvedAddress'")
                
                // ✅ Phase 1: 通过 ViewModel → LocationRepository 保存
                val result = viewModel.saveToHistory(name, resolvedAddress, latitude, longitude)
                when (result) {
                    is com.mockloc.core.common.AppResult.Success ->
                        Timber.d("Total history records (after cleanup): ${result.data}")
                    is com.mockloc.core.common.AppResult.Error ->
                        Timber.e(result.exception, "Failed to save to history")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to save to history")
            }
        }
    }
    
    /**
     * 停止位置模拟，向 [LocationService] 发送停止指令。
     */
    private fun stopSimulation() {
        try {
            val intent = android.content.Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
            }
            requireContext().startService(intent)
            
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_simulation_stopped))
        } catch (e: Exception) {
            Timber.e(e, "停止模拟失败")
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_simulation_stop_failed, e.message))
        }
    }
    
    /**
     * 传送位置（单点模式）。
     *
     * 坐标来自地图点击（GCJ-02），传给 [LocationService] 时标记为 GCJ-02，
     * Service 内部自动转换为 WGS-84 后注入 Mock Location。
     */
    private fun teleportToPosition(latitude: Double, longitude: Double, altitude: Float) {
        try {
            val intent = android.content.Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_UPDATE
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                // ✅ 修复：手动传送的坐标是GCJ-02，需要转换为WGS-84
                putExtra(LocationService.EXTRA_COORD_GCJ02, true)
            }
            requireContext().startService(intent)
            
            // 移动相机到新位置
            val targetLatLng = com.amap.api.maps.model.LatLng(latitude, longitude)
            updateMarker(targetLatLng, moveCamera = true)
            
            UIFeedbackHelper.showToast(requireContext(), "地图初始化失败")
        } catch (e: Exception) {
            Timber.e(e, "启动模拟失败")
            UIFeedbackHelper.showToast(requireContext(), "传送失败: ${e.message}")
        }
    }

    /**
     * 更新模拟位置（摇杆/路线模式）。
     *
     * 坐标来自 LocationService 内部（已是 WGS-84），不需要再转换，
     * 传给 [LocationService] 时标记 EXTRA_COORD_GCJ02=false。
     */
    private fun updatePosition(latitude: Double, longitude: Double, altitude: Float) {
        try {
            val intent = android.content.Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_UPDATE
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                // ✅ 修复：摇杆移动的坐标来自LocationService内部（已是WGS-84），不需要再转换
                putExtra(LocationService.EXTRA_COORD_GCJ02, false)
            }
            requireContext().startService(intent)
            
    // 路线点编辑
            val targetLatLng = com.amap.api.maps.model.LatLng(latitude, longitude)
            updateMarker(targetLatLng, moveCamera = true)
            
            UIFeedbackHelper.showToast(requireContext(), "地图初始化失败")
        } catch (e: Exception) {
            Timber.e(e, "启动模拟失败")
            UIFeedbackHelper.showToast(requireContext(), "传送失败: ${e.message}")
        }
    }

    /**
     * 响应地图状态变化，更新标记和位置信息。
     *
     * 处理逻辑：
     * - shouldMoveToCurrentLocation → 动画移动相机到当前位置
     * - markedPosition 变化 → 更新标记位置
     * - currentLocation + address → 更新位置信息显示
     * - 非模拟状态 → 重置 FAB 图标
     */
    private fun updateMapUI(state: MainViewModel.MapState) {
        Timber.d("updateMapUI called: markedPosition=${state.markedPosition}, currentLocation=${state.currentLocation}, shouldMoveCamera=${state.shouldMoveCamera}, shouldMoveToCurrentLocation=${state.shouldMoveToCurrentLocation}")
        
    // 路线点编辑
        if (state.shouldMoveToCurrentLocation && state.currentLocation != null) {
            Timber.d("Moving camera to current location: ${state.currentLocation}, zoom: ${state.zoom}")
            // ✅ 使用 animateCamera 实现流畅的飞行动画
            aMap.animateCamera(
                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(state.currentLocation, state.zoom)
            )
            // 重置标志
            viewModel.resetShouldMoveToCurrentLocation()
        }
        
        // 更新标记（只在位置改变时更新，避免频繁 remove/add 导致地图跳动）
        state.markedPosition?.let { position ->
            val positionChanged = currentMarker == null || currentMarker!!.position != position
            if (positionChanged) {
                Timber.d("updateMapUI: updating marker at $position")
                updateMarker(position, moveCamera = state.shouldMoveCamera)
                viewModel.resetShouldMoveCamera()
            } else if (state.shouldMoveCamera) {
                // 标记位置未变但需要移动相机（如从历史记录/收藏返回同一位置）
                Timber.d("updateMapUI: marker position unchanged, but moving camera to $position")
                val currentZoom = aMap.cameraPosition.zoom
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, currentZoom))
                viewModel.resetShouldMoveCamera()
            } else {
                Timber.d("updateMapUI: marker position unchanged, skip update")
            }
        }

        // 更新位置信息（但不移动相机）
        if (state.currentLocation != null && state.address.isNotEmpty()) {
            Timber.d("updateMapUI: currentLocation updated to ${state.currentLocation}, but NOT moving camera")
            updateLocationInfo(state.currentLocation!!, state.address)
        }

        // 清除待传送标记后更新 FAB
        if (!state.isPositionPending && !viewModel.simulationState.value.isSimulating) {
            binding.fab.setImageResource(R.drawable.ic_position)
            binding.fab.imageTintList = null
            binding.statusText.text = "未模拟"
        }
    }

    /**
     * 委托给 [SearchDelegate] 处理搜索结果更新。
     */
    private fun updateSearchUI(state: MainViewModel.SearchState) {
        binding.searchProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        if (state.isVisible && state.results.isNotEmpty()) {
            searchDelegate.updateResults(state.results)
        } else {
            searchDelegate.hideSearchResults()
        }
        
        searchDelegate.updateClearButtonVisibility()
    }

    /**
     * 响应底部面板状态变化（预留扩展）。
     */
    private fun updateBottomSheetUI(state: MainViewModel.BottomSheetState) {
        // 可以在这里更新底部面板的展开/收起状态
    }

    /**
     * 初始化高德地图。
     *
     * 设置以下监听：
     * - 地图点击 → [onMapClick]
     * - 地图长按 → [onMapLongClick]
     * - 标记拖拽 → 更新选中位置
     * - 相机变化 → 保存地图状态、标记拖动状态
     *
     * 使用 [ThemeDelegate] 设置夜间模式地图类型。
     */
    private fun initMap() {
        // ✅ 防御性检查：确保 AMap 实例获取成功
        aMap = binding.mapView.map
        if (aMap == null) {
            Timber.e("❌ Failed to get AMap instance, map initialization failed")
            Toast.makeText(requireContext(), "地图初始化失败，请重启应用", Toast.LENGTH_LONG).show()
            return
        }
        Timber.d("✅ AMap instance obtained successfully")
        
    // ✅ Phase 2: Delegate 成员变量
        themeDelegate.updateMapTypeForNightMode(aMap)
        
        // 启用我的位置图层（使用 LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER）
        // 显示蓝点+方向箭头，但不自动移动相机
        aMap.myLocationStyle = com.amap.api.maps.model.MyLocationStyle().apply {
            myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            showMyLocation(true)
            // 设置精度圈颜色
            strokeColor(com.mockloc.R.color.primary)
            radiusFillColor(ContextCompat.getColor(requireContext(), R.color.location_accuracy_fill))
            strokeWidth(2f)
        }
        aMap.isMyLocationEnabled = true
        Timber.d("My location layer enabled with LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER")
        
        // 根据夜间模式设置地图类型
        themeDelegate.updateMapTypeForNightMode(aMap)
        
        // 设置地图点击监听
        aMap.setOnMapClickListener { latLng ->
            onMapClick(latLng)
        }
        
        // 设置地图长按监听
        aMap.setOnMapLongClickListener { latLng ->
            onMapLongClick(latLng)
        }
        
        // 设置标记拖拽监听
        aMap.setOnMarkerDragListener(object : AMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}
            
            override fun onMarkerDrag(marker: Marker) {}
            
            override fun onMarkerDragEnd(marker: Marker) {
                val position = marker.position
                // 拖拽标记结束时更新位置，不移动相机
                viewModel.selectPosition(position, moveCamera = false)
                updateLocationInfo(position)
            }
        })
        
        // 设置相机变化监听
        aMap.addOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(cameraPosition: com.amap.api.maps.model.CameraPosition) {
                // 相机变化中，标记为拖动状态
                isMapDragging = true
                Timber.d("onCameraChange: isMapDragging = true, target=${cameraPosition.target}")
            }
            
            override fun onCameraChangeFinish(cameraPosition: com.amap.api.maps.model.CameraPosition) {
                // 相机变化完成，保存状态（包括最新的缩放级别）
                viewModel.saveMapState(
                    center = cameraPosition.target,
                    zoom = cameraPosition.zoom
                )
                
                Timber.d("onCameraChangeFinish: target=${cameraPosition.target}, zoom=${cameraPosition.zoom}, will reset isMapDragging after 300ms")
                // 延迟重置拖动标记，避免立即触发的点击事件（增加到 300ms）
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    isMapDragging = false
                    Timber.d("isMapDragging reset to false")
                }
            }
        })
    }

    /**
     * 初始化底部面板行为，设置 peekHeight、滑动回调和 FAB 联动。
     */
    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.apply {
            peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
            isHideable = false
            state = BottomSheetBehavior.STATE_EXPANDED
            
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    Timber.d("BottomSheet state: $newState")
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    applyBottomSheetParallax(slideOffset)
                    
                    // FAB跟随BottomSheet滑动
                    val fabTranslation = resources.getDimension(R.dimen.fab_translation_y)
                    val fabTranslationY = -fabTranslation * (1f - slideOffset.coerceIn(0f, 1f))
                    binding.fab.translationY = fabTranslationY
                }
            })
        }
    }

    /**
     * 底部面板视差效果，根据滑动偏移量调整位置信息卡片透明度。
     */
    private fun applyBottomSheetParallax(slideOffset: Float) {
        val normalizedOffset = if (slideOffset < 0) 0f else slideOffset
        
    // 路线点编辑
        binding.locationInfoCard?.alpha = 0.7f + normalizedOffset * 0.3f
    }

    /**
     * 初始化面板 Tab（单点/路线），切换时更新按钮和 ViewModel 状态。
     */
    private fun initPanelTabs() {
        // ✅ 禁用 TabLayout 的所有动画（防止夜间模式闪烁）
        binding.panelTabs.setScrollPosition(0, 0f, false)
        
        // 初始化Tab切换
        for (i in 0 until binding.panelTabs.tabCount) {
            val tab = binding.panelTabs.getTabAt(i)
            tab?.view?.setBackgroundResource(android.R.color.transparent)
        }
        
        binding.panelTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // ✅ 清除选中项的背景（防止夜间模式闪烁）
                tab?.view?.setBackgroundResource(android.R.color.transparent)
                
                when (tab?.position) {
                    0 -> {
                        showPointModeButtons()
                        viewModel.setRouteMode(false)  // ✅ 切换到单点模式
                    }
                    1 -> {
                        showRouteModeButtons()
                        viewModel.setRouteMode(true)   // ✅ 切换到路线模式
                    }
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // ✅ 清除未选中项的背景
                tab?.view?.setBackgroundResource(android.R.color.transparent)
            }
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        // 默认显示单点模式按钮
        showPointModeButtons()
    }

    /**
     * 显示单点模式按钮，隐藏路线控制面板，FAB 显示定位图标。
     */
    private fun showPointModeButtons() {
        currentTabMode = 0
        binding.pointActionButtons.visibility = android.view.View.VISIBLE
        
    // 路线点编辑
        binding.routeControlCard.visibility = android.view.View.GONE
        
        // 初始化BottomSheet
        binding.routePanel.visibility = android.view.View.GONE
        
        // FAB显示定位图标
        binding.fab.setImageResource(R.drawable.ic_position)
        binding.fab.imageTintList = null
        
        Timber.d("Switched to point mode buttons")
    }

    /**
     * 显示路线模式控制面板，隐藏单点按钮，FAB 保持定位图标。
     */
    private fun showRouteModeButtons() {
        currentTabMode = 1
        binding.pointActionButtons.visibility = android.view.View.GONE
        
    // 路线点编辑
        binding.routeControlCard.visibility = android.view.View.VISIBLE
        
        // ✅ 显示 BottomSheet 内的路线控制面板（与4个按钮同位置）
        binding.routePanel.visibility = android.view.View.VISIBLE
        
        // FAB保持定位图标
        binding.fab.setImageResource(R.drawable.ic_position)
        binding.fab.imageTintList = null
        
        Timber.d("Switched to route mode panel")
    }



    /**
     * 初始化底部导航栏，设置图标/文字颜色和页面跳转（地图/历史/收藏/设置）。
     */
    private fun initBottomNavigation() {
        // ✅ 关键修复：使用与主题切换时相同的方式获取颜色
        val resources = requireContext().resources
        val theme = requireContext().theme
        
        // ✅ 初始化选中态背景色（使用 nav_item_selected_background 颜色）
        try {
            val containerColor = resources.getColor(R.color.nav_item_selected_background, theme)
            binding.bottomNav.setItemActiveIndicatorColor(android.content.res.ColorStateList.valueOf(containerColor))
        } catch (e: Exception) {
            Timber.e(e, "Failed to set active indicator color")
        }
        
    // 路线点编辑
        try {
            val primaryColor = resources.getColor(R.color.primary, theme)
            val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)
            
            val navItemColorStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    primaryColor,
                    textSecondaryColor
                )
            )
            binding.bottomNav.itemIconTintList = navItemColorStateList
            binding.bottomNav.itemTextColor = navItemColorStateList
            
            Timber.d("✅ BottomNavigationView initialized: primary=#${Integer.toHexString(primaryColor)}, text_secondary=#${Integer.toHexString(textSecondaryColor)}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize BottomNavigationView colors")
        }
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    // 已在地图页，不需要做任何事
                    true
                }
                R.id.nav_history -> {
                    historyLauncher.launch(Intent(requireContext(), HistoryActivity::class.java))
                    applyActivityTransition()
                    true
                }
                R.id.nav_favorite -> {
                    favoriteLauncher.launch(Intent(requireContext(), FavoriteActivity::class.java))
                    applyActivityTransition()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    applyActivityTransition()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 设置所有按钮点击事件，作为事件路由表分发到各 Delegate。
     *
     * 包括：地图缩放、路线点编辑、定位、图层、坐标输入、历史、收藏、
     * FAB 模拟切换、路线播放/速度/停止、速度芯片、循环、移动模式、清除路线。
     */
    private fun setupClickListeners() {
        // ✅ Phase 2: 搜索相关监听器已迁移到 SearchDelegate，此处不再重复设置
        
        // 地图控制按钮
        binding.zoomInBtn.setOnClickListener {
            aMap.animateCamera(CameraUpdateFactory.zoomIn())
        }

        binding.zoomOutBtn.setOnClickListener {
            aMap.animateCamera(CameraUpdateFactory.zoomOut())
        }
        
    // 路线点编辑
        binding.btnDeleteRoutePoint.setOnClickListener {
            routeEditDelegate.deleteSelectedRoutePoint()
        }
        
        binding.btnCancelSelect.setOnClickListener {
            routeEditDelegate.hideRoutePointEditButtons()
        }

        binding.locationBtn.setOnClickListener {
            viewModel.mapState.value.currentLocation?.let { loc ->
            // ✅ 使用 animateCamera 实现流畅的飞行动画
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 15f))
            } ?: run {
                viewModel.initLocation()
            }
        }

        binding.layerBtn.setOnClickListener {
            showMapLayerDialog()
        }

        // 操作按钮
        binding.inputCoordsBtn.setOnClickListener {
            showInputCoordsDialog()
        }

        binding.historyBtn.setOnClickListener {
            historyLauncher.launch(Intent(requireContext(), HistoryActivity::class.java))
        }

        binding.favoriteBtn.setOnClickListener {
            addToFavorite()
        }

        binding.routeBtn.setOnClickListener {
        // 初始化Tab切换
            binding.panelTabs.getTabAt(1)?.select()
        }

        // FAB按钮
        binding.fab.setOnClickListener {
        // 初始化Tab切换
            if (currentTabMode == 0) {
                // 单点模式：启动/停止模拟
                toggleSimulation()
            } else {
            // ✅ 权限被拒绝，检查是否需要显示解释
                toggleRoutePlaybackFromFab()
            }
        }

        // 监听输入框变化，动态显示/隐藏清除按钮
        binding.routePlayFabBtn.setOnClickListener {
            if (viewModel.routeState.value.routePoints.size < 2) {
            UIFeedbackHelper.showToast(requireContext(), "地图初始化失败")
                return@setOnClickListener
            }
            viewModel.toggleRoutePlayback()
        }

        binding.routeSpeedFabBtn.setOnClickListener {
            // 循环切换速度：1x -> 2x -> 4x -> 0.5x -> 1x
            val currentSpeed = viewModel.routeState.value.playbackState.speedMultiplier
            val nextSpeed = when (currentSpeed) {
                1f -> 2f
                2f -> 4f
                4f -> 0.5f
                else -> 1f
            }
            viewModel.setRouteSpeedMultiplier(nextSpeed)
            UIFeedbackHelper.showToast(requireContext(), "速度: ${nextSpeed}x")
        }

        binding.routeStopFabBtn.setOnClickListener {
        // 显示蓝点+方向箭头，但不自动移动相机
            viewModel.stopRoutePlayback()
        }

        // ✅ 路线播放/暂停（由地图上方的 route_control_card 控制）
        // 初始化BottomSheet

        // 速度选择
        binding.speed05x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(0.5f)
            simulationDelegate.updateSpeedChipSelection(binding.speed05x)
        }
        binding.speed1x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(1f)
            simulationDelegate.updateSpeedChipSelection(binding.speed1x)
        }
        binding.speed2x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(2f)
            simulationDelegate.updateSpeedChipSelection(binding.speed2x)
        }
        binding.speed4x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(4f)
            simulationDelegate.updateSpeedChipSelection(binding.speed4x)
        }

        // 循环
        binding.routeLoopBtn.setOnClickListener {
            val isLooping = !viewModel.routeState.value.playbackState.isLooping
            viewModel.setRouteLooping(isLooping)
        }

    // 路线点编辑
        binding.routeMovementModeBtn.setOnClickListener {
            viewModel.toggleMovementMode()
        }

        // 清除路线
        binding.routeClearBtn.setOnClickListener {
            viewModel.clearRoute()
        }
    }

    /**
     * 地图点击回调，选择位置（GCJ-02 坐标）。
     *
     * 路线模式下自动添加路线点；单点模式下选中位置并更新逆地理编码。
     * 拖动地图后 300ms 内的点击会被忽略，防止误触。
     */
    private fun onMapClick(latLng: LatLng) {
        if (isMapDragging) {
            Timber.d("Map click ignored (after drag), isMapDragging=$isMapDragging")
            return
        }
        
    // 路线点编辑
        routeEditDelegate.hideRoutePointEditButtons()
        
        val routeState = viewModel.routeState.value
        if (routeState.isRouteMode && !routeState.playbackState.isPlaying) {
            viewModel.addRoutePoint(latLng)
            Timber.d("Route point added: ${latLng.latitude}, ${latLng.longitude}")
            return
        }
        
        Timber.d("Map clicked: ${latLng.latitude}, ${latLng.longitude}, isMapDragging=$isMapDragging")
        viewModel.hideSearchResults()
        viewModel.selectPosition(latLng, moveCamera = false)
        updateLocationInfo(latLng)
    }

    /**
     * 地图长按回调，选择位置并移动相机（委托给 [onMapClick]）。
     */
    private fun onMapLongClick(latLng: LatLng) {
        Timber.d("Map long clicked: ${latLng.latitude}, ${latLng.longitude}")
        // 长按也触发点击逻辑
        onMapClick(latLng)
    }

    /**
     * 更新地图标记位置。
     *
     * 已有标记时仅更新位置（避免 remove/add 导致跳动），首次创建时添加红色可拖拽标记。
     *
     * @param latLng 标记位置（GCJ-02）
     * @param moveCamera 是否动画移动相机到标记位置
     */
    private fun updateMarker(latLng: LatLng, moveCamera: Boolean = false) {
        Timber.d("updateMarker called: latLng=$latLng, moveCamera=$moveCamera, currentCenter=${aMap.cameraPosition.target}")
        
        if (currentMarker != null) {
            // 如果标记已存在，只更新位置（避免删除/重新添加导致的跳动）
            currentMarker!!.setPosition(latLng)
            Timber.d("Marker position updated using setPosition")
        } else {
            // 首次创建标记
            currentMarker?.remove()
            
            // 使用红色标记，更醒目
            val markerOptions = MarkerOptions()
                .position(latLng)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .anchor(0.5f, 1.0f)  // 锚点在底部中心
            
            currentMarker = aMap.addMarker(markerOptions)
            Timber.d("Marker created at: $latLng")
        }
        
        // 清除待传送标记后更新 FAB
        if (moveCamera) {
            val currentZoom = aMap.cameraPosition.zoom
            // ✅ 使用 animateCamera 实现流畅的飞行动画
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, currentZoom))
            Timber.d("Camera animated to marker position, zoom=$currentZoom")
        } else {
            Timber.d("Camera NOT moved (moveCamera=false), center after update: ${aMap.cameraPosition.target}")
        }
    }

    /**
     * 更新位置信息显示（经纬度 + 逆地理编码地址）。
     *
     * 有地址时直接显示，无地址时通过 ViewModel 异步逆地理编码获取。
     */
    private fun updateLocationInfo(latLng: LatLng, address: String = "") {
        AnimationHelper.animateNumberChange(
            binding.latitudeText,
            String.format("%.6f°", latLng.latitude)
        )
        AnimationHelper.animateNumberChange(
            binding.longitudeText,
            String.format("%.6f°", latLng.longitude)
        )
        
        // 获取地址信息
        if (address.isNotEmpty()) {
            AnimationHelper.fadeIn(binding.addressText, 200)
            binding.addressText.text = address
        } else {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                // ✅ Phase 1: 通过 ViewModel 使用 SearchRepository 的单例 PoiSearchHelper
                    val (_, fullAddress) = kotlinx.coroutines.suspendCancellableCoroutine<Pair<String, String>> { cont ->
                        viewModel.reverseGeocode(latLng.latitude, latLng.longitude) { name, addr ->
                            if (cont.isActive) cont.resume(Pair(name, addr)) {}
                        }
                    }
                    
                // ✅ 关键修复：检查 Fragment 是否仍处于活跃状态，防止回调悬空
                    if (!isAdded || view == null) return@launch
                    
                    withContext(Dispatchers.Main) {
                        if (_binding != null && fullAddress.isNotEmpty()) {
                            AnimationHelper.fadeIn(binding.addressText, 200)
                            binding.addressText.text = fullAddress
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "逆地理编码失败")
                }
            }
        }
    }

    /**
     * 切换模拟状态（委托给 [SimulationDelegate]），检查权限和标记位置后调用 ViewModel 确认。
     */
    private fun toggleSimulation() {
        // 检查定位权限
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
            return
        }
        
        val mapState = viewModel.mapState.value
        val simState = viewModel.simulationState.value
        
        // 未模拟时需要检查是否有标记位置
        if (!simState.isSimulating && mapState.markedPosition == null) {
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_please_select_location))
            return
        }

        // 调用 ViewModel 确认模拟
        viewModel.confirmSimulation()
    }

    /**
     * 从 FAB 切换路线播放/暂停（委托给 [SimulationDelegate]），路线点不足时提示。
     */
    private fun toggleRoutePlaybackFromFab() {
        val routeState = viewModel.routeState.value
        
        if (routeState.routePoints.size < 2) {
            UIFeedbackHelper.showToast(requireContext(), "地图初始化失败")
            return
        }
        
        // 调用 ViewModel 的 toggleRoutePlayback
        viewModel.toggleRoutePlayback()
    }

    /**
     * 添加到收藏，通过 ViewModel → Repository 链路保存。
     * 已收藏时提示，未选中位置时引导选择。
     */
    private fun addToFavorite() {
        viewModel.mapState.value.markedPosition?.let { location ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                // ✅ Phase 1: 通过 ViewModel → LocationRepository 保存
                    val exists = viewModel.isFavorite(location.latitude, location.longitude)
                    
                // ✅ 关键修复：检查 Fragment 是否仍处于活跃状态，防止回调悬空
                    if (!isAdded || view == null) return@launch
                    
                    if (exists) {
                        withContext(Dispatchers.Main) {
                            if (isAdded && view != null) {
                                UIFeedbackHelper.showToast(requireContext(), "该位置已在收藏中")
                            }
                        }
                    } else {
                        // ✅ Phase 1: 通过 ViewModel 的 reverseGeocode 复用单例 PoiSearchHelper
                        val (name, fullAddress) = getAddressFromLocation(location)
                        
                // ✅ 关键修复：检查 Fragment 是否仍处于活跃状态，防止回调悬空
                        if (!isAdded || view == null) return@launch
                        
                // ✅ Phase 1: 通过 ViewModel → LocationRepository 保存
                        viewModel.addToFavorite(name, fullAddress, location.latitude, location.longitude)
                        
                        withContext(Dispatchers.Main) {
                            if (isAdded && view != null) {
                                UIFeedbackHelper.showToast(requireContext(), "已添加到收藏")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "添加收藏失败")
                }
            }
        } ?: run {
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_please_select_location))
        }
    }

    /**
     * 获取地址名称（使用高德逆地理编码）— ✅ Phase 1: 通过 ViewModel + SearchRepository
     */
    private suspend fun getAddressFromLocation(latLng: LatLng): Pair<String, String> {
        return try {
            val (name, fullAddress) = kotlinx.coroutines.suspendCancellableCoroutine<Pair<String, String>> { cont ->
                viewModel.reverseGeocode(latLng.latitude, latLng.longitude) { n, addr ->
                    if (cont.isActive) cont.resume(Pair(n, addr)) {}
                }
            }
            val displayName = if (name.isNotEmpty()) name else String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            val displayAddress = if (fullAddress.isNotEmpty()) fullAddress else displayName
            Pair(displayName, displayAddress)
        } catch (e: Exception) {
                    Timber.w(e, "逆地理编码失败")
            val fallback = String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            Pair(fallback, fallback)
        }
    }

    /**
     * 显示手动输入坐标对话框，支持 WGS-84 / BD-09 / GCJ-09 输入。
     *
     * 输入的坐标自动转换为 GCJ-02 后选中位置并更新逆地理编码。
     */
    private fun showInputCoordsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input_coords, null)
        val latEdit = dialogView.findViewById<android.widget.EditText>(R.id.edit_latitude)
        val lngEdit = dialogView.findViewById<android.widget.EditText>(R.id.edit_longitude)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group_coordinate_system)
        
        // 使用 MaterialAlertDialogBuilder 自带圆角样式，去掉标题
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                try {
                    val lat = latEdit.text.toString().toDouble()
                    val lng = lngEdit.text.toString().toDouble()
                    
                    // 验证坐标范围
                    if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                        UIFeedbackHelper.showToast(requireContext(), "坐标超出有效范围")
                        return@setPositiveButton
                    }
                    
                    // 获取用户选择的坐标系
                    val selectedCoordType = when (radioGroup.checkedRadioButtonId) {
                        R.id.radio_gcj02 -> "GCJ02"
                        R.id.radio_wgs84 -> "WGS84"
                        R.id.radio_bd09 -> "BD09"
                        else -> "GCJ02"
                    }
                    
                // ✅ 修复：模拟中手动传送，坐标来自地图（GCJ-02），需要转换
                    val gcjLatLng = when (selectedCoordType) {
                        "GCJ02" -> com.amap.api.maps.model.LatLng(lat, lng)
                        "WGS84" -> {
                            val gcj = com.mockloc.util.MapUtils.wgs84ToGcj02(lng, lat)
                            com.amap.api.maps.model.LatLng(gcj[1], gcj[0])
                        }
                        "BD09" -> {
                            val gcj = com.mockloc.util.MapUtils.bd09ToGcj02(lng, lat)
                            com.amap.api.maps.model.LatLng(gcj[1], gcj[0])
                        }
                        else -> com.amap.api.maps.model.LatLng(lat, lng)
                    }
                    
    // 路线点编辑
                    viewModel.selectPosition(gcjLatLng, moveCamera = true)
                    updateLocationInfo(gcjLatLng)
                    
                    Timber.d("📍 Input coords: $lat, $lng ($selectedCoordType) -> GCJ02: ${gcjLatLng.latitude}, ${gcjLatLng.longitude}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse coordinates")
                    UIFeedbackHelper.showToast(requireContext(), "坐标格式错误")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示地图图层选择对话框（标准/卫星/夜间），选择后设置 [ThemeDelegate.isManualLayerSelected]。
     */
    private fun showMapLayerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_map_layers, null)
        
        val normalLayer = dialogView.findViewById<android.widget.LinearLayout>(R.id.layer_normal)
        val satelliteLayer = dialogView.findViewById<android.widget.LinearLayout>(R.id.layer_satellite)
        val nightLayer = dialogView.findViewById<android.widget.LinearLayout>(R.id.layer_night)
        
        val normalCheck = dialogView.findViewById<android.widget.ImageView>(R.id.check_normal)
        val satelliteCheck = dialogView.findViewById<android.widget.ImageView>(R.id.check_satellite)
        val nightCheck = dialogView.findViewById<android.widget.ImageView>(R.id.check_night)
        
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)
        
    // 路线点编辑
        fun updateSelection(selected: String) {
            normalCheck.visibility = if (selected == "normal") android.view.View.VISIBLE else android.view.View.GONE
            satelliteCheck.visibility = if (selected == "satellite") android.view.View.VISIBLE else android.view.View.GONE
            nightCheck.visibility = if (selected == "night") android.view.View.VISIBLE else android.view.View.GONE
        }
        
    // 路线点编辑
        updateSelection(when (aMap.mapType) {
            AMap.MAP_TYPE_NORMAL -> "normal"
            AMap.MAP_TYPE_SATELLITE -> "satellite"
            AMap.MAP_TYPE_NIGHT -> "night"
            else -> "normal"
        })
        
        normalLayer.setOnClickListener {
            aMap.mapType = AMap.MAP_TYPE_NORMAL
            themeDelegate.setManualLayerSelected(true)
            updateSelection("normal")
            dialog.dismiss()
        }
        
        satelliteLayer.setOnClickListener {
            aMap.mapType = AMap.MAP_TYPE_SATELLITE
            themeDelegate.setManualLayerSelected(true)
            updateSelection("satellite")
            dialog.dismiss()
        }
        
        nightLayer.setOnClickListener {
            aMap.mapType = AMap.MAP_TYPE_NIGHT
            themeDelegate.setManualLayerSelected(true)
            updateSelection("night")
            dialog.dismiss()
        }
        
        dialog.show()
    }

    /**
     * 检查定位权限，未授权时请求权限；已授权且无缓存位置时初始化定位。
     */
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // ✅ 权限被拒绝，检查是否需要显示解释
            val lastLocation = viewModel.mapState.value.currentLocation
            
            if (lastLocation == null) {
        // 显示蓝点+方向箭头，但不自动移动相机
                Timber.d("First launch or no cached location, auto-location enabled")
                viewModel.initLocation()
            } else {
                // 路线模式：播放/暂停路线
                Timber.d("Using cached location: $lastLocation")
            }
        }
    }

    /**
     * 显示权限解释对话框（用户之前拒绝过，但未选择“不再询问”）
     */
    private fun showPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setTitle("需要定位权限")
            .setMessage("虚拟定位功能需要访问您的位置信息。\n\n请允许定位权限以使用此功能。")
            .setPositiveButton("授予权限") { _, _ ->
                // 再次请求权限
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示权限被拒绝对话框（用户选择了“不再询问”）
     */
    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setTitle("需要定位权限")
            .setMessage("虚拟定位功能需要访问您的位置信息。\n\n请允许定位权限以使用此功能。")
            .setPositiveButton("确定") { _, _ ->
                // 打开应用设置页面
                PermissionHelper.openAppSettings(requireContext())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 恢复地图状态（相机位置和缩放级别），从 ViewModel 获取持久化的中心点。
     */
    private fun restoreMapState() {
        val center = viewModel.restoreMapState()
        center?.let {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, viewModel.mapState.value.zoom))
        }
    }

    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    /**
     * dp转px扩展函数
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEdit.windowToken, 0)
    }

    /**
     * 应用 Activity 过渡动画（兼容 API 34+）
     * API 34+ 废弃了 overridePendingTransition，改用 overrideActivityTransition
     */
    private fun applyActivityTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requireActivity().overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        } else {
            @Suppress("DEPRECATION")
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    // ==================== 生命周期方法 ====================

    /**
     * 恢复地图生命周期、恢复标记位置、更新夜间模式。
     *
     * 从 SharedPreferences 恢复相机位置（可能来自悬浮窗修改），
     * 如果 launcher 刚设置了新位置（来自历史/收藏），跳过恢复避免覆盖。
     */
    override fun onResume() {
        super.onResume()
        
        // 确保 binding 有效
        if (_binding != null) {
            binding.mapView.onResume()
            
            // ✅ 从 SP 恢复最新状态（可能来自悬浮窗的修改）
            // 但如果 launcher 刚设置了新位置，跳过相机恢复，避免覆盖
            if (!pendingPositionFromResult) {
                val prefs = requireContext().getSharedPreferences(
                    com.mockloc.util.PrefsConfig.MAP_STATE, 
                    android.content.Context.MODE_PRIVATE
                )
                val lat = prefs.getFloat(com.mockloc.util.PrefsConfig.MapState.KEY_LATITUDE, -1f)
                val lng = prefs.getFloat(com.mockloc.util.PrefsConfig.MapState.KEY_LONGITUDE, -1f)
                val zoom = prefs.getFloat(com.mockloc.util.PrefsConfig.MapState.KEY_ZOOM, 15f)
                
            // ✅ 从 SP 恢复最新状态（可能来自悬浮窗的修改）
                if (lat > 0 && lng > 0) {
                    val center = com.amap.api.maps.model.LatLng(lat.toDouble(), lng.toDouble())
                    aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(center, zoom))
                    Timber.d("onResume: 恢复地图状态 center=$center, zoom=$zoom")
                }
            }
            
            // 触发 ViewModel 状态更新（用于标记位置等）
            viewModel.restoreMapState()
            
            // 如果 launcher 刚设置了新位置（来自历史/收藏），跳过恢复标记位置，避免覆盖
            if (pendingPositionFromResult) {
                pendingPositionFromResult = false
                Timber.d("onResume: 跳过 restoreMarkedPosition（来自历史/收藏的新位置）")
            } else {
                viewModel.restoreMarkedPosition()
            }
            
    // 路线点编辑
            updateNightModeStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    /**
     * 清理地图监听、Delegate 动画资源、MapView。
     *
     * mapView.onDestroy() 必须在 binding 置 null 之前调用，
     * 因为 onDestroy() 在 onDestroyView() 之后执行时 binding 已为 null。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        
    // 路线点编辑
        aMap.setOnMapClickListener(null)
        aMap.setOnMapLongClickListener(null)
        aMap.setOnMarkerDragListener(null)
        
        // 清理动画资源
        simulationDelegate.cleanup()
        binding.fab.clearAnimation()
        
        // mapView.onDestroy() 必须在 binding 置 null 之前调用
        // 因为 onDestroy() 在 onDestroyView() 之后执行，此时 binding 已为 null
        binding.mapView.onDestroy()
        
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // mapView 的 onDestroy 已在 onDestroyView 中调用
        // 此处 _binding 已为 null，不再访问 binding
    }

    /**
     * 处理配置变化（夜间模式切换）
     * configChanges="uiMode" 阻止了 Activity 重建，需要手动更新所有 View 颜色
     * 使用 newConfig 创建 themedContext，确保 Resources 从正确的目录加载
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val isNight = (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        Timber.d("MainFragment configuration changed: isNight=$isNight")

        // 使用 newConfig 创建 themedContext，委托给 ThemeDelegate 完成全部主题更新
        val newConfigContext = requireContext().createConfigurationContext(newConfig)
        val themedContext = com.mockloc.util.ThemeUtils.createThemedContext(newConfigContext).first
        themeDelegate.handleThemeUpdate(if (::aMap.isInitialized) aMap else null, themedContext)
    }
}

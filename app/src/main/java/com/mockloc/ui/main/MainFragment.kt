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
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.mockloc.repository.PoiSearchHelper
import com.mockloc.service.LocationService
import com.mockloc.ui.favorite.FavoriteActivity
import com.mockloc.ui.history.HistoryActivity
import com.mockloc.ui.search.SearchResultAdapter
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
 * 主界面Fragment
 * 
 * 职责：
 * - 初始化地图（AMap、标记、相机）
 * - 管理搜索 UI（EditText、RecyclerView）
 * - 管理 BottomSheet（BottomSheetBehavior）
 * - 管理 FAB 动画
 * - 观察 ViewModel 状态并更新 UI
 * - 响应用户交互（点击、拖拽、搜索）
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var aMap: AMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var searchAdapter: SearchResultAdapter
    
    private var currentMarker: Marker? = null
    private var isSearchResultVisible = false
    private var idlePulseAnimator: android.animation.ObjectAnimator? = null
    private var hasFirstLocation = false
    private var isMapDragging = false  // 标记是否正在拖动地图
    private var pendingPositionFromResult = false  // launcher 回调设置了新位置，onResume 不应覆盖
    private var isNightMode = false  // ✅ 初始值会在 onViewCreated 中根据系统主题正确设置
    private var isManualLayerSelected = false  // 标记用户是否手动选择了图层
    
    // ✅ 搜索框文本监听器（用于清除时临时移除）
    private val searchTextWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            updateClearButtonVisibility()
        }
    }

    // 历史记录结果启动器
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

    // 收藏结果启动器
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

    // 定位权限请求启动器
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Location permission granted")
            viewModel.initLocation()
        } else {
            // ✅ 权限被拒绝，检查是否需要显示解释
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            
            if (shouldShowRationale) {
                // 用户之前拒绝过，但未选择“不再询问” → 显示解释对话框
                showPermissionRationaleDialog()
            } else {
                // 用户选择了“不再询问”或首次拒绝 → 引导去设置页面
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

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

        // 初始化地图
        try {
            binding.mapView.onCreate(savedInstanceState)
            initMap()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize map")
            UIFeedbackHelper.showToast(requireContext(), "地图初始化失败")
        }

        // 初始化搜索
        viewModel.initSearch()
        initSearch()

        // 初始化BottomSheet
        initBottomSheet()

        // 初始化底部导航
        initBottomNavigation()

        // 设置点击事件
        setupClickListeners()

        // 检查权限
        checkPermissions()

        // 初始化FAB图标 + 慢速脉冲动画
        binding.fab.setImageResource(R.drawable.ic_position)
        binding.fab.imageTintList = null
        idlePulseAnimator = AnimationHelper.pulseInfinite(binding.fab, 2000)

        // 观察 ViewModel 状态
        observeViewModel()

        // 恢复地图状态
        restoreMapState()
    }

    /**
     * 更新夜间模式状态
     */
    private fun updateNightModeStatus() {
        // 确保 View 已初始化
        if (!::aMap.isInitialized) {
            Timber.d("Map not initialized yet, skip night mode update")
            return
        }
        
        try {
            val currentNightMode = (resources.configuration.uiMode 
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            if (isNightMode != currentNightMode) {
                isNightMode = currentNightMode
                Timber.d("Night mode changed: $isNightMode")
                
                // 更新地图类型
                updateMapTypeForNightMode()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update night mode status")
        }
    }

    /**
     * 根据夜间模式更新地图类型
     */
    private fun updateMapTypeForNightMode() {
        // 如果用户手动选择了图层，则不自动切换
        if (isManualLayerSelected) {
            Timber.d("User manually selected layer, skip auto-switch")
            return
        }
        
        // 根据夜间模式设置默认地图类型
        val targetMapType = if (isNightMode) {
            AMap.MAP_TYPE_NIGHT
        } else {
            AMap.MAP_TYPE_NORMAL
        }
        
        if (aMap.mapType != targetMapType) {
            aMap.mapType = targetMapType
            Timber.d("Map type updated to: ${if (isNightMode) "NIGHT" else "NORMAL"}")
        }
    }

    /**
     * 观察 ViewModel 状态变化
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

                // 观察搜索状态
                launch {
                    viewModel.searchState.collect { state ->
                        updateSearchUI(state)
                    }
                }

                // 观察模拟状态
                launch {
                    viewModel.simulationState.collect { state ->
                        updateSimulationUI(state)
                    }
                }

                // 观察底部面板状态
                launch {
                    viewModel.bottomSheetState.collect { state ->
                        updateBottomSheetUI(state)
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
     * 处理模拟控制事件
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
     * 启动模拟
     */
    private fun startSimulation(latitude: Double, longitude: Double, altitude: Float) {
        try {
            // ✅ 保存到历史记录
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
            
            // 移动相机到目标位置（用户确认传送时）
            val targetLatLng = com.amap.api.maps.model.LatLng(latitude, longitude)
            updateMarker(targetLatLng, moveCamera = true)
            
            // ✅ 调试：打印红色标记和蓝色蓝点的预期位置
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
     * 保存位置到历史记录
     * ✅ 修复：使用高德异步逆地理编码，避免原生 Geocoder 阻塞 IO 线程
     */
    private fun saveToHistory(latitude: Double, longitude: Double) {
        Timber.d("saveToHistory called: lat=$latitude, lng=$longitude")
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = com.mockloc.VirtualLocationApp.getDatabase()
                
                // ✅ 优化：使用 PoiSearchHelper 进行异步逆地理编码
                val helper = com.mockloc.repository.PoiSearchHelper(requireContext())
                var address = ""
                
                // 使用 suspendCancellableCoroutine 将回调转为协程
                kotlinx.coroutines.suspendCancellableCoroutine<String> { cont ->
                    helper.latLngToAddress(latitude, longitude) { result ->
                        if (cont.isActive) cont.resume(result) {}
                    }
                }.let { resolvedAddress ->
                    address = resolvedAddress
                }
                
                Timber.d("Fresh address resolved: '$address'")
                
                val name = if (address.isNotEmpty() && !address.contains("°N")) {
                    // 使用地址的第一行作为名称
                    address.split(",").firstOrNull()?.trim() ?: "未知位置"
                } else {
                    // ✅ 优化：添加方向标识，使坐标显示更友好 (例如: 39.9042°N, 116.4074°E)
                    val latDir = if (latitude >= 0) "N" else "S"
                    val lngDir = if (longitude >= 0) "E" else "W"
                    String.format("%.4f°%s, %.4f°%s", Math.abs(latitude), latDir, Math.abs(longitude), lngDir)
                }
                
                Timber.d("Creating HistoryLocation: name='$name', address='$address'")
                
                val historyLocation = com.mockloc.data.db.HistoryLocation(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )
                
                Timber.d("Inserting into database...")
                
                // ✅ 优化：检查是否与上一条历史记录坐标相同，避免重复
                val allRecords = db.historyLocationDao().getAll()
                val lastRecord = allRecords.firstOrNull()
                
                if (lastRecord != null && 
                    lastRecord.latitude == latitude && 
                    lastRecord.longitude == longitude) {
                    // 坐标相同，只更新时间戳和名称
                    val updatedRecord = lastRecord.copy(
                        name = name,
                        address = address,
                        timestamp = System.currentTimeMillis()
                    )
                    db.historyLocationDao().update(updatedRecord)
                    Timber.d("✅ Updated last history record timestamp: $name")
                } else {
                    // 坐标不同，插入新记录
                    db.historyLocationDao().insert(historyLocation)
                    Timber.d("✅ Inserted new history record: $name")
                }
                
                // ✅ 新增：自动清理旧数据，只保留最近 100 条
                db.historyLocationDao().keepRecentRecords(100)
                
                val finalCount = db.historyLocationDao().getAll().size
                Timber.d("Total history records in DB (after cleanup): $finalCount")
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to save to history")
            }
        }
    }
    
    /**
     * 停止模拟
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
     * 传送到新位置（手动传送，坐标来自地图GCJ-02）
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
            
            UIFeedbackHelper.showToast(requireContext(), "已传送到新位置")
        } catch (e: Exception) {
            Timber.e(e, "传送位置失败")
            UIFeedbackHelper.showToast(requireContext(), "传送失败: ${e.message}")
        }
    }

    /**
     * 更新位置（摇杆移动，坐标来自Service内部WGS-84）
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
            
            // 移动相机到新位置（用户确认传送时）
            val targetLatLng = com.amap.api.maps.model.LatLng(latitude, longitude)
            updateMarker(targetLatLng, moveCamera = true)
            
            UIFeedbackHelper.showToast(requireContext(), "已传送到新位置")
        } catch (e: Exception) {
            Timber.e(e, "传送位置失败")
            UIFeedbackHelper.showToast(requireContext(), "传送失败: ${e.message}")
        }
    }

    /**
     * 更新地图 UI
     */
    private fun updateMapUI(state: MainViewModel.MapState) {
        Timber.d("updateMapUI called: markedPosition=${state.markedPosition}, currentLocation=${state.currentLocation}, shouldMoveCamera=${state.shouldMoveCamera}, shouldMoveToCurrentLocation=${state.shouldMoveToCurrentLocation}")
        
        // ✅ 处理自动定位后移动相机到当前位置
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
     * 更新搜索 UI
     */
    private fun updateSearchUI(state: MainViewModel.SearchState) {
        // 更新加载指示器
        binding.searchProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        if (state.isVisible && state.results.isNotEmpty()) {
            searchAdapter.submitList(state.results)
            showSearchResults()
        } else {
            hideSearchResults()
        }
        
        // ✅ 更新清除按钮可见性
        updateClearButtonVisibility()
    }

    /**
     * 更新模拟 UI
     */
    private fun updateSimulationUI(state: MainViewModel.SimulationState) {
        if (state.isSimulating) {
            binding.fab.setImageResource(R.drawable.ic_fly)
            binding.fab.imageTintList = null
            // 停止脉冲动画
            idlePulseAnimator?.cancel()
            idlePulseAnimator = null
            
            // 更新状态徽章
            binding.statusText.text = "模拟中"
        } else {
            binding.fab.setImageResource(R.drawable.ic_position)
            binding.fab.imageTintList = null
            // 重启脉冲动画
            if (idlePulseAnimator == null) {
                idlePulseAnimator = AnimationHelper.pulseInfinite(binding.fab, 2000)
            }
            
            // 更新状态徽章
            binding.statusText.text = "未模拟"
        }
    }

    /**
     * 更新底部面板 UI
     */
    private fun updateBottomSheetUI(state: MainViewModel.BottomSheetState) {
        // 可以在这里更新底部面板的展开/收起状态
    }

    /**
     * 初始化地图
     */
    private fun initMap() {
        aMap = binding.mapView.map
        
        // ✅ 初始化夜间模式状态（确保地图类型正确）
        isNightMode = (resources.configuration.uiMode 
            and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        Timber.d("initMap: isNightMode=$isNightMode")
        
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
        updateMapTypeForNightMode()
        
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
     * 初始化搜索功能
     */
    private fun initSearch() {
        searchAdapter = SearchResultAdapter { poi ->
            // 点击搜索结果
            viewModel.selectSearchResult(poi)
            binding.searchEdit.setText(poi.name)
            binding.searchEdit.setSelection(binding.searchEdit.text.length)
        }
        
        binding.searchResultList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
        
        // ✅ 初始化搜索清除按钮
        binding.searchClearBtn.setOnClickListener {
            clearSearch()
        }
        
        // 监听输入框变化，动态显示/隐藏清除按钮
        binding.searchEdit.addTextChangedListener(searchTextWatcher)
    }

    /**
     * 初始化BottomSheet
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
     * 应用BottomSheet视差效果
     */
    private fun applyBottomSheetParallax(slideOffset: Float) {
        val normalizedOffset = if (slideOffset < 0) 0f else slideOffset
        
        // 位置信息卡片透明度
        binding.locationInfoCard?.alpha = 0.7f + normalizedOffset * 0.3f
    }

    /**
     * 初始化底部导航
     */
    private fun initBottomNavigation() {
        // 设置选中态背景色（使用 primary_container 颜色）
        try {
            val containerColor = ContextCompat.getColor(requireContext(), R.color.primary_container)
            binding.bottomNav.setItemActiveIndicatorColor(android.content.res.ColorStateList.valueOf(containerColor))
        } catch (e: Exception) {
            Timber.e(e, "Failed to set active indicator color")
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
     * 设置点击事件
     */
    private fun setupClickListeners() {
        // 搜索框
        binding.searchEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEdit.text.toString()
                if (query.isNotEmpty()) {
                    // 获取当前地图中心作为搜索中心点
                    val center = viewModel.mapState.value.currentLocation ?: aMap.cameraPosition.target
                    viewModel.searchPlaces(query, center.latitude, center.longitude)
                    // 搜索后隐藏键盘
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }

        // 搜索框文本变化
        binding.searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty()) {
                    viewModel.hideSearchResults()
                }
            }
        })

        // 搜索框焦点变化
        binding.searchEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.hideSearchResults()
            } else {
                val textLength = binding.searchEdit.text.length
                if (textLength > 0) {
                    binding.searchEdit.setSelection(textLength)
                }
            }
        }

        // 地图控制按钮
        binding.zoomInBtn.setOnClickListener {
            aMap.animateCamera(CameraUpdateFactory.zoomIn())
        }

        binding.zoomOutBtn.setOnClickListener {
            aMap.animateCamera(CameraUpdateFactory.zoomOut())
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

        binding.copyBtn.setOnClickListener {
            copyCoordinates()
        }

        // FAB按钮
        binding.fab.setOnClickListener {
            toggleSimulation()
        }
    }

    /**
     * 地图点击事件
     */
    private fun onMapClick(latLng: LatLng) {
        // 如果是拖动后松开的点击，忽略
        if (isMapDragging) {
            Timber.d("Map click ignored (after drag), isMapDragging=$isMapDragging")
            return
        }
        
        Timber.d("Map clicked: ${latLng.latitude}, ${latLng.longitude}, isMapDragging=$isMapDragging")
        viewModel.hideSearchResults()
        // 点击地图选择位置，但不移动相机（避免拖动地图后被拉回）
        viewModel.selectPosition(latLng, moveCamera = false)
        updateLocationInfo(latLng)
    }

    /**
     * 地图长按事件
     */
    private fun onMapLongClick(latLng: LatLng) {
        Timber.d("Map long clicked: ${latLng.latitude}, ${latLng.longitude}")
        // 长按也触发点击逻辑
        onMapClick(latLng)
    }

    /**
     * 更新地图标记
     * @param latLng 标记位置
     * @param moveCamera 是否移动相机到标记位置
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
        
        // 只有在需要时才移动相机（如点击 FAB 确认时）
        if (moveCamera) {
            val currentZoom = aMap.cameraPosition.zoom
            // ✅ 使用 animateCamera 实现流畅的相机移动动画
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, currentZoom))
            Timber.d("Camera animated to marker position, zoom=$currentZoom")
        } else {
            Timber.d("Camera NOT moved (moveCamera=false), center after update: ${aMap.cameraPosition.target}")
        }
    }

    /**
     * 更新位置信息
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
                    // ✅ API 33+ 兼容性修复：先检查 Geocoder 是否可用
                    if (!android.location.Geocoder.isPresent()) {
                        Timber.w("Geocoder service is not available on this device")
                        return@launch
                    }
                    
                    val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                    
                    // ✅ API 33+: getFromLocation 可能返回 null 或抛出 IOException
                    @Suppress("DEPRECATION")  // getFromLocation 在 Android 14+ 已弃用，推荐使用异步 API
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    
                    if (addresses != null && addresses.isNotEmpty()) {
                        val addr = addresses[0].getAddressLine(0) ?: ""
                        withContext(Dispatchers.Main) {
                            if (_binding != null && addr.isNotEmpty()) {
                                AnimationHelper.fadeIn(binding.addressText, 200)
                                binding.addressText.text = addr
                            }
                        }
                    } else {
                        Timber.d("Geocoder returned empty result for: ${latLng.latitude}, ${latLng.longitude}")
                    }
                } catch (e: java.io.IOException) {
                    // ✅ API 33+ 常见异常：网络问题或服务不可用
                    Timber.w(e, "Geocoder IO error (API 33+ may throw IOException)")
                } catch (e: IllegalArgumentException) {
                    // ✅ API 33+ 新增：无效坐标会抛出此异常
                    Timber.w(e, "Invalid coordinates for Geocoder: ${latLng.latitude}, ${latLng.longitude}")
                } catch (e: Exception) {
                    // ✅ 捕获其他未知异常
                    Timber.e(e, "Unexpected Geocoder error")
                }
            }
        }
    }

    /**
     * 显示搜索结果列表
     */
    private fun showSearchResults() {
        if (!isSearchResultVisible) {
            binding.searchResultContainer.visibility = View.VISIBLE
            binding.searchResultList.animate().cancel()
            AnimationHelper.fadeIn(binding.searchResultList, 250)
            isSearchResultVisible = true
            // 为搜索结果列表添加底部圆角，与搜索框保持一致
            val radius = 16f * resources.displayMetrics.density
            binding.searchResultList.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.surface))
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            }
        }
    }

    /**
     * 隐藏搜索结果列表
     */
    private fun hideSearchResults() {
        if (isSearchResultVisible) {
            binding.searchResultList.animate().cancel()
            AnimationHelper.fadeOut(binding.searchResultList, 200) {
                binding.searchResultContainer.visibility = View.GONE
            }
            isSearchResultVisible = false
        }
    }
    
    /**
     * ✅ 清除搜索（清空输入框、隐藏结果、隐藏清除按钮）
     */
    private fun clearSearch() {
        // 先移除监听器，避免 setText 触发 TextWatcher
        binding.searchEdit.removeTextChangedListener(searchTextWatcher)
        
        binding.searchEdit.setText("")
        binding.searchEdit.clearFocus()
        viewModel.hideSearchResults()
        
        // 手动更新按钮状态
        binding.searchClearBtn.visibility = View.GONE
        
        // 重新添加监听器
        binding.searchEdit.addTextChangedListener(searchTextWatcher)
        
        UIFeedbackHelper.showToast(requireContext(), "已清除搜索")
    }
    
    /**
     * ✅ 更新清除按钮的可见性
     */
    private fun updateClearButtonVisibility() {
        val hasText = binding.searchEdit.text.isNotEmpty()
        val hasResults = isSearchResultVisible
        
        // 当有输入内容或有搜索结果时显示清除按钮
        binding.searchClearBtn.visibility = if (hasText || hasResults) View.VISIBLE else View.GONE
    }

    /**
     * 切换模拟状态
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

        // 调用 ViewModel 确认模拟（事件处理器会显示 Toast，这里不需要再显示）
        viewModel.confirmSimulation()
    }

    /**
     * 添加到收藏
     */
    private fun addToFavorite() {
        viewModel.mapState.value.markedPosition?.let { location ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = com.mockloc.VirtualLocationApp.getDatabase()
                    val exists = db.favoriteLocationDao().exists(location.latitude, location.longitude)
                    
                    if (exists) {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            UIFeedbackHelper.showToast(requireContext(), "该位置已在收藏中")
                        }
                    } else {
                        val address = getAddressFromLocation(location)
                        val favorite = com.mockloc.data.db.FavoriteLocation(
                            name = address,
                            address = address,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                        db.favoriteLocationDao().insert(favorite)
                        
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            UIFeedbackHelper.showToast(requireContext(), "已添加到收藏")
                        }
                    }
                } catch (e: Exception) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        UIFeedbackHelper.showToast(requireContext(), "添加失败，请重试")
                    }
                    Timber.e(e, "添加收藏失败")
                }
            }
        } ?: run {
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_please_select_location))
        }
    }

    /**
     * 获取地址名称
     */
    private fun getAddressFromLocation(latLng: LatLng): String {
        return try {
            // ✅ API 33+ 兼容性修复：先检查 Geocoder 是否可用
            if (!android.location.Geocoder.isPresent()) {
                Timber.w("Geocoder service is not available")
                return String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            }
            
            val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
            
            // ✅ API 33+: getFromLocation 可能返回 null 或抛出 IOException
            @Suppress("DEPRECATION")  // getFromLocation 在 Android 14+ 已弃用，推荐使用异步 API
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            
            if (addresses != null && addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0) ?: String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            } else {
                Timber.d("Geocoder returned empty result")
                String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            }
        } catch (e: java.io.IOException) {
            // ✅ API 33+ 常见异常：网络问题或服务不可用
            Timber.w(e, "Geocoder IO error in getAddressFromLocation")
            String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
        } catch (e: IllegalArgumentException) {
            // ✅ API 33+ 新增：无效坐标会抛出此异常
            Timber.w(e, "Invalid coordinates: ${latLng.latitude}, ${latLng.longitude}")
            String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
        } catch (e: Exception) {
            // ✅ 捕获其他未知异常
            Timber.e(e, "Unexpected Geocoder error in getAddressFromLocation")
            String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
        }
    }

    /**
     * 复制坐标
     */
    private fun copyCoordinates() {
        viewModel.mapState.value.markedPosition?.let { location ->
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("coordinates", "${location.latitude}, ${location.longitude}")
            clipboard.setPrimaryClip(clip)
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_coordinates_copied))
        } ?: run {
            UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_please_select_location))
        }
    }

    /**
     * 显示输入坐标对话框
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
                    
                    // 转换为 GCJ-02（高德地图坐标系）用于显示
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
                    
                    // 手动输入坐标需要移动相机
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
     * 显示地图图层对话框（带预览图）
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
        
        // 更新选中状态
        fun updateSelection(selected: String) {
            normalCheck.visibility = if (selected == "normal") android.view.View.VISIBLE else android.view.View.GONE
            satelliteCheck.visibility = if (selected == "satellite") android.view.View.VISIBLE else android.view.View.GONE
            nightCheck.visibility = if (selected == "night") android.view.View.VISIBLE else android.view.View.GONE
        }
        
        // 初始选中状态
        updateSelection(when (aMap.mapType) {
            AMap.MAP_TYPE_NORMAL -> "normal"
            AMap.MAP_TYPE_SATELLITE -> "satellite"
            AMap.MAP_TYPE_NIGHT -> "night"
            else -> "normal"
        })
        
        normalLayer.setOnClickListener {
            aMap.mapType = AMap.MAP_TYPE_NORMAL
            isManualLayerSelected = true  // 标记为手动选择
            updateSelection("normal")
            dialog.dismiss()
        }
        
        satelliteLayer.setOnClickListener {
            aMap.mapType = AMap.MAP_TYPE_SATELLITE
            isManualLayerSelected = true  // 标记为手动选择
            updateSelection("satellite")
            dialog.dismiss()
        }
        
        nightLayer.setOnClickListener {
            aMap.mapType = AMap.MAP_TYPE_NIGHT
            isManualLayerSelected = true  // 标记为手动选择
            updateSelection("night")
            dialog.dismiss()
        }
        
        dialog.show()
    }

    /**
     * 检查权限
     */
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // ✅ 智能自动定位策略
            val lastLocation = viewModel.mapState.value.currentLocation
            
            if (lastLocation == null) {
                // 首次启动或没有缓存位置 → 自动定位
                Timber.d("First launch or no cached location, auto-location enabled")
                viewModel.initLocation()
            } else {
                // 已有缓存位置 → 不自动定位，直接使用缓存
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
            .setTitle("定位权限已拒绝")
            .setMessage("您已拒绝定位权限，虚拟定位功能无法使用。\n\n请在系统设置中手动开启定位权限。")
            .setPositiveButton("去设置") { _, _ ->
                // 打开应用设置页面
                PermissionHelper.openAppSettings(requireContext())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 恢复地图状态
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
                
                // 如果 SP 中有有效的中心点，直接恢复
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
            
            // 检测夜间模式变化
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

    override fun onDestroyView() {
        super.onDestroyView()
        
        // 清除地图监听器
        aMap.setOnMapClickListener(null)
        aMap.setOnMapLongClickListener(null)
        aMap.setOnMarkerDragListener(null)
        
        // 清理FAB脉冲动画
        idlePulseAnimator?.cancel()
        idlePulseAnimator = null
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
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        val isNight = (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        Timber.d("MainFragment configuration changed: isNight=$isNight")
        
        // 更新夜间模式状态并切换地图类型
        updateNightModeStatus()
        
        // 手动更新视图背景颜色（因为 configChanges 阻止了自动重建）
        updateViewBackgrounds()
    }

    /**
     * 手动更新视图背景颜色以响应主题变化
     */
    private fun updateViewBackgrounds() {
        if (_binding == null) return
        
        try {
            // ✅ 关键修复：使用 Resources.getColor(resId, theme) 强制从最新主题获取颜色
            // ContextCompat.getColor() 在 configChanges 下可能返回缓存的旧颜色
            val resources = requireContext().resources
            val theme = requireContext().theme
            
            val surfaceColor = resources.getColor(R.color.surface, theme)
            val backgroundColor = resources.getColor(R.color.background, theme)
            val primaryColor = resources.getColor(R.color.primary, theme)
            val surfaceVariantColor = resources.getColor(R.color.surface_variant, theme)
            val dividerColor = resources.getColor(R.color.divider, theme)  // ✅ 搜索结果容器顶部分隔线
            val dividerLightColor = resources.getColor(R.color.divider_light, theme)  // Item 分隔线
            val textPrimaryColor = resources.getColor(R.color.text_primary, theme)
            val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)
            val navIndicatorColor = resources.getColor(R.color.nav_item_selected_background, theme)
            
            // 更新 CoordinatorLayout 背景
            binding.fragmentRoot.setBackgroundColor(backgroundColor)
            
            // 更新搜索卡片背景
            binding.searchCard.setCardBackgroundColor(surfaceColor)
            Timber.d("Search card background updated: surfaceColor=#${Integer.toHexString(surfaceColor)}")
            
            // 更新搜索结果列表背景
            binding.searchResultList.setBackgroundColor(surfaceColor)
            
            // ✅ 优化：使用 ID 引用替代 getChildAt(0)，提高代码健壮性
            binding.searchTopDivider.setBackgroundColor(dividerColor)
            
            // ✅ 更新 BottomSheet 背景（重新加载 drawable 以保留圆角）
            // 关键修复：先清除背景，再重新设置，强制触发颜色重新解析
            binding.bottomSheet.background = null
            binding.bottomSheet.setBackgroundResource(R.drawable.bg_bottom_sheet)
            Timber.d("BottomSheet background updated")
            
            // ✅ 更新 BottomSheet 拖拽手柄颜色（重新加载 drawable 以保留圆角）
            binding.dragHandle.background = null
            binding.dragHandle.setBackgroundResource(R.drawable.bg_drag_handle)
            Timber.d("Drag handle background updated")
            
            // 更新位置信息卡片背景
            binding.locationInfoCard.setCardBackgroundColor(primaryColor)
            
            // 更新底部导航栏背景（使用 backgroundColor 而非 surfaceColor）
            binding.bottomNav.setBackgroundColor(backgroundColor)
            Timber.d("Bottom nav background updated: backgroundColor=#${Integer.toHexString(backgroundColor)}")
            
            // ✅ 通知搜索结果列表刷新，让 Item 重新加载颜色资源
            binding.searchResultList.adapter?.notifyDataSetChanged()
            Timber.d("Search result list notified for theme change")
            
            // ✅ 额外修复：强制刷新 RecyclerView 的布局管理器（确保所有 Item 重新测量）
            binding.searchResultList.layoutManager?.requestLayout()
            
            // ✅ 更新 BottomNavigationView 的图标和文字颜色（ColorStateList 需要手动刷新）
            val navItemColorStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    resources.getColor(R.color.primary, theme),
                    resources.getColor(R.color.text_secondary, theme)
                )
            )
            binding.bottomNav.itemIconTintList = navItemColorStateList
            binding.bottomNav.itemTextColor = navItemColorStateList
            
            // ✅ 强制刷新 BottomNavigationView 的状态（确保颜色立即生效）
            binding.bottomNav.post {
                binding.bottomNav.refreshDrawableState()
            }
            
            Timber.d("BottomNavigationView colors updated: primary=#${Integer.toHexString(primaryColor)}, text_secondary=#${Integer.toHexString(textSecondaryColor)}")
            
            // ✅ 更新搜索框文字颜色（EditText 不会自动刷新）
            binding.searchEdit.setTextColor(textPrimaryColor)
            binding.searchEdit.setHintTextColor(resources.getColor(R.color.text_hint, theme))
            
            // ✅ 更新搜索图标的 tint 颜色（XML 静态绑定不会自动刷新）
            binding.searchEdit.parent?.let { searchRow ->
                val searchIcon = (searchRow as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.ImageView
                searchIcon?.setColorFilter(resources.getColor(R.color.text_hint, theme))
            }
            
            // ✅ 更新操作按钮组的图标和文字颜色
            updateButtonIconTint(binding.inputCoordsBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.historyBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.favoriteBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.copyBtn, primaryColor, textSecondaryColor)
            
            // 直接设置选中项指示器颜色（绕过 configChanges 导致的资源不刷新问题）
            binding.bottomNav.setItemActiveIndicatorColor(
                android.content.res.ColorStateList.valueOf(navIndicatorColor)
            )
            
            // 更新右侧固定栏按钮背景（重要！）
            // 普通按钮使用 surface 或 surface_variant
            updateButtonBackground(binding.zoomInBtn, surfaceVariantColor)
            updateButtonBackground(binding.zoomOutBtn, surfaceVariantColor)
            updateButtonBackground(binding.layerBtn, surfaceVariantColor)
            // 定位按钮使用主色调
            updateButtonBackground(binding.locationBtn, primaryColor)
            
            // ✅ 更新右侧按钮的图标 tint 颜色
            binding.zoomInBtn.setColorFilter(textPrimaryColor)
            binding.zoomOutBtn.setColorFilter(textPrimaryColor)
            binding.layerBtn.setColorFilter(textPrimaryColor)
            // 定位按钮图标保持白色
            binding.locationBtn.setColorFilter(android.graphics.Color.WHITE)
            
            // ✅ 更新 FAB 按钮的背景和图标颜色
            binding.fab.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            binding.fab.setColorFilter(android.graphics.Color.WHITE)
            
            // ✅ 更新搜索加载指示器颜色（ProgressBar indeterminateTint 需要手动刷新）
            binding.searchProgress.indeterminateTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            
            // ✅ 更新状态徽章背景（重新加载 drawable 以应用夜间模式版本）
            binding.statusBadge.setBackgroundResource(R.drawable.bg_status_badge)
            
            // ✅ 更新状态徽章文字颜色（白天白色，夜间深色）
            // 注意：状态徽章在地图上，背景是半透明的
            // 白天：白色文字 + 半透明白色背景
            // 夜间：深色文字 + 半透明黑色背景
            val statusTextColor = if (isNightMode) {
                resources.getColor(R.color.text_primary, theme)  // 夜间：白色
            } else {
                android.graphics.Color.WHITE  // 白天：白色
            }
            binding.statusText.setTextColor(statusTextColor)
            Timber.d("Status text color updated: isNightMode=$isNightMode")
            
            // ✅ 更新位置信息卡片内的文字颜色
            // 卡片背景是 primary（蓝绿色），所以文字保持白色
            binding.latitudeText.setTextColor(android.graphics.Color.WHITE)
            binding.longitudeText.setTextColor(android.graphics.Color.WHITE)
            binding.addressText.setTextColor(android.graphics.Color.WHITE)
            Timber.d("Location info card text colors updated")
            
            Timber.d("View backgrounds updated for theme change")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update view backgrounds")
        }
    }

    /**
     * 更新单个按钮的背景颜色
     */
    private fun updateButtonBackground(button: android.widget.ImageButton, color: Int) {
        button.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12.dpToPx().toFloat()
            setColor(color)
        }
    }

    /**
     * 更新操作按钮组的图标和文字颜色
     */
    private fun updateButtonIconTint(
        buttonContainer: android.widget.LinearLayout,
        iconTint: Int,
        textColor: Int
    ) {
        try {
            // 更新图标 tint
            val icon = buttonContainer.getChildAt(0) as? android.widget.ImageView
            icon?.setColorFilter(iconTint)
            
            // 更新文字颜色
            val text = buttonContainer.getChildAt(1) as? android.widget.TextView
            text?.setTextColor(textColor)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update button icon tint")
        }
    }
}

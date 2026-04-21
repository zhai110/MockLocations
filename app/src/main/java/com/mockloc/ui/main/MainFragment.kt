package com.mockloc.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import com.mockloc.util.AdvancedAnimationHelper
import com.mockloc.util.MapConfig
import com.mockloc.util.OnboardingManager
import com.mockloc.util.PermissionHelper
import com.mockloc.util.UIFeedbackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var isNightMode = false  // 标记当前是否为夜间模式
    private var isManualLayerSelected = false  // 标记用户是否手动选择了图层

    // 定位权限请求启动器
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Location permission granted")
            viewModel.initLocation()
        } else {
            UIFeedbackHelper.showToast(requireContext(), "定位权限被拒绝，虚拟定位功能不可用")
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
            requireActivity().window.setDecorFitsSystemWindows(false)
            requireActivity().window.insetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set immersive layout")
        }

        // 检测当前是否为夜间模式
        updateNightModeStatus()

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
        idlePulseAnimator = AdvancedAnimationHelper.pulseInfinite(binding.fab, 2000)

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
                        event?.let {
                            handleSimulationControlEvent(it)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 处理模拟控制事件
     */
    private fun handleSimulationControlEvent(event: MainViewModel.SimulationControlEvent) {
        when (event.eventType) {
            MainViewModel.SimulationControlEvent.EventType.START_SIMULATION -> {
                startSimulation(event.latitude!!, event.longitude!!, event.altitude)
            }
            MainViewModel.SimulationControlEvent.EventType.STOP_SIMULATION -> {
                stopSimulation()
            }
            MainViewModel.SimulationControlEvent.EventType.UPDATE_POSITION -> {
                updatePosition(event.latitude!!, event.longitude!!, event.altitude)
            }
        }
    }
    
    /**
     * 启动模拟
     */
    private fun startSimulation(latitude: Double, longitude: Double, altitude: Float) {
        try {
            val intent = android.content.Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_START
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
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
            
            UIFeedbackHelper.showToast(requireContext(), "已启动模拟")
        } catch (e: Exception) {
            Timber.e(e, "启动模拟失败")
            UIFeedbackHelper.showToast(requireContext(), "启动模拟失败: ${e.message}")
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
            
            UIFeedbackHelper.showToast(requireContext(), "已停止模拟")
        } catch (e: Exception) {
            Timber.e(e, "停止模拟失败")
            UIFeedbackHelper.showToast(requireContext(), "停止模拟失败: ${e.message}")
        }
    }
    
    /**
     * 更新位置
     */
    private fun updatePosition(latitude: Double, longitude: Double, altitude: Float) {
        try {
            val intent = android.content.Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_UPDATE
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                putExtra(LocationService.EXTRA_COORD_GCJ02, true)
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
        Timber.d("updateMapUI called: markedPosition=${state.markedPosition}, currentLocation=${state.currentLocation}, shouldMoveCamera=${state.shouldMoveCamera}")
        
        // 更新标记（只在位置改变时更新，避免频繁 remove/add 导致地图跳动）
        state.markedPosition?.let { position ->
            // 只有当标记位置真正改变时才更新
            if (currentMarker == null || currentMarker!!.position != position) {
                Timber.d("updateMapUI: updating marker at $position")
                // 根据 ViewModel 的状态决定是否移动相机
                updateMarker(position, moveCamera = state.shouldMoveCamera)
                // 重置 shouldMoveCamera 标志
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
                idlePulseAnimator = AdvancedAnimationHelper.pulseInfinite(binding.fab, 2000)
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
        
        // 启用我的位置图层（使用 LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER）
        // 显示蓝点+方向箭头，但不自动移动相机
        aMap.myLocationStyle = com.amap.api.maps.model.MyLocationStyle().apply {
            myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            showMyLocation(true)
            // 设置精度圈颜色
            strokeColor(com.mockloc.R.color.primary)
            radiusFillColor(android.graphics.Color.parseColor("#1A667EEA"))
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
                // 相机变化完成，保存状态
                viewModel.saveMapState()
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
                    startActivity(Intent(requireContext(), HistoryActivity::class.java))
                    requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    true
                }
                R.id.nav_favorite -> {
                    startActivity(Intent(requireContext(), FavoriteActivity::class.java))
                    requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
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
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, currentZoom))
            Timber.d("Camera moved to marker position, zoom=$currentZoom")
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
                    val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val addr = addresses[0].getAddressLine(0) ?: ""
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            if (addr.isNotEmpty()) {
                                AnimationHelper.fadeIn(binding.addressText, 200)
                                binding.addressText.text = addr
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "获取地址失败")
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
                setColor(android.graphics.Color.WHITE)
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
            UIFeedbackHelper.showToast(requireContext(), "请先选择位置")
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
            UIFeedbackHelper.showToast(requireContext(), "请先选择位置")
        }
    }

    /**
     * 获取地址名称
     */
    private fun getAddressFromLocation(latLng: LatLng): String {
        return try {
            val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0) ?: String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            } else {
                String.format("%.4f, %.4f", latLng.latitude, latLng.longitude)
            }
        } catch (e: Exception) {
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
            UIFeedbackHelper.showToast(requireContext(), "坐标已复制")
        } ?: run {
            UIFeedbackHelper.showToast(requireContext(), "请先选择位置")
        }
    }

    /**
     * 显示输入坐标对话框
     */
    private fun showInputCoordsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input_coords, null)
        val latEdit = dialogView.findViewById<android.widget.EditText>(R.id.edit_latitude)
        val lngEdit = dialogView.findViewById<android.widget.EditText>(R.id.edit_longitude)
        
        // 使用 MaterialAlertDialogBuilder 自带圆角样式，去掉标题
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                try {
                    val lat = latEdit.text.toString().toDouble()
                    val lng = lngEdit.text.toString().toDouble()
                    val latLng = LatLng(lat, lng)
                    // 手动输入坐标需要移动相机
                    viewModel.selectPosition(latLng, moveCamera = true)
                    updateLocationInfo(latLng)
                } catch (e: Exception) {
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
            viewModel.initLocation()
        }
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

    // ==================== 生命周期方法 ====================

    override fun onResume() {
        super.onResume()
        
        // 确保 binding 有效
        if (_binding != null) {
            binding.mapView.onResume()
            
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
            // 使用 ContextCompat.getColor 获取当前主题的颜色
            val surfaceColor = ContextCompat.getColor(requireContext(), R.color.surface)
            val backgroundColor = ContextCompat.getColor(requireContext(), R.color.background)
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
            val surfaceVariantColor = ContextCompat.getColor(requireContext(), R.color.surface_variant)
            
            // 更新 CoordinatorLayout 背景
            binding.fragmentRoot.setBackgroundColor(backgroundColor)
            
            // 更新搜索卡片背景
            binding.searchCard.setCardBackgroundColor(surfaceColor)
            
            // 更新搜索结果列表背景
            binding.searchResultList.setBackgroundColor(surfaceColor)
            
            // 更新 BottomSheet 背景
            binding.bottomSheet.setBackgroundColor(surfaceColor)
            
            // 更新位置信息卡片背景
            binding.locationInfoCard.setCardBackgroundColor(primaryColor)
            
            // 更新底部导航栏背景
            binding.bottomNav.setBackgroundColor(surfaceColor)
            
            // 更新右侧固定栏按钮背景（重要！）
            // 普通按钮使用 surface 或 surface_variant
            updateButtonBackground(binding.zoomInBtn, surfaceVariantColor)
            updateButtonBackground(binding.zoomOutBtn, surfaceVariantColor)
            updateButtonBackground(binding.layerBtn, surfaceVariantColor)
            // 定位按钮使用主色调
            updateButtonBackground(binding.locationBtn, primaryColor)
            
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
}

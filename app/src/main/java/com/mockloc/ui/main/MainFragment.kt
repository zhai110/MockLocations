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
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import com.mockloc.ui.favorite.FavoriteActivity
import com.mockloc.ui.history.HistoryActivity
import com.mockloc.ui.settings.SettingsActivity
import com.mockloc.util.AnimationHelper
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
    private lateinit var dialogDelegate: com.mockloc.ui.main.delegate.DialogDelegate

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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Location permission granted")
            viewModel.initLocation()
        } else {
            val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            if (showRationale) {
                showPermissionRationaleDialog()
            } else {
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
            var statusBarHeight = 0
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                statusBarHeight = resources.getDimensionPixelSize(resourceId)
            }
            if (statusBarHeight > 0) {
                (binding.searchCard.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { params ->
                    params.topMargin = statusBarHeight + (16 * resources.displayMetrics.density + 0.5f).toInt()
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
        simulationDelegate.onUpdateMarker = { latLng, moveCamera -> updateMarker(latLng, moveCamera) }
        simulationDelegate.onSaveToHistory = { lat, lng -> viewModel.saveToHistoryAsync(lat, lng) }
        simulationDelegate.currentTabMode = currentTabMode
        
        routeEditDelegate = com.mockloc.ui.main.delegate.RouteEditDelegate(this, viewModel, binding)
        routeEditDelegate.onGetAMap = { if (::aMap.isInitialized) aMap else null }
        
        themeDelegate = com.mockloc.ui.main.delegate.ThemeDelegate(this, binding)
        themeDelegate.init()

        dialogDelegate = com.mockloc.ui.main.delegate.DialogDelegate(this)

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

        // 恢复地图状态
        viewModel.restoreMapState()?.let {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, viewModel.mapState.value.zoom))
        }
    }

    /**
     * 观察 ViewModel 的 StateFlow，将状态变化委托给各 Delegate 处理。
     *
     * 观察的状态流：
     * - [MainViewModel.mapState] → [updateMapUI]
     * - [MainViewModel.searchState] → [updateSearchUI]
     * - [MainViewModel.simulationState] → [SimulationDelegate.updateSimulationUI]
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
                    viewModel.routeState.collect { state ->
                        routeEditDelegate.updateRouteUI(state)
                    }
                }
                
                // 观察模拟控制事件
                launch {
                    viewModel.simulationControlEvents.collect { event ->
                        simulationDelegate.handleSimulationControlEvent(event)
                    }
                }

                launch {
                    viewModel.favoriteResult.collect { message ->
                        UIFeedbackHelper.showToast(requireContext(), message)
                    }
                }
            }
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
        if (state.currentLocation != null) {
            Timber.d("updateMapUI: currentLocation updated to ${state.currentLocation}, but NOT moving camera")
            updateLocationInfo(state.currentLocation!!, state.address)
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
        aMap = binding.mapView.map
        Timber.d("AMap instance obtained successfully")
        
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
            Timber.d("Map long clicked: ${latLng.latitude}, ${latLng.longitude}")
            onMapClick(latLng)
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
                // 底部面板视差效果
                val normalizedOffset = if (slideOffset < 0) 0f else slideOffset
                binding.locationInfoCard?.alpha = 0.7f + normalizedOffset * 0.3f
                
                // FAB跟随BottomSheet滑动
                val fabTranslation = resources.getDimension(R.dimen.fab_translation_y)
                val fabTranslationY = -fabTranslation * (1f - slideOffset.coerceIn(0f, 1f))
                binding.fab.translationY = fabTranslationY
            }
            })
        }
    }

    /**
     * 初始化面板 Tab（单点/路线），切换时更新按钮和 ViewModel 状态。
     */
    private fun initPanelTabs() {
        binding.panelTabs.setScrollPosition(0, 0f, false)
        binding.panelTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.view?.setBackgroundResource(android.R.color.transparent)
                when (tab?.position) {
                    0 -> { setPanelMode(false); viewModel.setRouteMode(false) }
                    1 -> { setPanelMode(true); viewModel.setRouteMode(true) }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.view?.setBackgroundResource(android.R.color.transparent)
            }
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        setPanelMode(false)
    }

    private fun setPanelMode(showRouteMode: Boolean) {
        currentTabMode = if (showRouteMode) 1 else 0
        simulationDelegate.currentTabMode = currentTabMode
        binding.pointActionButtons.visibility = if (showRouteMode) View.GONE else View.VISIBLE
        binding.routeControlCard.visibility = if (showRouteMode) View.VISIBLE else View.GONE
        binding.routePanel.visibility = if (showRouteMode) View.VISIBLE else View.GONE
        // 根据当前模拟状态更新 FAB 图标（而非硬编码 ic_position）
        simulationDelegate.updateSimulationUI(viewModel.simulationState.value, currentTabMode)
    }



    /**
     * 初始化底部导航栏，设置图标/文字颜色和页面跳转（地图/历史/收藏/设置）。
     */
    private fun initBottomNavigation() {
        val res = requireContext().resources
        val theme = requireContext().theme
        
        try {
            binding.bottomNav.setItemActiveIndicatorColor(android.content.res.ColorStateList.valueOf(res.getColor(R.color.nav_item_selected_background, theme)))
        } catch (e: Exception) { Timber.e(e, "Failed to set active indicator color") }
        
        try {
            val primary = res.getColor(R.color.primary, theme)
            val secondary = res.getColor(R.color.text_secondary, theme)
            binding.bottomNav.itemIconTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                intArrayOf(primary, secondary)
            )
            binding.bottomNav.itemTextColor = binding.bottomNav.itemIconTintList
        } catch (e: Exception) { Timber.e(e, "Failed to init nav colors") }
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            val cls = when (item.itemId) {
                R.id.nav_history -> HistoryActivity::class.java
                R.id.nav_favorite -> FavoriteActivity::class.java
                R.id.nav_settings -> SettingsActivity::class.java
                else -> null
            }
            if (cls != null) {
                val intent = Intent(requireContext(), cls)
                if (item.itemId == R.id.nav_history || item.itemId == R.id.nav_favorite) {
                    (historyLauncher.takeIf { item.itemId == R.id.nav_history } ?: favoriteLauncher).launch(intent)
                } else {
                    startActivity(intent)
                }
                applyNavTransition()
            }
            item.itemId == R.id.nav_map || cls != null
        }
    }
    
    private fun applyNavTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requireActivity().overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            @Suppress("DEPRECATION")
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupClickListeners() {
        binding.zoomInBtn.setOnClickListener { aMap.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.zoomOutBtn.setOnClickListener { aMap.animateCamera(CameraUpdateFactory.zoomOut()) }
        binding.locationBtn.setOnClickListener {
            viewModel.mapState.value.currentLocation?.let { aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
                ?: viewModel.initLocation()
        }
        binding.layerBtn.setOnClickListener { themeDelegate.showMapLayerDialog(aMap) }
        binding.inputCoordsBtn.setOnClickListener {
            dialogDelegate.showInputCoordsDialog { gcjLatLng ->
                viewModel.selectPosition(gcjLatLng, moveCamera = true)
                updateLocationInfo(gcjLatLng)
            }
        }
        binding.historyBtn.setOnClickListener { historyLauncher.launch(Intent(requireContext(), HistoryActivity::class.java)) }
        binding.favoriteBtn.setOnClickListener {
            viewModel.mapState.value.markedPosition?.let { viewModel.addToFavoriteAsync(it.latitude, it.longitude) }
                ?: UIFeedbackHelper.showToast(requireContext(), getString(R.string.toast_please_select_location))
        }
        binding.routeBtn.setOnClickListener { binding.panelTabs.getTabAt(1)?.select() }
        simulationDelegate.setupClickListeners()
        routeEditDelegate.setupClickListeners()
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
    private var lastGeocodeTime = 0L
    private val GEOCODE_THROTTLE_MS = 3000L

    private fun updateLocationInfo(latLng: LatLng, address: String = "") {
        AnimationHelper.animateNumberChange(
            binding.latitudeText,
            String.format("%.6f°", latLng.latitude)
        )
        AnimationHelper.animateNumberChange(
            binding.longitudeText,
            String.format("%.6f°", latLng.longitude)
        )
        
        if (address.isNotEmpty()) {
            AnimationHelper.fadeIn(binding.addressText, 200)
            binding.addressText.text = address
        } else {
            val now = System.currentTimeMillis()
            if (now - lastGeocodeTime < GEOCODE_THROTTLE_MS) return
            lastGeocodeTime = now
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val (_, fullAddress) = viewModel.getAddressFromLocation(latLng.latitude, latLng.longitude)
                    
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

    // ==================== 生命周期方法 ====================

    /**
     * 显示权限解释对话框（用户之前拒绝过，但未选择"不再询问"）
     */
    private fun showPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setTitle("需要定位权限")
            .setMessage("虚拟定位功能需要访问您的位置信息。\n\n请允许定位权限以使用此功能。")
            .setPositiveButton("授予权限") { _, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示权限被拒绝对话框（用户选择了"不再询问"）
     */
    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setTitle("需要定位权限")
            .setMessage("虚拟定位功能需要访问您的位置信息。\n\n请允许定位权限以使用此功能。")
            .setPositiveButton("确定") { _, _ ->
                PermissionHelper.openAppSettings(requireContext())
            }
            .setNegativeButton("取消", null)
            .show()
    }

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
            
            if (::aMap.isInitialized) {
                val themedContext = com.mockloc.util.ThemeUtils.createThemedContext(requireContext()).first
                themeDelegate.handleThemeUpdate(aMap, themedContext)
            }
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

package com.mockloc.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.mockloc.R
import com.mockloc.repository.PoiSearchHelper
import com.mockloc.util.AnimationHelper
import com.mockloc.util.MapUtils
import com.mockloc.util.PrefsConfig
import com.mockloc.util.UIFeedbackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 地图窗口控制器（完善版）
 * 
 * 负责管理悬浮窗中的地图选点界面
 * 包括：搜索框、地图视图、POI搜索结果、定位按钮
 */
class MapWindowController(
    private val context: Context,
    private val service: LocationService,
    private val windowManager: android.view.WindowManager,
    private val windowParams: android.view.WindowManager.LayoutParams,
    private val onSwitchToJoystick: () -> Unit,
    private val onSwitchToHistory: () -> Unit,
    private val onLocationSelected: (lat: Double, lng: Double) -> Unit
) : WindowController {

    override var rootView: View? = null
        private set
    
    override var isInitialized: Boolean = false
        private set
    
    override var isVisible: Boolean = false
        private set

    // 内部视图引用
    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var searchEditText: EditText? = null
    private var btnClose: ImageButton? = null
    private var btnGo: ImageButton? = null
    private var searchScroll: ScrollView? = null
    private var searchList: LinearLayout? = null
    
    // POI 搜索
    private var poiSearchHelper: PoiSearchHelper? = null
    
    // 定位客户端
    private var locationClient: AMapLocationClient? = null
    
    // 状态管理
    private var markedLatLng: LatLng? = null
    private var isPositionConfirmed = false
    private var btnGoPulseAnimator: android.animation.ValueAnimator? = null
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 主题色
    private var primaryColor: Int = 0
    private var textPrimary: Int = 0
    private var textSecondary: Int = 0
    private var textHint: Int = 0
    private var surface: Int = 0
    private var surfaceVariant: Int = 0
    private var divider: Int = 0
    
    // 暗黑模式
    private var isNightMode: Boolean = false

    override fun initialize() {
        if (isInitialized) return
        
        try {
            Timber.d("开始初始化 MapWindowController")
            
            isNightMode = (context.resources.configuration.uiMode
                    and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            Timber.d("初始化颜色")
            initColors()
            
            Timber.d("创建地图布局")
            createMapLayout()
            
            isInitialized = true
            
            // ✅ 初始化完成后立即校准地图类型，确保首次显示即正确
            updateMapTypeForNightMode()
            
            Timber.d("MapWindowController initialized with isNightMode=$isNightMode")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MapWindowController")
            isInitialized = false
        }
    }

    override fun show() {
        Timber.d("MapWindowController.show() called: isInitialized=$isInitialized, isNightMode=$isNightMode")
        
        if (!isInitialized) {
            Timber.d("Initializing MapWindowController for the first time")
            initialize()
        } else {
            // ✅ 每次显示时都检查主题状态（确保地图类型和颜色正确）
            val currentNightMode = (context.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            Timber.d("Theme check: isNightMode=$isNightMode, currentNightMode=$currentNightMode")
            
            if (isNightMode != currentNightMode) {
                Timber.d("Theme changed while hidden: isNightMode=$currentNightMode")
                isNightMode = currentNightMode
                
                // 重新初始化颜色
                initColors()
                
                // 更新地图类型
                updateMapTypeForNightMode()
                
                // ✅ 更新所有视图的颜色
                applyColorsToViews()
            } else {
                // ✅ 即使主题未变，也再次确认地图类型（防止 SDK 内部状态异常）
                updateMapTypeForNightMode()
            }
        }
        
        isVisible = true
        mapView?.onResume()
        
        // 重启定位（因为 hide() 时停止了）
        locationClient?.startLocation()
        
        // ✅ 每次显示时都恢复最新状态（可能来自主界面的修改）
        restoreMapState()
        
        Timber.d("Map window shown")
    }

    override fun hide() {
        isVisible = false
        mapView?.onPause()
        searchEditText?.clearFocus()
        
        // 停止定位
        locationClient?.stopLocation()
        
        Timber.d("Map window hidden")
    }

    override fun destroy() {
        scope.cancel()
        btnGoPulseAnimator?.cancel()
        
        // 销毁定位客户端
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
        
        mapView?.onDestroy()
        mapView = null
        aMap = null
        searchEditText = null
        btnClose = null
        btnGo = null
        searchScroll = null
        searchList = null
        poiSearchHelper = null
        rootView = null
        isInitialized = false
        isVisible = false
        Timber.d("MapWindowController destroyed")
    }

    /**
     * 初始化颜色（从主题读取）
     */
    private fun initColors() {
        val themedContext = com.mockloc.util.ThemeUtils.createThemedContext(context).first
        primaryColor = ContextCompat.getColor(themedContext, R.color.primary)
        textPrimary = ContextCompat.getColor(themedContext, R.color.text_primary)
        textSecondary = ContextCompat.getColor(themedContext, R.color.text_secondary)
        textHint = ContextCompat.getColor(themedContext, R.color.text_hint)
        surface = ContextCompat.getColor(themedContext, R.color.surface)
        surfaceVariant = ContextCompat.getColor(themedContext, R.color.surface_variant)
        divider = ContextCompat.getColor(themedContext, R.color.divider)
    }

    /**
     * 将当前主题颜色应用到所有视图
     */
    private fun applyColorsToViews() {
        try {
            val rootView = rootView ?: return
            val density = context.resources.displayMetrics.density
            
            // 更新容器背景
            if (rootView is LinearLayout) {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(surface)
                    cornerRadius = 10f * density
                    setStroke((3 * density).toInt(), divider)
                }
                rootView.background = bg
            }
            
            // 更新搜索框
            searchEditText?.apply {
                setTextColor(textPrimary)
                setHintTextColor(textHint)
            }
            
            // 更新关闭按钮
            btnClose?.setColorFilter(textSecondary)
            
            // 更新确定选点按钮
            btnGo?.apply {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(primaryColor)
                }
                background = bg
                setColorFilter(ContextCompat.getColor(context, R.color.on_primary))
            }
            
            Timber.d("Colors applied to views: primary=#${Integer.toHexString(primaryColor)}, surface=#${Integer.toHexString(surface)}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply colors to views")
        }
    }

    /**
     * 创建地图布局
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createMapLayout() {
        val density = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        poiSearchHelper = PoiSearchHelper(service)

        // 主容器（使用 DragLinearLayout 支持拖动）
        val container = DragLinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(surface)
                cornerRadius = 10f * density
                setStroke(dp(3), divider)
            }
            background = bg
            setPadding(dp(3), dp(3), dp(3), dp(3))
            minimumWidth = dp(320)
        }

        // 顶部工具栏：搜索输入框 + 关闭按钮
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(40)
            )
        }

        // 搜索输入框
        searchEditText = EditText(com.mockloc.util.ThemeUtils.createThemedContext(context).first).apply {
            hint = "搜索地点"
            textSize = 14f
            setTextColor(textPrimary)
            setHintTextColor(textHint)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(8), 0, dp(8), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(surfaceVariant)
                cornerRadius = 8f * density
                setStroke(dp(1), divider)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(32)).apply {
                weight = 1f
                leftMargin = dp(4)
            }
            val searchIcon = ContextCompat.getDrawable(context, R.drawable.ic_search)
            searchIcon?.setBounds(0, 0, dp(16), dp(16))
            setCompoundDrawables(searchIcon, null, null, null)
        }.also { topBar.addView(it) }

        // 关闭按钮
        btnClose = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            setColorFilter(textSecondary)
            contentDescription = "关闭"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }.also { topBar.addView(it) }

        container.addView(topBar)

        // 地图区域
        val mapFrame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(450)
            ).apply {
                topMargin = dp(8)  // 紧贴搜索结果列表下方
            }
        }

        // 高德 MapView
        mapView = MapView(com.mockloc.util.ThemeUtils.createThemedContext(context).first).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }.also { mapFrame.addView(it) }

        // 回到当前位置按钮
        val btnBack = ImageButton(context).apply {
            setImageResource(R.drawable.ic_home_position)
            background = null
            setColorFilter(primaryColor)
            contentDescription = "回到当前位置"
        }.also {
            it.layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
                topMargin = dp(240)
                leftMargin = dp(260)
            }
            mapFrame.addView(it)
        }

        // 确定选点按钮
        btnGo = ImageButton(context).apply {
            setImageResource(R.drawable.ic_position)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(primaryColor)
            }
            setColorFilter(ContextCompat.getColor(context, R.color.on_primary))
            contentDescription = "确定选点"
            elevation = dp(8).toFloat()
        }.also {
            it.layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                bottomMargin = dp(40)
            }
            mapFrame.addView(it)
            
            // 启动脉冲动画
            isPositionConfirmed = false
            btnGoPulseAnimator = AnimationHelper.pulseInfinite(it, 2000)
        }

        // POI搜索结果列表（作为覆盖层显示在地图上）
        searchScroll = ScrollView(context).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            ).apply {
                gravity = Gravity.TOP
                topMargin = dp(0)  // 紧贴 mapFrame 顶部（即搜索框下方）
            }
            // 使用圆角背景，只底部有圆角
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(
                    0f, 0f,  // 左上、右上无圆角
                    0f, 0f,  // 右上、右下无圆角
                    dp(8).toFloat(), dp(8).toFloat(),  // 左下、右下有圆角
                    dp(8).toFloat(), dp(8).toFloat()   // 左下、右下有圆角
                )
            }
            background = bg
            elevation = dp(8).toFloat()  // 提高层级，确保在地图上方
        }

        searchList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }.also { searchScroll?.addView(it) }

        // 将搜索结果列表添加到 mapFrame 中作为覆盖层
        mapFrame.addView(searchScroll)
        container.addView(mapFrame)

        // 排除地图区域的拖动
        container.dragExcludeView = mapFrame

        // 初始化地图
        setupMap()
        
        // 注意：restoreSimulationState() 将在地图加载完成后调用
        // 见 restoreMapState() 方法

        // ===== 搜索框事件 =====
        searchEditText?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                enableSearchFocus()
                v.post {
                    v.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    
                    // 光标移到末尾
                    val textLength = (v as? EditText)?.text?.length ?: 0
                    if (textLength > 0) {
                        (v as? EditText)?.setSelection(textLength)
                    }
                }
            }
            false
        }

        searchEditText?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enableSearchFocus()
            } else {
                disableSearchFocus()
            }
        }

        searchEditText?.setOnEditorActionListener(android.widget.TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    searchPoi(query)
                }
                // 隐藏键盘
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(searchEditText?.windowToken, 0)
                searchEditText?.clearFocus()
            }
            true
        })

        // ===== 按钮事件 =====
        btnClose?.setOnClickListener {
            btnGoPulseAnimator?.cancel()
            isPositionConfirmed = false
            
            disableSearchFocus()
            searchScroll?.visibility = View.GONE
            searchEditText?.setText("")
            searchEditText?.clearFocus()
            
            onSwitchToJoystick()
        }

        btnBack.setOnClickListener {
            resetMapCamera()
        }

        btnGo?.setOnClickListener {
            handleBtnGoClick()
        }

        rootView = container
    }

    /**
     * 初始化地图
     */
    private fun setupMap() {
        mapView?.onCreate(null)
        aMap = mapView?.map
        
        // 初始化定位客户端（让蓝点能够跟随模拟位置移动）
        initLocationClient()
        
        aMap?.apply {
            // 设置地图类型
            mapType = if (isNightMode) {
                val prefs = service.getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)
                prefs.getInt("map_type_night", AMap.MAP_TYPE_NIGHT)
            } else {
                val prefs = service.getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)
                prefs.getInt("map_type_day", AMap.MAP_TYPE_NORMAL)
            }
            
            // 显示定位蓝点
            uiSettings.isMyLocationButtonEnabled = false
            myLocationStyle = MyLocationStyle().apply {
                // 使用 LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER
                // 显示蓝点+方向箭头，但不自动移动相机
                // 这样既能看到朝向，又不会干扰用户选点
                myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                showMyLocation(true)
                // 设置精度圈颜色（与app内部一致）
                strokeColor(ContextCompat.getColor(context, R.color.primary))
                radiusFillColor(ContextCompat.getColor(context, R.color.location_accuracy_fill))
                // 设置边框宽度
                strokeWidth(2f)
            }
            isMyLocationEnabled = true
            
            // 隐藏缩放按钮和指南针
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = false
            
            // 地图点击标记
            setOnMapClickListener { latLng ->
                // 点击地图时关闭搜索列表
                searchScroll?.visibility = View.GONE
                markMapPoint(latLng)
            }
            setOnMapLongClickListener { latLng ->
                // 长按地图时关闭搜索列表
                searchScroll?.visibility = View.GONE
                markMapPoint(latLng)
            }
            
            // 标记拖拽监听
            setOnMarkerDragListener(object : AMap.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {
                    // 拖拽开始
                }

                override fun onMarkerDrag(marker: Marker) {
                    // 拖拽中，更新标记位置
                    markedLatLng = marker.position
                }

                override fun onMarkerDragEnd(marker: Marker) {
                    // 拖拽结束，更新位置
                    markedLatLng = marker.position
                    // 触发脉冲动画
                    if (!isPositionConfirmed) {
                        btnGoPulseAnimator?.cancel()
                        isPositionConfirmed = true
                        btnGo?.setImageResource(R.drawable.ic_fly)
                        btnGo?.let { btnGoPulseAnimator = AnimationHelper.pulseInfinite(it, 1200) }
                    }
                }
            })
            
            // 地图相机变化监听
            setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                override fun onCameraChange(cameraPosition: com.amap.api.maps.model.CameraPosition) {
                    // 相机变化中，不做处理
                }

                override fun onCameraChangeFinish(cameraPosition: com.amap.api.maps.model.CameraPosition) {
                    // 相机变化完成，保存状态
                    val prefs = service.getSharedPreferences(PrefsConfig.MAP_STATE, Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putFloat(PrefsConfig.MapState.KEY_LATITUDE, cameraPosition.target.latitude.toFloat())
                        putFloat(PrefsConfig.MapState.KEY_LONGITUDE, cameraPosition.target.longitude.toFloat())
                        putFloat(PrefsConfig.MapState.KEY_ZOOM, cameraPosition.zoom)
                        apply()
                    }
                    Timber.d("保存地图状态: lat=${cameraPosition.target.latitude}, lng=${cameraPosition.target.longitude}, zoom=${cameraPosition.zoom}")
                }
            })
        }
        
        // 恢复地图状态（包括模拟位置）
        restoreMapState()
    }

    /**
     * 根据夜间模式更新地图类型
     */
    private fun updateMapTypeForNightMode() {
        aMap?.apply {
            mapType = if (isNightMode) {
                val prefs = service.getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)
                prefs.getInt("map_type_night", AMap.MAP_TYPE_NIGHT)
            } else {
                val prefs = service.getSharedPreferences(PrefsConfig.SETTINGS, Context.MODE_PRIVATE)
                prefs.getInt("map_type_day", AMap.MAP_TYPE_NORMAL)
            }
            Timber.d("Map type updated: isNightMode=$isNightMode, mapType=$mapType")
        }
    }

    /**
     * 恢复地图状态
     */
    private fun restoreMapState() {
        try {
            if (aMap == null) {
                Timber.w("aMap 为空，跳过恢复地图状态")
                return
            }
            
            // 读取保存的地图状态
            val prefs = service.getSharedPreferences(PrefsConfig.MAP_STATE, Context.MODE_PRIVATE)
            val lat = prefs.getFloat(PrefsConfig.MapState.KEY_LATITUDE, -1f)
            val lng = prefs.getFloat(PrefsConfig.MapState.KEY_LONGITUDE, -1f)
            val zoom = prefs.getFloat(PrefsConfig.MapState.KEY_ZOOM, 15f)
            
            if (lat > 0 && lng > 0) {
                // 恢复到上次的位置和缩放级别
                val target = com.amap.api.maps.model.LatLng(lat.toDouble(), lng.toDouble())
                aMap?.moveCamera(
                    com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(target, zoom)
                )
                Timber.d("恢复地图状态: lat=$lat, lng=$lng, zoom=$zoom")
            } else {
                // 如果没有保存的状态，定位到当前位置
                Timber.d("无保存的地图状态，等待定位")
            }
            
            // ✅ 恢复标记位置
            val markedLat = prefs.getFloat(PrefsConfig.MapState.KEY_MARKED_LAT, -1f)
            val markedLng = prefs.getFloat(PrefsConfig.MapState.KEY_MARKED_LNG, -1f)
            if (markedLat > 0 && markedLng > 0) {
                val markedPos = com.amap.api.maps.model.LatLng(markedLat.toDouble(), markedLng.toDouble())
                markMapPoint(markedPos)
                Timber.d("恢复标记位置: $markedPos")
            }
        } catch (e: Exception) {
            Timber.e(e, "恢复地图状态失败")
        }
    }

    /**
     * 标记地图点
     */
    private fun markMapPoint(latLng: LatLng) {
        markedLatLng = latLng
        
        // ✅ 保存标记位置到 SP（同步到主界面）
        val prefs = service.getSharedPreferences(PrefsConfig.MAP_STATE, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(PrefsConfig.MapState.KEY_MARKED_LAT, latLng.latitude.toFloat())
            putFloat(PrefsConfig.MapState.KEY_MARKED_LNG, latLng.longitude.toFloat())
            apply()
        }
        Timber.d("保存标记位置: $latLng")
        
        aMap?.apply {
            clear()
            // 使用红色标记（与app内部一致）
            val marker = addMarker(
                MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .draggable(true)  // 支持拖拽
            )
            
            // 选中状态的标记样式
            marker?.let {
                it.setInfoWindowEnable(false)
                it.isFlat = false
            }
        }
        
        // 停止待命脉冲，切换到激活状态
        btnGoPulseAnimator?.cancel()
        isPositionConfirmed = true
        btnGo?.setImageResource(R.drawable.ic_fly)
        btnGo?.let { btnGoPulseAnimator = AnimationHelper.pulseInfinite(it, 1200) }
    }

    /**
     * 处理确定按钮点击
     */
    private fun handleBtnGoClick() {
        val marked = markedLatLng
        
        if (marked != null) {
            // 有新位置：传送到新位置
            btnGoPulseAnimator?.cancel()
            
            if (!isPositionConfirmed) {
                // 第一次确认：切换图标
                btnGo?.animate()
                    ?.scaleX(0.8f)
                    ?.scaleY(0.8f)
                    ?.setDuration(100)
                    ?.withEndAction {
                        btnGo?.setImageResource(R.drawable.ic_fly)
                        btnGo?.setColorFilter(ContextCompat.getColor(context, R.color.on_primary))
                        btnGo?.animate()
                            ?.scaleX(1f)
                            ?.scaleY(1f)
                            ?.setDuration(100)
                            ?.start()
                    }
                    ?.start()
            }
            
            // 执行位置传送
            val wgs = MapUtils.gcj02ToWgs84(marked.longitude, marked.latitude)
            // 修正：gcj02ToWgs84 返回 [经度, 纬度]，而回调参数顺序是 (lat, lng)
            onLocationSelected(wgs[1], wgs[0])
            
            // 地图相机跟随（使用动画）
            val currentZoom = aMap?.cameraPosition?.zoom ?: 18f
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(marked, currentZoom))
            
            markedLatLng = null
            
            // 显示提示
            UIFeedbackHelper.showToast(context, "已传送到新位置")
            
            // 保持激活状态
            isPositionConfirmed = true
            btnGo?.let { btnGoPulseAnimator = AnimationHelper.pulseInfinite(it, 1200) }
            
        } else {
            // 没有新位置：切换激活/去激活状态
            if (isPositionConfirmed) {
                // 激活 -> 去激活
                btnGoPulseAnimator?.cancel()
                isPositionConfirmed = false
                btnGo?.setImageResource(R.drawable.ic_position)
                UIFeedbackHelper.showToast(context, "已停止模拟")
            } else {
                // 去激活 -> 无操作，提示用户先选择位置
                UIFeedbackHelper.showToast(context, "请先在地图上选择一个位置")
            }
        }
    }

    /**
     * 重置地图相机
     */
    private fun resetMapCamera() {
        val (lat, lng) = service.getCurrentLocationGcj02()
        if (lat != 0.0 || lng != 0.0) {
            // ✅ 使用动画平滑移动到当前位置
            aMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f)
            )
        }
    }

    /**
     * 搜索 POI
     */
    private fun searchPoi(query: String) {
        Timber.d("Searching POI: $query")
        
        // 获取当前地图中心点作为搜索中心
        val centerLat = aMap?.cameraPosition?.target?.latitude
        val centerLng = aMap?.cameraPosition?.target?.longitude
        
        poiSearchHelper?.searchPlace(
            keyword = query,
            callback = { results ->
                Timber.d("POI search result count: ${results.size}")
                results.forEachIndexed { index, item ->
                    Timber.d("Result $index: ${item.name}, lat=${item.lat}, lng=${item.lng}")
                }
                showSearchResults(results)
            },
            centerLat = centerLat,
            centerLng = centerLng
            // ✅ 使用默认半径20km（支持自动扩大到50km）
        )
    }

    /**
     * 显示搜索结果
     */
    private fun showSearchResults(results: List<com.mockloc.repository.PoiSearchHelper.PlaceItem>) {
        Timber.d("showSearchResults called with ${results.size} items")
        searchList?.removeAllViews()

        if (results.isEmpty()) {
            Timber.d("No results, hiding search scroll")
            searchScroll?.visibility = View.GONE
            return
        }

        // ✅ 提前检查 searchList 是否为 null，避免无效创建
        val searchListView = searchList ?: run {
            Timber.w("searchList is null, cannot show results")
            return
        }

        // ✅ 性能优化：限制最大显示数量，避免过多 View 影响性能
        // 悬浮窗空间有限，显示太多结果反而影响用户体验
        val maxResults = minOf(results.size, 10)
        val displayResults = results.take(maxResults)
        
        if (results.size > maxResults) {
            Timber.d("Limiting results from ${results.size} to $maxResults for better performance")
        }

        val density = context.resources.displayMetrics.density
        val paddingH = (12 * density).toInt()
        val paddingV = (10 * density).toInt()

        displayResults.forEach { result ->
            val item = TextView(context).apply {
                text = result.name
                textSize = 14f
                setTextColor(textPrimary)
                setPadding(paddingH, paddingV, paddingH, paddingV)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // ✅ 使用 Material Ripple 效果（更可靠）
                try {
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        typedValue,
                        true
                    )
                    setBackgroundResource(typedValue.resourceId)
                } catch (e: Exception) {
                    // 降级方案：设置简单的点击反馈
                    Timber.w(e, "Failed to set ripple background, using fallback")
                    isClickable = true
                    isFocusable = true
                }
            }

            // ✅ 提取点击逻辑，减少 Lambda 创建
            item.setOnClickListener {
                onSearchResultClicked(result)
            }

            searchListView.addView(item)
            Timber.d("Added search result item: ${result.name}")
        }

        searchScroll?.visibility = View.VISIBLE
        Timber.d("Search scroll visibility set to VISIBLE")
    }

    /**
     * 处理搜索结果点击事件
     */
    private fun onSearchResultClicked(result: com.mockloc.repository.PoiSearchHelper.PlaceItem) {
        // 移动到搜索结果位置（使用动画）
        val pos = LatLng(result.lat, result.lng)
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
        markMapPoint(pos)

        // 隐藏搜索列表并清空输入
        searchScroll?.visibility = View.GONE
        searchEditText?.setText("")
        searchEditText?.clearFocus()
    }

    /**
     * 初始化定位客户端
     * 作用：让定位蓝点能够读取系统位置（包括 TestProvider 设置的模拟位置）
     * 参考：MainActivity.kt 中的 initLocation() 实现
     */
    private fun initLocationClient() {
        try {
            // 使用 service 作为 Context，而不是 themedContext
            locationClient = AMapLocationClient(service)
            locationClient?.setLocationOption(AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = false  // 持续定位
                isNeedAddress = false
                // 设置定位间隔（毫秒），持续定位必须设置
                interval = 1000  // 1秒更新一次（最低1000ms）
                // 允许模拟位置
                isMockEnable = true
            })
            
            // 设置定位监听
            locationClient?.setLocationListener { location ->
                if (location != null && location.errorCode == 0) {
                    Timber.d("悬浮窗地图定位成功: ${location.latitude}, ${location.longitude}")
                    // 高德地图会自动根据此位置更新蓝点
                    // 这里不需要手动移动相机，避免干扰用户选点
                } else {
                    Timber.w("悬浮窗地图定位失败: ${location?.errorInfo}")
                }
            }
            
            // 开始定位
            locationClient?.startLocation()
            Timber.d("悬浮窗地图 AMapLocationClient 已启动")
        } catch (e: Exception) {
            Timber.e(e, "悬浮窗地图初始化定位客户端失败")
        }
    }

    /**
     * 启用搜索框焦点
     */
    private fun enableSearchFocus() {
        try {
            windowParams.flags = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            rootView?.let { windowManager.updateViewLayout(it, windowParams) }
        } catch (e: Exception) {
            Timber.w(e, "enableSearchFocus failed")
        }
    }

    /**
     * 禁用搜索框焦点
     */
    private fun disableSearchFocus() {
        try {
            windowParams.flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            rootView?.let { windowManager.updateViewLayout(it, windowParams) }
        } catch (e: Exception) {
            Timber.w(e, "disableSearchFocus failed")
        }
    }
}

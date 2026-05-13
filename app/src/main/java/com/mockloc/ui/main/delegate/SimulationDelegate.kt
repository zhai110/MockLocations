package com.mockloc.ui.main.delegate

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.animation.ObjectAnimator
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import com.mockloc.service.LocationService
import com.mockloc.ui.main.MainViewModel
import com.mockloc.util.AnimationHelper
import com.mockloc.util.UIFeedbackHelper
import timber.log.Timber

/**
 * 模拟控制委托类
 *
 * 职责：
 * - 处理单点定位的启动/停止模拟
 * - 处理路线模式的播放/暂停/停止
 * - 更新模拟 UI（FAB 图标、脉冲动画、状态徽章）
 * - 权限检查和用户反馈
 * - 模拟控制事件处理（启动/停止/传送/更新位置）
 * - 模拟相关按钮点击事件设置
 *
 * Delegate 间通信规则：
 * - 各 Delegate 之间不直接引用，所有跨 Delegate 的状态共享通过 ViewModel 中转
 * - 例如：SimulationDelegate 需要知道当前是否为路线模式，通过 currentTabMode 字段
 *   由 MainFragment 在 Tab 切换时赋值，而非直接引用 RouteEditDelegate
 */
class SimulationDelegate(
    private val fragment: Fragment,
    private val viewModel: MainViewModel,
    private val binding: FragmentMainBinding
) {
    
    private var idlePulseAnimator: ObjectAnimator? = null

    /** 更新地图标记回调（由 MainFragment 提供，因为 aMap 在 MainFragment 中） */
    var onUpdateMarker: ((latLng: com.amap.api.maps.model.LatLng, moveCamera: Boolean) -> Unit)? = null

    /** 保存历史记录回调（由 MainFragment 提供，因为需要 lifecycleScope） */
    var onSaveToHistory: ((latitude: Double, longitude: Double) -> Unit)? = null

    /**
     * 设置模拟控制相关的按钮点击监听器
     *
     * 包括：FAB 按钮、路线播放/速度/停止按钮、速度 Chip、循环按钮、移动模式按钮、清除路线按钮
     */
    fun setupClickListeners() {
        binding.fab.setOnClickListener {
            if (currentTabMode == 0) {
                toggleSimulation()
            } else {
                toggleRoutePlaybackFromFab()
            }
        }

        binding.routePlayFabBtn.setOnClickListener {
            if (viewModel.routeState.value.routePoints.size < 2) {
                UIFeedbackHelper.showToast(fragment.requireContext(), "至少需要2个路线点")
                return@setOnClickListener
            }
            viewModel.toggleRoutePlayback()
        }

        binding.routeSpeedFabBtn.setOnClickListener {
            val currentSpeed = viewModel.routeState.value.playbackState.speedMultiplier
            val nextSpeed = when (currentSpeed) {
                1f -> 2f
                2f -> 4f
                4f -> 0.5f
                else -> 1f
            }
            viewModel.setRouteSpeedMultiplier(nextSpeed)
            UIFeedbackHelper.showToast(fragment.requireContext(), "速度: ${nextSpeed}x")
        }

        binding.routeStopFabBtn.setOnClickListener {
            viewModel.stopRoutePlayback()
        }

        binding.speed05x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(0.5f)
            updateSpeedChipSelection(binding.speed05x)
        }
        binding.speed1x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(1f)
            updateSpeedChipSelection(binding.speed1x)
        }
        binding.speed2x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(2f)
            updateSpeedChipSelection(binding.speed2x)
        }
        binding.speed4x.setOnClickListener {
            viewModel.setRouteSpeedMultiplier(4f)
            updateSpeedChipSelection(binding.speed4x)
        }

        binding.routeLoopBtn.setOnClickListener {
            val isLooping = !viewModel.routeState.value.playbackState.isLooping
            viewModel.setRouteLooping(isLooping)
        }

        binding.routeMovementModeBtn.setOnClickListener {
            viewModel.toggleMovementMode()
        }

        binding.routeClearBtn.setOnClickListener {
            viewModel.clearRoute()
        }
    }

    /**
     * 启动位置模拟
     *
     * 通过 Intent 与 [LocationService] 通信，坐标为 GCJ-02，
     * Service 内部自动转换为 WGS-84 后注入 Mock Location。
     * 同时通过回调保存位置到历史记录，并更新地图标记。
     *
     * @param latitude 纬度（GCJ-02）
     * @param longitude 经度（GCJ-02）
     * @param altitude 海拔
     */
    fun startSimulation(latitude: Double, longitude: Double, altitude: Float) {
        try {
            onSaveToHistory?.invoke(latitude, longitude)

            Timber.d("Starting simulation: lat=$latitude, lng=$longitude (GCJ-02)")

            val intent = android.content.Intent(fragment.requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_START
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                putExtra(LocationService.EXTRA_COORD_GCJ02, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fragment.requireContext().startForegroundService(intent)
            } else {
                fragment.requireContext().startService(intent)
            }

            onUpdateMarker?.invoke(com.amap.api.maps.model.LatLng(latitude, longitude), true)

            val wgs84 = com.mockloc.util.MapUtils.gcj02ToWgs84(longitude, latitude)
            Timber.d("Red marker (GCJ-02): ($latitude, $longitude)")
            Timber.d("Blue dot expected (WGS-84 injected): (${wgs84[1]}, ${wgs84[0]})")

            UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_simulation_started))
        } catch (e: Exception) {
            Timber.e(e, "启动模拟失败")
            UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_simulation_start_failed, e.message))
        }
    }

    /**
     * 停止位置模拟，向 [LocationService] 发送停止指令
     */
    fun stopSimulation() {
        try {
            val intent = android.content.Intent(fragment.requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
            }
            fragment.requireContext().startService(intent)
            UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_simulation_stopped))
        } catch (e: Exception) {
            Timber.e(e, "停止模拟失败")
            UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_simulation_stop_failed, e.message))
        }
    }

    /**
     * 传送位置（单点模式）
     *
     * 坐标来自地图点击（GCJ-02），传给 [LocationService] 时标记为 GCJ-02，
     * Service 内部自动转换为 WGS-84 后注入 Mock Location。
     *
     * @param latitude 纬度（GCJ-02）
     * @param longitude 经度（GCJ-02）
     * @param altitude 海拔
     */
    fun teleportToPosition(latitude: Double, longitude: Double, altitude: Float) {
        try {
            val intent = android.content.Intent(fragment.requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_UPDATE
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                putExtra(LocationService.EXTRA_COORD_GCJ02, true)
            }
            fragment.requireContext().startService(intent)
            onUpdateMarker?.invoke(com.amap.api.maps.model.LatLng(latitude, longitude), true)
            UIFeedbackHelper.showToast(fragment.requireContext(), "已传送到新位置")
        } catch (e: Exception) {
            Timber.e(e, "传送失败")
            UIFeedbackHelper.showToast(fragment.requireContext(), "传送失败: ${e.message}")
        }
    }

    /**
     * 更新模拟位置（摇杆/路线模式）
     *
     * 坐标来自 LocationService 内部（已是 WGS-84），不需要再转换，
     * 传给 [LocationService] 时标记 EXTRA_COORD_GCJ02=false。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param altitude 海拔
     */
    fun updatePosition(latitude: Double, longitude: Double, altitude: Float) {
        try {
            val intent = android.content.Intent(fragment.requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_UPDATE
                putExtra(LocationService.EXTRA_LATITUDE, latitude)
                putExtra(LocationService.EXTRA_LONGITUDE, longitude)
                putExtra(LocationService.EXTRA_ALTITUDE, altitude.toDouble())
                putExtra(LocationService.EXTRA_COORD_GCJ02, false)
            }
            fragment.requireContext().startService(intent)
            onUpdateMarker?.invoke(com.amap.api.maps.model.LatLng(latitude, longitude), true)
        } catch (e: Exception) {
            Timber.e(e, "更新位置失败")
            UIFeedbackHelper.showToast(fragment.requireContext(), "更新位置失败: ${e.message}")
        }
    }

    /**
     * 处理模拟控制事件，由 [MainViewModel.simulationControlEvents] 触发
     *
     * 事件类型：
     * - START_SIMULATION → [startSimulation]：启动位置模拟
     * - STOP_SIMULATION → [stopSimulation]：停止位置模拟
     * - UPDATE_POSITION → [teleportToPosition] + 保存历史记录：模拟中手动传送
     */
    fun handleSimulationControlEvent(event: MainViewModel.SimulationControlEvent) {
        Timber.d("handleSimulationControlEvent: eventType=${event.eventType}")
        when (event.eventType) {
            MainViewModel.SimulationControlEvent.EventType.START_SIMULATION -> {
                startSimulation(event.latitude!!, event.longitude!!, event.altitude)
            }
            MainViewModel.SimulationControlEvent.EventType.STOP_SIMULATION -> {
                stopSimulation()
            }
            MainViewModel.SimulationControlEvent.EventType.UPDATE_POSITION -> {
                teleportToPosition(event.latitude!!, event.longitude!!, event.altitude)
                onSaveToHistory?.invoke(event.latitude, event.longitude)
            }
        }
    }
    
    /**
     * 更新模拟 UI
     * @param state 模拟状态
     * @param currentTabMode 当前 Tab 模式（0=单点模式，1=路线模式）
     */
    fun updateSimulationUI(state: MainViewModel.SimulationState, currentTabMode: Int) {
        // 单点/路线模式统一风格：模拟中 → ic_fly，未模拟 → ic_position + 脉冲动画
        if (state.isSimulating) {
            binding.fab.setImageResource(R.drawable.ic_fly)
            binding.fab.imageTintList = null
            idlePulseAnimator?.cancel()
            idlePulseAnimator = null
            binding.statusText.text = "模拟中"
        } else {
            binding.fab.setImageResource(R.drawable.ic_position)
            binding.fab.imageTintList = null
            if (idlePulseAnimator == null) {
                idlePulseAnimator = AnimationHelper.pulseInfinite(binding.fab, 2000)
            }
            binding.statusText.text = "未模拟"
        }
    }
    
    /**
     * 获取脉冲动画（供 MainFragment 在 onDestroyView 时清理）
     */
    fun getIdlePulseAnimator(): ObjectAnimator? = idlePulseAnimator
    
    /**
     * 清理动画资源
     */
    fun cleanup() {
        idlePulseAnimator?.cancel()
        idlePulseAnimator = null
    }
    
    /**
     * 切换模拟状态（单点模式）
     */
    fun toggleSimulation() {
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onPermissionCheckNeeded?.invoke()
            return
        }
        
        val mapState = viewModel.mapState.value
        val simState = viewModel.simulationState.value
        
        if (!simState.isSimulating && mapState.markedPosition == null) {
            UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_please_select_location))
            return
        }

        viewModel.confirmSimulation()
    }
    
    /**
     * 从 FAB 按钮触发路线播放/暂停
     */
    fun toggleRoutePlaybackFromFab() {
        val routeState = viewModel.routeState.value
        
        if (routeState.routePoints.size < 2) {
            UIFeedbackHelper.showToast(fragment.requireContext(), "至少需要2个路线点")
            return
        }
        
        viewModel.toggleRoutePlayback()
    }
    
    /**
     * 更新速度 Chip 的选择状态
     */
    fun updateSpeedChipSelection(selected: Chip) {
        listOf(binding.speed05x, binding.speed1x, binding.speed2x, binding.speed4x).forEach {
            it.isChecked = (it == selected)
        }
    }
    
    /**
     * 权限检查回调（由 MainFragment 提供）
     *
     * 设计原因：Fragment 的 requestPermissions / registerForActivityResult 等
     * 权限请求 API 必须在 Fragment 生命周期内调用，Delegate 无法独立发起权限请求。
     * 因此通过回调将权限检查委托给 MainFragment 执行。
     */
    var onPermissionCheckNeeded: (() -> Unit)? = null
    
    /**
     * 当前 Tab 模式（0=单点模式，1=路线模式）
     *
     * 由 MainFragment 在 Tab 切换时赋值，用于决定 FAB 按钮的行为：
     * - 单点模式下点击 FAB 触发 toggleSimulation()
     * - 路线模式下点击 FAB 触发 toggleRoutePlaybackFromFab()
     */
    var currentTabMode: Int = 0
}

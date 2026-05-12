package com.mockloc.ui.main.delegate

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.animation.ObjectAnimator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
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
    
    /**
     * 更新模拟 UI
     * @param state 模拟状态
     * @param currentTabMode 当前 Tab 模式（0=单点模式，1=路线模式）
     */
    fun updateSimulationUI(state: MainViewModel.SimulationState, currentTabMode: Int) {
        if (currentTabMode == 0) {
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
        // 检查定位权限
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 需要调用外部的 checkPermissions 方法，这里通过回调实现
            onPermissionCheckNeeded?.invoke()
            return
        }
        
        val mapState = viewModel.mapState.value
        val simState = viewModel.simulationState.value
        
        // 未模拟时需要检查是否有标记位置
        if (!simState.isSimulating && mapState.markedPosition == null) {
            UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_please_select_location))
            return
        }

        // 调用 ViewModel 确认模拟（事件处理器会显示 Toast，这里不需要再显示）
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
        
        // 调用 ViewModel 的 toggleRoutePlayback
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

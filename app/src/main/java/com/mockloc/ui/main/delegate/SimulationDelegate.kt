package com.mockloc.ui.main.delegate

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import com.mockloc.ui.main.MainViewModel
import com.mockloc.util.UIFeedbackHelper
import timber.log.Timber

/**
 * 模拟控制委托类
 * 
 * 职责：
 * - 处理单点定位的启动/停止模拟
 * - 处理路线模式的播放/暂停/停止
 * - 更新速度选择 Chip 的状态
 * - 权限检查和用户反馈
 */
class SimulationDelegate(
    private val fragment: Fragment,
    private val viewModel: MainViewModel,
    private val binding: FragmentMainBinding
) {
    
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
     */
    var onPermissionCheckNeeded: (() -> Unit)? = null
}

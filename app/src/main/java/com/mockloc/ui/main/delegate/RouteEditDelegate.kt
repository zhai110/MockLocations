package com.mockloc.ui.main.delegate

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import com.mockloc.ui.main.MainViewModel
import com.mockloc.util.AnimationHelper
import timber.log.Timber

/**
 * 路线编辑委托类
 *
 * 职责：
 * - 管理路线点和折线的显示/更新
 * - 处理路线点的点击编辑（显示/隐藏编辑按钮）
 * - 删除选中的路线点
 * - 创建路线点标记图标
 * - 响应路线状态变化
 *
 * 坐标系说明：
 * - 路线点使用 GCJ-02 坐标系（国测局坐标），与高德地图一致
 * - 无需额外的坐标转换即可直接用于 AMap 的 Marker 和 Polyline
 *
 * 设计说明：
 * - 本 Delegate 不直接持有 AMap 引用，通过 onGetAMap 回调获取，
 *   避免与地图生命周期强耦合
 */
class RouteEditDelegate(
    private val fragment: Fragment,
    private val viewModel: MainViewModel,
    private val binding: FragmentMainBinding
) {
    
    private var routePolyline: Polyline? = null
    private var routePointMarkers: MutableList<Marker> = mutableListOf()
    private var selectedPointIndex: Int = -1

    /**
     * 上一次路线点列表的 hashCode，用于判断路线点是否发生变化
     *
     * 避免重复绘制：当 ViewModel 状态通知到达但路线点实际未改变时，
     * 跳过清除+重绘操作，减少不必要的 Marker/Polyline 创建和地图刷新
     */
    private var lastRoutePointsHash: Int = 0
    
    /**
     * 更新路线 UI（响应 ViewModel 状态变化）
     */
    fun updateRouteUI(state: MainViewModel.RouteState) {
        // 需要外部传入 aMap 实例，这里通过回调获取
        val aMap = onGetAMap?.invoke() ?: return
        
        val pointsHash = state.routePoints.hashCode()
        if (pointsHash != lastRoutePointsHash) {
            lastRoutePointsHash = pointsHash
            clearRouteDisplay(aMap)
            
            // 绘制路线折线
            if (state.routePoints.size >= 2) {
                val points = state.routePoints.map { it.latLng }
                routePolyline = aMap.addPolyline(PolylineOptions()
                    .addAll(points)
                    .width(8f)
                    .color(ContextCompat.getColor(fragment.requireContext(), R.color.primary))
                    .geodesic(true))
            }
            
            // 添加路线点标记
            state.routePoints.forEachIndexed { index, point ->
                val label = when {
                    index == 0 -> "起"
                    index == state.routePoints.size - 1 && state.routePoints.size > 1 -> "终"
                    else -> "${index + 1}"
                }
                val bgColor = when {
                    index == 0 -> ContextCompat.getColor(fragment.requireContext(), R.color.route_point_start)
                    index == state.routePoints.size - 1 && state.routePoints.size > 1 -> ContextCompat.getColor(fragment.requireContext(), R.color.route_point_end)
                    else -> ContextCompat.getColor(fragment.requireContext(), R.color.route_point_middle)
                }
                val marker = aMap.addMarker(MarkerOptions()
                    .position(point.latLng)
                    .icon(createRoutePointIcon(label, bgColor))
                    .anchor(0.5f, 1.0f)
                    .draggable(false))
                
                routePointMarkers.add(marker)
            }
            
            // 设置全局 Marker 点击监听器
            setupMarkerClickListener(aMap)
        }
        
        // 更新路线点计数
        val pointCount = state.routePoints.size
        binding.routePointCount.text = "$pointCount 个点"
        
        // 更新播放状态相关 UI
        updatePlaybackUI(state)
    }
    
    /**
     * 清除路线显示
     */
    private fun clearRouteDisplay(aMap: AMap) {
        routePolyline?.remove()
        routePolyline = null
        routePointMarkers.forEach { it.remove() }
        routePointMarkers.clear()
    }
    
    /**
     * 设置 Marker 点击监听器
     */
    private fun setupMarkerClickListener(aMap: AMap) {
        aMap.setOnMarkerClickListener { clickedMarker ->
            val clickedIndex = routePointMarkers.indexOf(clickedMarker)
            if (clickedIndex >= 0) {
                showRoutePointEditButtons(clickedIndex)
                true
            } else {
                false
            }
        }
    }
    
    /**
     * 更新播放状态相关 UI
     */
    private fun updatePlaybackUI(state: MainViewModel.RouteState) {
        val playback = state.playbackState
        
        if (playback.isPlaying) {
            binding.routePlayFabBtn.setImageResource(R.drawable.ic_stop)
            binding.routeProgressSection.visibility = View.VISIBLE
            binding.routeProgress.progress = (playback.progress * 100).toInt()
        } else {
            binding.routePlayFabBtn.setImageResource(R.drawable.ic_play)
            binding.routeProgressSection.visibility = View.VISIBLE
            binding.routeProgress.progress = (playback.progress * 100).toInt()
        }
        
        // 更新循环按钮颜色
        val loopColor = if (playback.isLooping) R.color.primary else R.color.text_hint
        binding.routeLoopBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(fragment.requireContext(), loopColor)
        )
        
        // 更新移动模式图标
        val movementIconRes = when (state.movementMode) {
            MainViewModel.MovementMode.WALK -> R.drawable.ic_walk
            MainViewModel.MovementMode.RUN -> R.drawable.ic_run
            MainViewModel.MovementMode.BIKE -> R.drawable.ic_bike
        }
        binding.routeMovementModeBtn.setImageResource(movementIconRes)
    }
    
    /**
     * 显示路线点编辑按钮
     */
    fun showRoutePointEditButtons(index: Int) {
        hideRoutePointEditButtons()
        
        selectedPointIndex = index
        
        binding.routePointEditContainer.visibility = View.VISIBLE
        AnimationHelper.slideUp(binding.routePointEditContainer, 200)
    }
    
    /**
     * 隐藏路线点编辑按钮
     */
    fun hideRoutePointEditButtons() {
        selectedPointIndex = -1
        binding.routePointEditContainer.visibility = View.GONE
    }
    
    /**
     * 删除选中的路线点
     */
    fun deleteSelectedRoutePoint() {
        if (selectedPointIndex < 0) return
        
        val routeState = viewModel.routeState.value
        val pointCount = routeState.routePoints.size
        val pointLabel = when {
            selectedPointIndex == 0 -> "起点"
            selectedPointIndex == pointCount - 1 -> "终点"
            else -> "第 ${selectedPointIndex + 1} 个点"
        }
        
        viewModel.removeRoutePointAt(selectedPointIndex)
        hideRoutePointEditButtons()
        
        com.mockloc.util.UIFeedbackHelper.showToast(fragment.requireContext(), "已删除 $pointLabel")
    }
    
    /**
     * 创建路线点标记图标
     *
     * 绘制水滴形标记，整体结构为：上方圆形区域 + 下方锥形尖端。
     * 圆形区域内居中显示序号文字（起点显示"起"，终点显示"终"，中间点显示序号）。
     *
     * 绘制逻辑：
     * 1. 计算圆形与锥形交汇处的切线角度（halfAngleRad），确定弧线起止角度
     * 2. 从尖端底部 (cx, tipY) 出发，用三次贝塞尔曲线（cubicTo）平滑过渡到圆形右侧
     * 3. 沿圆形画弧线（arcTo），跨越顶部回到左侧
     * 4. 再用三次贝塞尔曲线从左侧平滑过渡回尖端底部
     * 5. 填充背景色，描边半透明白色边框，最后在圆形中心绘制文字标签
     *
     * @param label 标记文字（"起"、"终" 或序号字符串）
     * @param bgColor 背景填充色（起点/终点/中间点使用不同颜色）
     * @return 用于 AMap Marker 的 BitmapDescriptor
     */
    private fun createRoutePointIcon(label: String, bgColor: Int): com.amap.api.maps.model.BitmapDescriptor {
        val dp = fragment.resources.displayMetrics.density
        val circleR = 10f * dp
        val tipH = 8f * dp
        val pad = 2f * dp
        val w = (circleR * 2 + pad * 2).toInt()
        val h = (circleR * 2 + tipH + pad * 2).toInt()
        val cx = w / 2f
        val cy = circleR + pad
        val tipY = cy + circleR + tipH

        val dist = tipY - cy
        val halfAngleRad = Math.asin((circleR / dist).toDouble())
        val halfAngleDeg = Math.toDegrees(halfAngleRad).toFloat()
        val rightX = cx + circleR * Math.sin(halfAngleRad).toFloat()
        val rightY = cy + circleR * Math.cos(halfAngleRad).toFloat()
        val leftX = cx - circleR * Math.sin(halfAngleRad).toFloat()
        val leftY = rightY

        val arcStart = 90f - halfAngleDeg
        val arcSweep = -(360f - halfAngleDeg * 2)

        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        val shape = android.graphics.Path()
        shape.moveTo(cx, tipY)
        val ctrl1x = cx
        val ctrl1y = tipY - (tipY - rightY) * 0.6f
        val ctrl2x = rightX - (rightX - cx) * 0.15f
        val ctrl2y = rightY + (rightY - cy) * 0.15f
        shape.cubicTo(ctrl1x, ctrl1y, ctrl2x, ctrl2y, rightX, rightY)
        shape.arcTo(android.graphics.RectF(cx - circleR, cy - circleR, cx + circleR, cy + circleR), arcStart, arcSweep)
        val ctrl3x = leftX + (leftX - cx) * (-0.15f)
        val ctrl3y = leftY + (leftY - cy) * 0.15f
        val ctrl4x = cx
        val ctrl4y = tipY - (tipY - leftY) * 0.6f
        shape.cubicTo(ctrl3x, ctrl3y, ctrl4x, ctrl4y, cx, tipY)
        shape.close()

        paint.color = bgColor
        canvas.drawPath(shape, paint)

        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1.5f * dp
        paint.color = 0x33FFFFFF
        canvas.drawPath(shape, paint)

        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 11f * dp
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.isFakeBoldText = true
        val fm = paint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, cx, textY, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    
    /**
     * 设置路线点编辑相关的按钮点击监听器
     *
     * 从 MainFragment.setupClickListeners() 迁移而来，
     * 将路线点编辑按钮的点击逻辑集中到本 Delegate 中管理。
     *
     * 包含：
     * - 删除选中的路线点按钮
     * - 取消选择路线点按钮
     */
    fun setupClickListeners() {
        binding.btnDeleteRoutePoint.setOnClickListener {
            deleteSelectedRoutePoint()
        }

        binding.btnCancelSelect.setOnClickListener {
            hideRoutePointEditButtons()
        }
    }

    /**
     * 获取 AMap 实例的回调（由 MainFragment 提供）
     *
     * 设计原因：Delegate 不直接持有 AMap 引用。AMap 的生命周期与 MapView/Fragment 紧密绑定，
     * 直接持有容易导致地图销毁后仍操作已释放的资源。通过回调在需要时获取当前有效的 AMap 实例，
     * 若地图未就绪或已销毁则回调返回 null，安全跳过绘制操作。
     *
     * @return 当前有效的 AMap 实例，若地图未就绪则返回 null
     */
    var onGetAMap: (() -> AMap?)? = null
}

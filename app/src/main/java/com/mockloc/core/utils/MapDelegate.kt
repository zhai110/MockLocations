package com.mockloc.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions

/**
 * 地图操作共享层
 *
 * 消除 MainFragment 和 MapWindowController 之间的地图逻辑重复。
 * 只抽取真正重复的基础操作：
 * - Marker 管理（创建/更新/删除）
 * - 相机控制（animate/move）
 * - 夜间模式切换
 * - 路线点图标生成
 *
 * 注意：不包含业务逻辑（点击事件处理、搜索等由各自实现）
 */
class MapDelegate(private val aMap: AMap) {

    private var currentMarker: Marker? = null

    // ==================== Marker 管理 ====================

    /**
     * 更新或创建标记点（优化：复用现有Marker避免闪烁）
     * MainFragment 使用此方法（增量更新）
     */
    fun updateMarker(position: LatLng, moveCamera: Boolean = false, draggable: Boolean = true) {
        if (currentMarker != null) {
            // 复用现有 Marker，仅更新位置（避免 remove/add 导致的闪烁）
            currentMarker!!.position = position
        } else {
            currentMarker = aMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .draggable(draggable)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .anchor(0.5f, 1.0f)
            )
        }

        if (moveCamera) {
            val currentZoom = aMap.cameraPosition.zoom
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, currentZoom))
        }
    }

    /**
     * 清除地图并重新添加标记点
     * MapWindowController 使用此方法（简单替换）
     * 注意：aMap.clear() 会移除所有覆盖物
     */
    fun replaceMarker(position: LatLng, draggable: Boolean = true): Marker? {
        aMap.clear()
        currentMarker = aMap.addMarker(
            MarkerOptions()
                .position(position)
                .draggable(draggable)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .infoWindowEnable(false)
        )
        return currentMarker
    }

    /**
     * 移除当前标记点
     */
    fun clearMarker() {
        currentMarker?.remove()
        currentMarker = null
    }

    /**
     * 获取当前标记点位置
     */
    fun getMarkerPosition(): LatLng? = currentMarker?.position

    // ==================== 相机控制 ====================

    fun animateCamera(position: LatLng, zoom: Float? = null) {
        val targetZoom = zoom ?: aMap.cameraPosition.zoom
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, targetZoom))
    }

    fun animateCameraWithDuration(position: LatLng, zoom: Float, durationMs: Int) {
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom), durationMs.toLong(), null)
    }

    fun moveCamera(position: LatLng, zoom: Float = 15f) {
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom))
    }

    fun zoomIn() {
        aMap.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        aMap.animateCamera(CameraUpdateFactory.zoomOut())
    }

    val currentZoom: Float
        get() = aMap.cameraPosition.zoom

    // ==================== 地图样式 ====================

    /**
     * 切换夜间/白天地图类型
     * @param isNight 是否夜间模式
     * @param nightMapType 夜间地图类型（默认 AMap.MAP_TYPE_NIGHT）
     * @param dayMapType 白天地图类型（默认 AMap.MAP_TYPE_NORMAL）
     */
    fun setNightMode(isNight: Boolean, nightMapType: Int = AMap.MAP_TYPE_NIGHT, dayMapType: Int = AMap.MAP_TYPE_NORMAL) {
        val targetMapType = if (isNight) nightMapType else dayMapType
        if (aMap.mapType != targetMapType) {
            aMap.mapType = targetMapType
        }
    }

    // ==================== UI 设置 ====================

    /**
     * 配置默认 UI 设置（缩放按钮、比例尺等）
     */
    fun setupDefaultSettings(
        showLocationButton: Boolean = true,
        zoomControlsEnabled: Boolean = false,
        compassEnabled: Boolean = false,
        scaleControlsEnabled: Boolean = true
    ) {
        aMap.uiSettings?.apply {
            isZoomControlsEnabled = zoomControlsEnabled
            isMyLocationButtonEnabled = showLocationButton
            isCompassEnabled = compassEnabled
            isScaleControlsEnabled = scaleControlsEnabled
        }
    }

    // ==================== 路线点图标生成 ====================

    /**
     * 生成带数字标签的路线点图标
     * 从 MainFragment.createRoutePointIcon() 提取的共享逻辑
     */
    fun createRoutePointIcon(label: String, bgColor: Int, density: Float): BitmapDescriptor {
        val dp = density
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

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val shape = Path()
        shape.moveTo(cx, tipY)
        val ctrl1x = cx
        val ctrl1y = tipY - (tipY - rightY) * 0.6f
        val ctrl2x = rightX - (rightX - cx) * 0.15f
        val ctrl2y = rightY + (rightY - cy) * 0.15f
        shape.cubicTo(ctrl1x, ctrl1y, ctrl2x, ctrl2y, rightX, rightY)
        shape.arcTo(RectF(cx - circleR, cy - circleR, cx + circleR, cy + circleR), arcStart, arcSweep)
        val ctrl3x = leftX + (leftX - cx) * (-0.15f)
        val ctrl3y = leftY + (leftY - cy) * 0.15f
        val ctrl4x = cx
        val ctrl4y = tipY - (tipY - leftY) * 0.6f
        shape.cubicTo(ctrl3x, ctrl3y, ctrl4x, ctrl4y, cx, tipY)
        shape.close()

        paint.color = bgColor
        canvas.drawPath(shape, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * dp
        paint.color = 0x33FFFFFF
        canvas.drawPath(shape, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 11f * dp
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        val fm = paint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, cx, textY, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // ==================== 清理 ====================

    /**
     * 清理所有监听器和引用
     * 在 Fragment.onDestroyView() 或 Controller.destroy() 中调用
     */
    fun cleanup() {
        aMap.setOnMapClickListener(null)
        aMap.setOnMapLongClickListener(null)
        aMap.setOnMarkerDragListener(null)
        clearMarker()
    }
}

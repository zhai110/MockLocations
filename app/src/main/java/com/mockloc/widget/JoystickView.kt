package com.mockloc.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.mockloc.R
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 摇杆控件 — 参考项目 RockerView 改进版
 *
 * 两种模式（点击内圆切换）：
 * - 锁定模式(isAuto=true，默认)：松手后摇杆不回弹，持续自动移动
 * - 自由模式(isAuto=false)：松手后摇杆回弹中心，停止移动
 *
 * 角度计算（与参考项目 RockerView 一致）：
 * angle = atan2(innerCenterX - viewCenterX, innerCenterY - viewCenterY) - 90°
 * r = distance / (outerRadius - innerRadius)   // 归一化 0~1
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== 视图尺寸 ====================
    private var viewCenterX = 0f
    private var viewCenterY = 0f
    private var outerCircleRadius = 0
    private var innerCircleRadius = 0

    // 内圆（手柄）中心
    private var innerCenterX = 0f
    private var innerCenterY = 0f

    // ==================== 画笔 ====================
    private val outerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ==================== 锁定图标 ====================
    private var rockerBitmap: Bitmap? = null
    private var isAuto = true  // 默认锁定模式
    private var isClick = false  // 是否为单击（没有MOVE过）

    // ==================== 方向回调 ====================
    private var onMoveListener: ((auto: Boolean, angle: Double, r: Double) -> Unit)? = null
    
    // ==================== 触觉反馈配置 ====================
    private var hapticEnabled = true  // 是否启用触觉反馈（可从设置读取）
    private val hapticDistanceThreshold = 40f  // 每移动40dp触发一次（极其轻微）
    private val hapticTimeThreshold = 100L  // 最小时间间隔100ms（极低频率）
    private var lastHapticDistance = 0f
    private var lastHapticTime = 0L

    init {
        initPaint()
        loadLockIcon()
    }

    private fun initPaint() {
        outerCirclePaint.apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.joystick_base)
            alpha = 180
        }
        innerCirclePaint.apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.joystick_stick_start)
            alpha = 180
        }
        iconPaint.apply {
            alpha = 200
            isFilterBitmap = true
        }
    }

    private fun loadLockIcon() {
        val resId = if (isAuto) R.drawable.ic_lock_close else R.drawable.ic_lock_open
        val bitmap = getBitmapFromDrawable(resId)
        bitmap?.let {
            // innerCircleRadius 在 onMeasure 之前为 0，此时跳过缩放
            // onMeasure 后会重新调用 loadLockIcon()
            val size = (innerCircleRadius * 1.2f).toInt()  // 图标占内圆直径的 60%
            if (size <= 0) return@let
            val scaled = Bitmap.createScaledBitmap(it, size, size, true)
            rockerBitmap?.recycle()
            rockerBitmap = scaled
        }
        // 锁定=主题色，解锁=灰色（与 ButtonView 统一）
        val color = if (isAuto) {
            ContextCompat.getColor(context, R.color.joystick_stick_start)
        } else {
            ContextCompat.getColor(context, R.color.on_surface_variant)
        }
        iconPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun getBitmapFromDrawable(drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        return when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            is VectorDrawable -> {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            else -> null
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = measuredWidth
        setMeasuredDimension(size, size)

        viewCenterX = size / 2f
        viewCenterY = size / 2f
        innerCenterX = viewCenterX
        innerCenterY = viewCenterY
        outerCircleRadius = size / 2
        innerCircleRadius = size / 5

        // 重新加载图标（尺寸变了）
        loadLockIcon()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 外圆
        canvas.drawCircle(viewCenterX, viewCenterY, outerCircleRadius.toFloat(), outerCirclePaint)

        // 内圆（手柄）
        canvas.drawCircle(innerCenterX, innerCenterY, innerCircleRadius.toFloat(), innerCirclePaint)

        // 中心图标
        rockerBitmap?.let { bmp ->
            val left = innerCenterX - bmp.width / 2f
            val top = innerCenterY - bmp.height / 2f
            canvas.drawBitmap(bmp, left, top, iconPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 只有触摸点在外圆范围内才响应（参考项目 RockerView）
                val dx = event.x - viewCenterX
                val dy = event.y - viewCenterY
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > outerCircleRadius) {
                    return false  // 触摸点在外圆之外，不消费事件
                }
                isClick = true
            }
            MotionEvent.ACTION_MOVE -> {
                moveToPosition(event.x, event.y)
                isClick = false
            }
            MotionEvent.ACTION_UP -> {
                if (isClick) {
                    isClick = false
                    toggleLockMode()
                    invalidate()
                }
                // 自由模式下松手回弹
                if (!isAuto) {
                    moveToPosition(viewCenterX, viewCenterY)
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * 移动手柄到指定位置
     * 参考项目 RockerView.moveToPosition()
     */
    private fun moveToPosition(x: Float, y: Float) {
        val dx = x - viewCenterX
        val dy = y - viewCenterY
        val distance = sqrt(dx * dx + dy * dy)
        val maxDistance = (outerCircleRadius - innerCircleRadius).toFloat()

        if (distance < maxDistance) {
            // 在自由域内，触摸点直接作为内圆中心
            innerCenterX = x
            innerCenterY = y
        } else {
            // 超出范围，内圆中心在触摸点与外圆中心的连线上
            val ratio = maxDistance / distance
            innerCenterX = dx * ratio + viewCenterX
            innerCenterY = dy * ratio + viewCenterY
        }

        // 计算角度和归一化距离 r（与参考项目一致）
        val angle = Math.toDegrees(
            atan2(
                (innerCenterX - viewCenterX).toDouble(),
                (innerCenterY - viewCenterY).toDouble()
            )
        ) - 90.0

        val r = sqrt(
            Math.pow((innerCenterX - viewCenterX).toDouble(), 2.0) +
            Math.pow((innerCenterY - viewCenterY).toDouble(), 2.0)
        ) / maxDistance

        // 触发触觉反馈
        triggerHapticFeedback(distance)

        onMoveListener?.invoke(isAuto, angle, r)
        invalidate()
    }

    /**
     * 切换锁定/自由模式
     */
    private fun toggleLockMode() {
        isAuto = !isAuto
        loadLockIcon()
        
        // 重置触觉反馈状态
        resetHapticState()

        if (!isAuto) {
            // 切换到自由模式，通知停止自动移动
            onMoveListener?.invoke(false, 0.0, 0.0)
        }
    }

    /**
     * 设置方向回调
     * @param listener (auto, angle, r) — auto=是否锁定自动移动, angle=角度(度), r=归一化距离(0~1)
     */
    fun setOnMoveListener(listener: (auto: Boolean, angle: Double, r: Double) -> Unit) {
        onMoveListener = listener
    }

    fun isAutoMode(): Boolean = isAuto
    
    /**
     * 触发触觉反馈（优化版）
     */
    private fun triggerHapticFeedback(currentDistance: Float) {
        if (!hapticEnabled) return
        
        val currentTime = System.currentTimeMillis()
        val distanceDelta = kotlin.math.abs(currentDistance - lastHapticDistance)
        val timeDelta = currentTime - lastHapticTime
        
        // 同时满足距离和时间阈值才触发
        if (distanceDelta >= hapticDistanceThreshold && 
            timeDelta >= hapticTimeThreshold) {
            
            // 使用 VIRTUAL_KEY，震动较轻柔
            performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            
            lastHapticDistance = currentDistance
            lastHapticTime = currentTime
        }
    }
    
    /**
     * 设置是否启用触觉反馈
     */
    fun setHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }
    
    /**
     * 重置触觉反馈状态（摇杆回弹时调用）
     */
    private fun resetHapticState() {
        lastHapticDistance = 0f
        lastHapticTime = 0L
    }
}

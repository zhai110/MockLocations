package com.mockloc.widget

import android.content.Context
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.mockloc.R

/**
 * 八方向按钮摇杆（参考 gogogo-master ButtonView.java）
 *
 * 3×3 九宫格布局：
 *   左上  上  右上
 *   左   锁定  右
 *   左下  下  右下
 *
 * 回调接口与 JoystickView 一致: (auto, angle, r)
 * - auto=true: 锁定模式，持续自动移动
 * - auto=false: 自由模式，单次移动
 */
class ButtonView(context: Context) : LinearLayout(context) {

    interface ButtonViewClickListener {
        fun clickAngleInfo(auto: Boolean, angle: Double, r: Double)
    }

    var listener: ButtonViewClickListener? = null

    // 锁定状态
    private var isLocked = true  // 默认锁定模式

    // 当前选中方向（-1=无, 0=北, 1=东北, 2=东, ...）
    private var activeDirection = -1

    private val btnSize = dp(32)
    // 从主题资源获取颜色，支持深色模式
    private var themeColor = ContextCompat.getColor(context, R.color.joystick_button_theme)
    private val iconColor = ContextCompat.getColor(context, R.color.on_surface)

    private lateinit var btnLock: ImageButton
    private val buttons = arrayOfNulls<ImageButton>(8)

    // 方向图标: 北, 东北, 东, 东南, 南, 西南, 西, 西北
    private val directionIcons = intArrayOf(
        R.drawable.ic_direction_up,        // 北 90°
        R.drawable.ic_direction_right_up,  // 东北 45°
        R.drawable.ic_direction_right,     // 东 0°
        R.drawable.ic_direction_right_down,// 东南 315°
        R.drawable.ic_direction_down,      // 南 270°
        R.drawable.ic_direction_left_down, // 西南 225°
        R.drawable.ic_direction_left,      // 西 180°
        R.drawable.ic_direction_left_up    // 西北 135°
    )
    private val directionAngles = doubleArrayOf(90.0, 45.0, 0.0, 315.0, 270.0, 225.0, 180.0, 135.0)

    // 九宫格: row0=西北/北/东北, row1=西/锁/东, row2=西南/南/东南
    private val grid = arrayOf(
        intArrayOf(7, 0, 1),
        intArrayOf(6, -1, 2),
        intArrayOf(5, 4, 3)
    )

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        for (row in 0..2) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }

            for (col in 0..2) {
                val dirIndex = grid[row][col]

                if (dirIndex == -1) {
                    // 中心锁定按钮
                    btnLock = createButton(R.drawable.ic_lock_close).apply {
                        setColorFilter(themeColor)
                    }
                    btnLock.setOnClickListener { onLockClicked() }
                    rowLayout.addView(btnLock, LayoutParams(btnSize, btnSize))
                } else {
                    val btn = createButton(directionIcons[dirIndex])
                    buttons[dirIndex] = btn
                    btn.setOnClickListener { onDirectionClicked(dirIndex) }
                    rowLayout.addView(btn, LayoutParams(btnSize, btnSize))
                }
            }

            addView(rowLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun createButton(iconRes: Int): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            setColorFilter(iconColor)
            setBackgroundColor(ContextCompat.getColor(context, R.color.transparent))
            setPadding(2, 2, 2, 2)
            contentDescription = "方向按钮"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun onLockClicked() {
        if (isLocked) {
            // 锁定 → 自由模式
            isLocked = false
            btnLock.setImageResource(R.drawable.ic_lock_open)
            btnLock.setColorFilter(iconColor)
            clearAllDirectionHighlight()
            listener?.clickAngleInfo(false, 0.0, 0.0)
        } else {
            // 自由 → 锁定模式
            isLocked = true
            btnLock.setImageResource(R.drawable.ic_lock_close)
            btnLock.setColorFilter(themeColor)
        }
    }

    private fun onDirectionClicked(dirIndex: Int) {
        val btn = buttons[dirIndex] ?: return
        val angle = directionAngles[dirIndex]

        if (isLocked) {
            // 锁定模式: 选中一个方向持续移动，再点取消
            if (activeDirection == dirIndex) {
                activeDirection = -1
                btn.setColorFilter(iconColor)
                listener?.clickAngleInfo(false, angle, 0.0)
            } else {
                clearAllDirectionHighlight()
                activeDirection = dirIndex
                btn.setColorFilter(themeColor)
                listener?.clickAngleInfo(true, angle, 1.0)
            }
        } else {
            // 自由模式: 单次移动
            btn.setColorFilter(themeColor)
            listener?.clickAngleInfo(false, angle, 1.0)
            postDelayed({ btn.setColorFilter(iconColor) }, 150)
        }
    }

    private fun clearAllDirectionHighlight() {
        for (i in 0..7) {
            buttons[i]?.setColorFilter(iconColor)
        }
        activeDirection = -1
    }

    /** 设置主题色（与摇杆主题色统一） */
    fun setPrimaryColor(color: Int) {
        themeColor = color
        if (isLocked) {
            btnLock.setColorFilter(color)
        }
        if (activeDirection >= 0) {
            buttons[activeDirection]?.setColorFilter(color)
        }
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}

package com.mockloc.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.mockloc.R
import com.mockloc.widget.ButtonView
import com.mockloc.widget.JoystickView
import timber.log.Timber

/**
 * 摇杆窗口控制器
 * 
 * 负责管理悬浮窗中的摇杆/按钮控制界面
 * 包括：顶部控制栏、摇杆区域、底部速度选择栏
 */
class JoystickWindowController(
    private val context: Context,
    private val service: LocationService,
    private val onDirectionChanged: (auto: Boolean, angle: Double, r: Double) -> Unit,
    private val onSwitchToHistory: () -> Unit,
    private val onSwitchToMap: () -> Unit
) : WindowController {

    override var rootView: View? = null
        private set
    
    override var isInitialized: Boolean = false
        private set
    
    override var isVisible: Boolean = false
        private set

    // 内部视图引用
    private var joystickView: JoystickView? = null
    private var buttonView: ButtonView? = null
    private var joystickArea: FrameLayout? = null
    
    // 主题色
    private var primaryColor: Int = 0
    private var textSecondary: Int = 0
    private var surface: Int = 0
    private var surfaceVariant: Int = 0
    private var divider: Int = 0

    override fun initialize() {
        if (isInitialized) return
        
        try {
            initColors()
            createJoystickLayout()
            isInitialized = true
            Timber.d("JoystickWindowController initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize JoystickWindowController")
        }
    }

    override fun show() {
        if (!isInitialized) {
            initialize()
        }
        isVisible = true
        Timber.d("Joystick window shown")
    }

    override fun hide() {
        isVisible = false
        Timber.d("Joystick window hidden")
    }

    override fun destroy() {
        joystickView?.setOnMoveListener { _, _, _ -> }
        buttonView?.listener = null
        joystickView = null
        buttonView = null
        joystickArea = null
        rootView = null
        isInitialized = false
        isVisible = false
        Timber.d("JoystickWindowController destroyed")
    }

    /**
     * 摇杆类型变化时切换显示（圆形摇杆 / 八方向按钮）
     */
    fun onJoystickTypeChanged() {
        val spJoystickType = service.getSharedPreferences("settings", 0)
            .getInt("joystick_type", 0)
        if (spJoystickType == 1) {
            // 按钮模式
            joystickView?.visibility = android.view.View.GONE
            buttonView?.visibility = android.view.View.VISIBLE
        } else {
            // 摇杆模式（默认）
            joystickView?.visibility = android.view.View.VISIBLE
            buttonView?.visibility = android.view.View.GONE
        }
        Timber.d("Joystick type changed to: $spJoystickType")
    }

    /**
     * 初始化颜色（从主题读取）
     */
    private fun initColors() {
        val themedContext = createThemedContext()
        primaryColor = ContextCompat.getColor(themedContext, R.color.primary)
        textSecondary = ContextCompat.getColor(themedContext, R.color.text_secondary)
        surface = ContextCompat.getColor(themedContext, R.color.surface)
        surfaceVariant = ContextCompat.getColor(themedContext, R.color.surface_variant)
        divider = ContextCompat.getColor(themedContext, R.color.divider)
    }

    /**
     * 创建带主题的 Context
     */
    private fun createThemedContext(): Context {
        val isNight = (context.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeRes = if (isNight) R.style.Theme_VirtualLocation else R.style.Theme_VirtualLocation
        return ContextThemeWrapper(context, themeRes)
    }

    /**
     * 创建摇杆布局
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createJoystickLayout() {
        val density = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        // 主容器
        val container = DragLinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(surface)
                cornerRadius = 16f * density
                setStroke(dp(1), divider)
            }
            background = bg
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // 顶部控制栏
        val topRow = createTopRow(dp)
        container.addView(topRow)

        // 摇杆区域
        joystickArea = createJoystickArea(dp)
        container.addView(joystickArea)
        
        // 排除摇杆区域的拖动（避免操控时窗口跟着动）
        container.dragExcludeView = joystickArea

        // 底部速度栏
        val bottomRow = createBottomRow(dp)
        container.addView(bottomRow)

        rootView = container
    }

    /**
     * 创建顶部控制栏（移动/历史/地图按钮）
     */
    private fun createTopRow(dp: (Int) -> Int): LinearLayout {
        val ctrlBtnSize = dp(30)
        
        fun makeCircleBgSelector() = android.graphics.drawable.StateListDrawable().apply {
            val normalBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(surfaceVariant)
            }
            val pressedBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(divider)
            }
            addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
            addState(intArrayOf(), normalBg)
        }

        fun makeCircleBtn(iconRes: Int, desc: String, iconColor: Int = textSecondary) = ImageButton(context).apply {
            setImageResource(iconRes)
            background = makeCircleBgSelector()
            setColorFilter(iconColor)
            contentDescription = desc
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnMove = makeCircleBtn(R.drawable.ic_move, "移动").apply { setColorFilter(primaryColor) }
        val btnHistory = makeCircleBtn(R.drawable.ic_history, "历史")
        val btnMap = makeCircleBtn(R.drawable.ic_map, "地图")

        topRow.addView(btnMove, LinearLayout.LayoutParams(ctrlBtnSize, ctrlBtnSize))
        topRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        topRow.addView(btnHistory, LinearLayout.LayoutParams(ctrlBtnSize, ctrlBtnSize))
        topRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        topRow.addView(btnMap, LinearLayout.LayoutParams(ctrlBtnSize, ctrlBtnSize))

        // 按钮点击事件
        btnMove.setOnClickListener { /* 当前模式，暂不处理 */ }
        btnHistory.setOnClickListener { onSwitchToHistory() }
        btnMap.setOnClickListener { onSwitchToMap() }

        return topRow
    }

    /**
     * 创建摇杆区域（包含圆形摇杆和八方向按钮）
     */
    private fun createJoystickArea(dp: (Int) -> Int): FrameLayout {
        val joystickArea = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
                gravity = Gravity.CENTER
            }
        }

        // 圆形摇杆
        joystickView = JoystickView(context).apply {
            val size = dp(96)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            
            // 读取触觉反馈设置
            val prefs = context.getSharedPreferences("settings", 0)
            val hapticEnabled = prefs.getBoolean("joystick_haptic", true)
            setHapticEnabled(hapticEnabled)
        }

        // 八方向按钮
        buttonView = ButtonView(context).apply {
            setPrimaryColor(primaryColor)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // 根据设置决定显示哪个
        val spJoystickType = context.getSharedPreferences("settings", 0)
            .getInt("joystick_type", 0)
        
        if (spJoystickType == 1) {
            // 按钮模式
            joystickView?.visibility = View.GONE
            buttonView?.visibility = View.VISIBLE
        } else {
            // 摇杆模式（默认）
            joystickView?.visibility = View.VISIBLE
            buttonView?.visibility = View.GONE
        }

        joystickArea.addView(joystickView)
        joystickArea.addView(buttonView)

        // 设置回调
        joystickView?.setOnMoveListener { auto, angle, r ->
            onDirectionChanged(auto, angle, r)
        }

        buttonView?.listener = object : ButtonView.ButtonViewClickListener {
            override fun clickAngleInfo(auto: Boolean, angle: Double, r: Double) {
                onDirectionChanged(auto, angle, r)
            }
        }

        return joystickArea
    }

    /**
     * 创建底部速度选择栏（步行/跑步/骑行）
     */
    private fun createBottomRow(dp: (Int) -> Int): LinearLayout {
        val speedBtnSize = dp(30)
        
        fun makeCircleBgSelector() = android.graphics.drawable.StateListDrawable().apply {
            val normalBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(surfaceVariant)
            }
            val pressedBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(divider)
            }
            addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
            addState(intArrayOf(), normalBg)
        }

        fun makeCircleBtn(iconRes: Int, desc: String, iconColor: Int = textSecondary) = ImageButton(context).apply {
            setImageResource(iconRes)
            background = makeCircleBgSelector()
            setColorFilter(iconColor)
            contentDescription = desc
        }

        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnWalk = makeCircleBtn(R.drawable.ic_walk, "步行").apply { setColorFilter(primaryColor) }
        val btnRun = makeCircleBtn(R.drawable.ic_run, "跑步")
        val btnBike = makeCircleBtn(R.drawable.ic_bike, "骑行")

        bottomRow.addView(btnWalk, LinearLayout.LayoutParams(speedBtnSize, speedBtnSize))
        bottomRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        bottomRow.addView(btnRun, LinearLayout.LayoutParams(speedBtnSize, speedBtnSize))
        bottomRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        bottomRow.addView(btnBike, LinearLayout.LayoutParams(speedBtnSize, speedBtnSize))

        // 速度按钮点击事件
        val speedBtns = listOf(btnWalk, btnRun, btnBike)
        fun selectSpeedBtn(selected: ImageButton) {
            speedBtns.forEach { it.setColorFilter(textSecondary) }
            selected.setColorFilter(primaryColor)
        }

        btnWalk.setOnClickListener { 
            selectSpeedBtn(btnWalk)
            service.setSpeedMode("walk")
        }
        btnRun.setOnClickListener { 
            selectSpeedBtn(btnRun)
            service.setSpeedMode("run")
        }
        btnBike.setOnClickListener { 
            selectSpeedBtn(btnBike)
            service.setSpeedMode("bike")
        }

        // 恢复上次的速度模式
        val savedMode = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("speed_mode", "walk") ?: "walk"
        service.setSpeedMode(savedMode)
        
        when (savedMode) {
            "walk" -> selectSpeedBtn(btnWalk)
            "run" -> selectSpeedBtn(btnRun)
            "bike" -> selectSpeedBtn(btnBike)
        }

        return bottomRow
    }

    /**
     * 获取摇杆视图（用于外部访问）
     */
    fun getJoystickView(): JoystickView? = joystickView

    /**
     * 获取按钮视图（用于外部访问）
     */
    fun getButtonView(): ButtonView? = buttonView
}

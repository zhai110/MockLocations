package com.mockloc.service

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.mockloc.R
import com.mockloc.databinding.LayoutFloatingRouteControlBinding
import timber.log.Timber

/**
 * 路线控制悬浮窗控制器
 * 
 * 职责：
 * - 管理路线控制悬浮窗的显示/隐藏
 * - 处理拖动逻辑
 * - 处理按钮点击事件（播放/暂停、速度切换、停止）
 */
class RouteControlWindowController(
    private val context: android.content.Context,  // ✅ 使用带主题的 Context
    private val service: LocationService,
    private val windowManager: WindowManager,
    private val windowParams: WindowManager.LayoutParams
) : WindowController {

    private var binding: LayoutFloatingRouteControlBinding? = null
    
    override var rootView: View? = null
        private set
    
    override var isInitialized: Boolean = false
        private set
    
    override var isVisible: Boolean = false
        private set
    
    // ✅ 状态变化监听器
    private var stateChangeListener: ((Boolean) -> Unit)? = null
    
    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun initialize() {
        if (isInitialized) return
        
        try {
            // ✅ 使用 themedContext inflate 布局
            val inflater = LayoutInflater.from(context)
            binding = LayoutFloatingRouteControlBinding.inflate(inflater)
            rootView = binding!!.root

            // 设置按钮点击事件
            setupClickListeners()
            
            // 设置拖动逻辑
            setupDragListener()
            
            // ✅ 注册状态监听器，实现实时图标更新
            registerStateChangeListener()
            
            isInitialized = true
            Timber.d("Route control window controller initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize RouteControlWindowController")
        }
    }

    override fun show() {
        if (!isInitialized) {
            initialize()
        }
        
        // ✅ 修复：如果已经显示（有 parent），则直接返回
        if (rootView?.parent != null) {
            Timber.d("Route control window already shown")
            return
        }
        
        // ✅ 确保 rootView 不为 null
        if (rootView == null) {
            Timber.e("Route control window rootView is null after initialization")
            return
        }

        // ✅ 更新按钮图标以同步当前状态
        updatePlayPauseIcon()

        // 添加到窗口
        try {
            windowManager.addView(rootView, windowParams)
            isVisible = true
            Timber.d("Route control window shown successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show route control window")
        }
    }

    override fun hide() {
        rootView?.let { view ->
            try {
                windowManager.removeView(view)
                isVisible = false
                Timber.d("Route control window hidden")
            } catch (e: Exception) {
                Timber.e(e, "Failed to hide route control window")
            }
        }
    }

    override fun destroy() {
        // ✅ 取消状态监听
        unregisterStateChangeListener()
        
        hide()
        rootView = null
        binding = null
        isInitialized = false
        Timber.d("Route control window controller destroyed")
    }

    /**
     * ✅ 更新主题（非接口方法，手动调用）
     */
    fun updateTheme(isNightMode: Boolean) {
        // 主题切换时重新创建视图以应用新主题
        if (isInitialized && isVisible) {
            hide()
            // 重置初始化状态，下次 show() 时会重新 inflate
            isInitialized = false
            rootView = null
            binding = null
            show()
        }
    }

    /**
     * 设置按钮点击事件
     */
    private fun setupClickListeners() {
        binding?.apply {
            // 播放/暂停
            btnPlayPause.setOnClickListener {
                val state = service.getRoutePlaybackState()
                if (state.isPlaying) {
                    service.pauseRoute()
                } else {
                    service.playRoute()
                }
                updatePlayPauseIcon()
            }

            // 速度切换
            btnSpeed.setOnClickListener {
                val currentSpeed = service.getRoutePlaybackState().speedMultiplier
                val nextSpeed = when (currentSpeed) {
                    1f -> 2f
                    2f -> 4f
                    4f -> 0.5f
                    else -> 1f
                }
                service.setRouteSpeedMultiplier(nextSpeed)
                
                // 显示 Toast 提示
                android.widget.Toast.makeText(service, "速度: ${nextSpeed}x", android.widget.Toast.LENGTH_SHORT).show()
            }

            // 停止
            btnStop.setOnClickListener {
                service.stopRoutePlayback()
                updatePlayPauseIcon()
            }
        }
    }

    /**
     * 设置拖动监听器
     */
    private fun setupDragListener() {
        rootView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams.x
                    initialY = windowParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false  // ✅ 不消费 DOWN 事件，让子视图（按钮）可以接收点击
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // 如果移动距离超过阈值，认为是拖动
                    if (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        windowParams.x = initialX + deltaX.toInt()
                        windowParams.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(view, windowParams)
                        true  // ✅ 拖动时消费事件
                    } else {
                        false  // ✅ 未拖动时不消费，让子视图处理
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 如果没有拖动，视为点击（但点击事件已由 OnClickListener 处理）
                    }
                    isDragging = false
                    false  // ✅ 不消费 UP 事件
                }
                else -> false
            }
        }
    }

    /**
     * 更新播放/暂停按钮图标
     */
    private fun updatePlayPauseIcon() {
        val isPlaying = service.getRoutePlaybackState().isPlaying
        binding?.btnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
        )
    }
    
    /**
     * ✅ 注册状态监听器（实现实时图标同步）
     */
    private fun registerStateChangeListener() {
        stateChangeListener = { isPlaying ->
            // 当路线播放状态变化时，自动更新图标
            Timber.d("Route playback state changed: isPlaying=$isPlaying")
            updatePlayPauseIcon()
        }
        
        // 注册到 LocationService
        service.setRouteControlStateListener(stateChangeListener)
        
        // 立即更新一次当前状态
        updatePlayPauseIcon()
        
        Timber.d("Registered route control state listener")
    }
    
    /**
     * ✅ 取消状态监听器
     */
    private fun unregisterStateChangeListener() {
        service.setRouteControlStateListener(null)
        stateChangeListener = null
        Timber.d("Unregistered route control state listener")
    }
}

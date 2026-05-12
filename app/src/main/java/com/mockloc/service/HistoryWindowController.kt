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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mockloc.R
import com.mockloc.data.db.HistoryLocation
import com.mockloc.data.repository.LocationRepository
import com.mockloc.util.UIFeedbackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 历史记录窗口控制器（完善版）
 * 
 * 负责管理悬浮窗中的历史记录列表界面
 * 包括：搜索框、历史记录列表、空状态提示
 */
class HistoryWindowController(
    private val context: Context,
    private val service: LocationService,
    private val windowManager: android.view.WindowManager,
    private val windowParams: android.view.WindowManager.LayoutParams,
    private val onSwitchToJoystick: () -> Unit,
    private val onSwitchToMap: () -> Unit,
    private val onHistorySelected: (location: HistoryLocation) -> Unit,
    private val locationRepository: LocationRepository  // ✅ Phase 1: Repository 替代直接 DAO 访问
) : WindowController {

    override var rootView: View? = null
        private set
    
    override var isInitialized: Boolean = false
        private set
    
    override var isVisible: Boolean = false
        private set

    // 内部视图引用
    private var recyclerView: RecyclerView? = null
    private var searchEditText: EditText? = null
    private var noRecordText: TextView? = null
    private var adapter: FloatingHistoryAdapter? = null
    
    // 数据
    private var allRecords: List<HistoryLocation> = emptyList()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // ✅ 缓存 themedContext，避免重复创建
    private lateinit var themedContext: Context

    // 主题色
    private var primaryColor: Int = 0
    private var textPrimary: Int = 0
    private var textSecondary: Int = 0
    private var textHint: Int = 0
    private var surface: Int = 0
    private var surfaceVariant: Int = 0
    private var divider: Int = 0

    override fun initialize() {
        if (isInitialized) return
        
        try {
            // ✅ 初始化 themedContext（只创建一次）
            themedContext = com.mockloc.util.ThemeUtils.createThemedContext(context).first
            
            initColors()
            createHistoryLayout()
            isInitialized = true
            Timber.d("HistoryWindowController initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize HistoryWindowController")
        }
    }

    override fun show() {
        if (!isInitialized) {
            initialize()
        }
        
        isVisible = true
        refreshHistory()
        Timber.d("History window shown")
    }

    override fun hide() {
        isVisible = false
        // 隐藏键盘
        searchEditText?.clearFocus()
        Timber.d("History window hidden")
    }

    override fun destroy() {
        // 1. 取消协程
        scope.cancel()
        
        // 2. 清理适配器
        adapter = null
        
        // 3. 清理所有视图引用
        recyclerView = null
        searchEditText = null
        noRecordText = null
        rootView = null
        
        isInitialized = false
        isVisible = false
        
        Timber.d("HistoryWindowController destroyed")
    }

    /**
     * 初始化颜色（从主题读取）
     */
    private fun initColors() {
        // ✅ 复用类级别的 themedContext
        primaryColor = ContextCompat.getColor(themedContext, R.color.primary)
        textPrimary = ContextCompat.getColor(themedContext, R.color.text_primary)
        textSecondary = ContextCompat.getColor(themedContext, R.color.text_secondary)
        textHint = ContextCompat.getColor(themedContext, R.color.text_hint)
        surface = ContextCompat.getColor(themedContext, R.color.surface)
        surfaceVariant = ContextCompat.getColor(themedContext, R.color.surface_variant)
        divider = ContextCompat.getColor(themedContext, R.color.divider)
    }

    /**
     * 创建历史记录布局
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createHistoryLayout() {
        val density = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

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
            
            // 设置固定宽度（90% 屏幕宽度）
            val screenWidth = context.resources.displayMetrics.widthPixels
            layoutParams = FrameLayout.LayoutParams(
                (screenWidth * 0.9).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 顶部工具栏：搜索输入框 + 关闭按钮
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
            )
        }

        // 搜索输入框
        searchEditText = EditText(themedContext).apply {
            hint = "搜索历史"
            textSize = 14f
            setTextColor(textPrimary)
            setHintTextColor(textHint)
            setSingleLine(true)
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
        val btnClose = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            setColorFilter(textSecondary)
            contentDescription = "关闭"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }.also { topBar.addView(it) }

        container.addView(topBar)

        // 内容区域
        val contentLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(430)
            ).apply {
                topMargin = dp(8)  // 减小顶部间距（原来是 dp(44)）
                bottomMargin = dp(4)
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        }

        // 空状态提示
        noRecordText = TextView(context).apply {
            text = "暂无历史记录"
            textSize = 15f
            setTextColor(ContextCompat.getColor(themedContext, R.color.text_hint))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(60), dp(16), dp(60))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // ✅ 设置最小宽度，确保与 RecyclerView 一致
            minimumWidth = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        }.also { contentLayout.addView(it) }

        // RecyclerView
        adapter = FloatingHistoryAdapter(
            onItemClick = { record ->
                // 点击历史记录
                Timber.d("History item clicked: ${record.name}")
                searchEditText?.setText("")
                searchEditText?.clearFocus()

                // 调用回调
                onHistorySelected(record)
                
                // 显示提示
                UIFeedbackHelper.showToast(context, "位置已更新: ${record.name}")
                
                // 切换回摇杆窗口
                onSwitchToJoystick()
            },
            context = themedContext
        )

        recyclerView = RecyclerView(context).apply {
            visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@HistoryWindowController.adapter
            // 固定高度，避免首次测量时布局抖动
            setHasFixedSize(true)
            // 添加淡入淡出动画
            itemAnimator = DefaultItemAnimator().apply {
                addDuration = com.mockloc.util.AnimationConfig.getFadeInDuration()
                removeDuration = com.mockloc.util.AnimationConfig.getFadeOutDuration()
            }
            isClickable = true
            isFocusable = false
            // 设置最小高度和宽度
            minimumHeight = dp(300)
            minimumWidth = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        }.also { contentLayout.addView(it) }

        container.addView(contentLayout)

        // 排除列表区域的拖动
        container.dragExcludeView = contentLayout

        // ===== 搜索框事件 =====
        searchEditText?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // 先启用焦点
                enableSearchFocus(windowManager, windowParams)
                // 延迟请求焦点和显示键盘，确保 WindowManager 已更新
                v.post {
                    v.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            false  // 让 EditText 继续处理后续事件
        }

        searchEditText?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enableSearchFocus(windowManager, windowParams)
            } else {
                disableSearchFocus(windowManager, windowParams)
            }
        }

        searchEditText?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    showHistoryList(allRecords)
                } else {
                    val filtered = allRecords.filter {
                        it.name.contains(text, true) || it.address.contains(text, true)
                    }
                    showHistoryList(filtered)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 关闭按钮
        btnClose.setOnClickListener {
            disableSearchFocus(windowManager, windowParams)
            searchEditText?.setText("")
            searchEditText?.clearFocus()
            onSwitchToJoystick()
        }

        rootView = container
    }

    /**
     * 显示历史记录列表
     */
    private fun showHistoryList(records: List<HistoryLocation>) {
        adapter?.submitList(records)
        
        // 显示/隐藏空状态
        if (records.isEmpty()) {
            noRecordText?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            noRecordText?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }

    /**
     * 刷新历史记录 — ✅ Phase 1: 通过 LocationRepository
     */
    fun refreshHistory() {
        scope.launch {
            try {
                allRecords = locationRepository.getAllHistory()
                withContext(Dispatchers.Main) {
                    showHistoryList(allRecords)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load history records")
            }
        }
    }
}

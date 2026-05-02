package com.mockloc.ui.settings

import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.mockloc.R
import com.mockloc.databinding.ActivitySettingsBinding
import com.mockloc.ui.update.UpdateDialogFragment
import com.mockloc.util.PrefsConfig
import com.mockloc.util.PrefsConfig.Settings
import com.mockloc.util.UIFeedbackHelper
import com.mockloc.util.UpdateChecker
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 设置页面
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences(PrefsConfig.SETTINGS, MODE_PRIVATE)

        setupToolbar()
        setupMovementSettings()
        setupLocationSettings()
        setupOtherSettings()
        setupResetButton()
        loadSettings()

        // ✅ 初始化主题相关颜色（确保与 onConfigurationChanged 行为一致）
        initializeThemedColors()
    }

    /**
     * 初始化主题相关颜色
     * 在 onCreate 时调用，确保无论从哪种路径进入设置页面，颜色都正确初始化
     */
    private fun initializeThemedColors() {
        updateViewBackgrounds(resources.configuration)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupMovementSettings() {
        // 步行速度 (3-13 km/h)
        binding.seekbarWalkingSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 3
                // 实时显示速度值
                binding.textWalkingSpeed.text = "约 $speed km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = binding.seekbarWalkingSpeed.progress + 3
                prefs.edit().putInt(Settings.KEY_WALK_SPEED, speed).apply()
                Timber.d("Walking speed saved: $speed km/h")
            }
        })

        // 跑步速度 (5-25 km/h)
        binding.seekbarRunningSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 5
                // 实时显示速度值
                binding.textRunningSpeed.text = "约 $speed km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = binding.seekbarRunningSpeed.progress + 5
                prefs.edit().putInt(Settings.KEY_RUN_SPEED, speed).apply()
                Timber.d("Running speed saved: $speed km/h")
            }
        })

        // 骑行速度 (10-40 km/h)
        binding.seekbarCyclingSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 10
                // 实时显示速度值
                binding.textCyclingSpeed.text = "约 $speed km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = binding.seekbarCyclingSpeed.progress + 10
                prefs.edit().putInt(Settings.KEY_BIKE_SPEED, speed).apply()
                Timber.d("Cycling speed saved: $speed km/h")
            }
        })
    }

    private fun setupLocationSettings() {
        // 海拔设置
        binding.itemAltitude.setOnClickListener {
            showAltitudeDialog()
        }
        
        // 位置更新频率
        binding.itemLocationUpdateInterval.setOnClickListener {
            showLocationUpdateIntervalDialog()
        }

        // 随机偏移开关
        binding.switchRandomOffset.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Settings.KEY_RANDOM_OFFSET, isChecked).apply()
            Timber.d("Random offset saved: $isChecked")
        }
        
        // 摇杆类型
        binding.itemJoystickType.setOnClickListener {
            showJoystickTypeDialog()
        }
        
        // 摇杆触觉反馈开关
        binding.switchJoystickHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Settings.KEY_JOYSTICK_HAPTIC, isChecked).apply()
            Timber.d("Joystick haptic feedback saved: $isChecked")
        }
    }

    private fun showAltitudeDialog() {
        val currentAltitude = prefs.getFloat(Settings.KEY_ALTITUDE, 0f)
        val input = android.widget.EditText(this).apply {
            setText(currentAltitude.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("设置海拔")
            .setMessage("请输入海拔高度（米）")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val altitude = input.text.toString().toFloatOrNull() ?: 0f
                prefs.edit().putFloat(Settings.KEY_ALTITUDE, altitude).apply()
                Timber.d("Altitude saved: $altitude")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示位置更新频率选择对话框
     */
    private fun showLocationUpdateIntervalDialog() {
        val options = arrayOf("极速模式", "快速模式", "标准模式", "省电模式")
        val intervals = longArrayOf(50L, 100L, 200L, 500L)
        val currentInterval = prefs.getLong(Settings.KEY_LOCATION_UPDATE_INTERVAL, 100L)
        val currentIndex = intervals.indexOf(currentInterval).takeIf { it >= 0 } ?: 1 // 默认选"快速"
            
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("更新频率")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val interval = intervals[which]
                prefs.edit().putLong(Settings.KEY_LOCATION_UPDATE_INTERVAL, interval).apply()
                // 更新显示文本
                binding.textLocationUpdateInterval.text = options[which]
                Timber.d("Location update interval saved: ${interval}ms")
                dialog.dismiss()
            }
            .show()
    }

    private fun setupOtherSettings() {
        // 开机自启开关
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Settings.KEY_AUTO_START, isChecked).apply()
            Timber.d("Auto start saved: $isChecked")
        }
        
        // 历史记录有效期
        binding.itemHistoryExpiry.setOnClickListener {
            showHistoryExpiryDialog()
        }
        
        // 日志开关
        binding.switchLog.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Settings.KEY_LOGGING, isChecked).apply()
            Timber.d("Logging saved: $isChecked")
        }

        // 关于
        binding.itemAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showJoystickTypeDialog() {
        val types = arrayOf("摇杆模式", "按钮模式")
        val currentType = prefs.getInt(Settings.KEY_JOYSTICK_TYPE, 0)
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("摇杆类型")
            .setSingleChoiceItems(types, currentType) { dialog, which ->
                prefs.edit().putInt(Settings.KEY_JOYSTICK_TYPE, which).apply()
                // 更新显示文本
                binding.textJoystickType.text = types[which]
                Timber.d("Joystick type saved: ${types[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showHistoryExpiryDialog() {
        val options = arrayOf("7天有效", "14天有效", "30天有效", "永久保存")
        val expiryDays = intArrayOf(7, 14, 30, -1)
        val currentExpiry = prefs.getInt(Settings.KEY_HISTORY_EXPIRY, 30)
        val currentIndex = when (currentExpiry) {
            7 -> 0
            14 -> 1
            30 -> 2
            else -> 3
        }
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("有效记录")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val expiry = expiryDays[which]
                prefs.edit().putInt(Settings.KEY_HISTORY_EXPIRY, expiry).apply()
                // 更新显示文本
                binding.textHistoryExpiry.text = options[which]
                Timber.d("History expiry saved: ${options[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showAboutDialog() {
        val updateChecker = UpdateChecker(this)
        val (versionCode, versionName) = updateChecker.getCurrentVersionInfo()
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("关于")
            .setMessage("${getString(R.string.about_message)}\n\n当前版本：$versionName ($versionCode)")
            .setPositiveButton("确定", null)
            .setNeutralButton("检查更新") { _, _ ->
                checkForUpdate()
            }
            .show()
    }
    
    /**
     * 检查更新
     */
    private fun checkForUpdate() {
        val updateChecker = UpdateChecker(this)
        
        lifecycleScope.launch {
            // ✅ 使用 Material 3 风格的加载对话框（替换已废弃的 ProgressDialog）
            val loadingDialog = AlertDialog.Builder(this@SettingsActivity, R.style.RoundedDialogTheme)
                .setView(
                    android.widget.LinearLayout(this@SettingsActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        setPadding(48, 48, 48, 48)
                        gravity = android.view.Gravity.CENTER
                        addView(
                            CircularProgressIndicator(this@SettingsActivity).apply {
                                isIndeterminate = true
                            }
                        )
                        addView(
                            android.widget.TextView(this@SettingsActivity).apply {
                                text = "检查更新中..."
                                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                                setPadding(24, 0, 0, 0)
                                setTextColor(getColor(R.color.text_primary))
                            }
                        )
                    }
                )
                .setCancelable(false)
                .create()
            
            loadingDialog.show()
            
            // ✅ 手动检查更新时强制检查，忽略频率限制
            updateChecker.checkForUpdate(forceCheck = true)
                .onSuccess { updateInfo ->
                    loadingDialog.dismiss()
                    
                    if (updateInfo != null) {
                        // 有新版本，显示更新对话框
                        UpdateDialogFragment.newInstance(updateInfo)
                            .show(supportFragmentManager, "update_dialog")
                    } else {
                        // 已是最新版本
                        UIFeedbackHelper.showToast(this@SettingsActivity, "当前已是最新版本")
                    }
                }
                .onFailure { error ->
                    loadingDialog.dismiss()
                    UIFeedbackHelper.showToast(this@SettingsActivity, "检查更新失败：${error.message}")
                    Timber.e(error, "Update check failed")
                }
        }
    }

    private fun setupResetButton() {
        binding.btnResetDefaults.setOnClickListener {
            AlertDialog.Builder(this, R.style.ResetDefaultsDialogTheme)
                .setTitle("恢复默认设置")
                .setMessage("此操作将清除所有自定义设置并恢复为默认值，是否继续？")
                .setPositiveButton("确定") { _, _ ->
                    // 清除所有 SharedPreferences 数据
                    prefs.edit().clear().apply()
                    Timber.d("All settings cleared, restored to defaults")
                    
                    // 重新加载默认设置到 UI
                    loadSettings()
                    
                    // 显示提示
                    android.widget.Toast.makeText(this, "已恢复默认设置", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadSettings() {
        // 加载速度设置
        val walkSpeed = prefs.getInt(Settings.KEY_WALK_SPEED, 5)
        val runSpeed = prefs.getInt(Settings.KEY_RUN_SPEED, 12)
        val bikeSpeed = prefs.getInt(Settings.KEY_BIKE_SPEED, 20)
        
        binding.seekbarWalkingSpeed.progress = walkSpeed - 3
        binding.seekbarRunningSpeed.progress = runSpeed - 5
        binding.seekbarCyclingSpeed.progress = bikeSpeed - 10
        
        // 更新速度显示文本
        binding.textWalkingSpeed.text = "约 $walkSpeed km/h"
        binding.textRunningSpeed.text = "约 $runSpeed km/h"
        binding.textCyclingSpeed.text = "约 $bikeSpeed km/h"
        
        // 加载开关设置
        binding.switchRandomOffset.isChecked = prefs.getBoolean(Settings.KEY_RANDOM_OFFSET, false)
        binding.switchAutoStart.isChecked = prefs.getBoolean(Settings.KEY_AUTO_START, false)
        binding.switchLog.isChecked = prefs.getBoolean(Settings.KEY_LOGGING, true)
        binding.switchJoystickHaptic.isChecked = prefs.getBoolean(Settings.KEY_JOYSTICK_HAPTIC, true)
        
        // 加载摇杆类型显示
        val joystickType = prefs.getInt(Settings.KEY_JOYSTICK_TYPE, 0)
        binding.textJoystickType.text = if (joystickType == 0) "摇杆模式" else "按钮模式"
        
        // 加载历史记录有效期显示
        val historyExpiry = prefs.getInt(Settings.KEY_HISTORY_EXPIRY, 30)
        binding.textHistoryExpiry.text = when (historyExpiry) {
            7 -> "7天有效"
            14 -> "14天有效"
            30 -> "30天有效"
            else -> "永久保存"
        }
        
        // 加载位置更新频率显示
        val locationUpdateInterval = prefs.getLong(Settings.KEY_LOCATION_UPDATE_INTERVAL, 100L)
        binding.textLocationUpdateInterval.text = when (locationUpdateInterval) {
            50L -> "极速模式"
            100L -> "快速模式"
            200L -> "标准模式"
            500L -> "省电模式"
            else -> "快速模式"
        }
        
        Timber.d("Settings loaded: walk=$walkSpeed, run=$runSpeed, bike=$bikeSpeed")
    }

    /**
     * 处理配置变化（夜间模式切换）
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateViewBackgrounds(newConfig)
    }
    
    /**
     * 手动更新视图背景颜色以响应主题变化
     * @param newConfig 系统传入的新 Configuration，用于创建正确的主题上下文
     */
    private fun updateViewBackgrounds(newConfig: android.content.res.Configuration) {
        try {
            // ✅ 关键修复：使用 newConfig 创建新的 Context，确保 Resources 从正确的目录加载
            val newConfigContext = createConfigurationContext(newConfig)
            val (themedContext, _) = com.mockloc.util.ThemeUtils.createThemedContext(newConfigContext)
            val resources = themedContext.resources
            val theme = themedContext.theme
            
            // 获取最新的颜色
            val backgroundColor = resources.getColor(R.color.background, theme)
            val surfaceColor = resources.getColor(R.color.surface, theme)
            val appBarBackgroundColor = resources.getColor(R.color.app_bar_background, theme)
            val textPrimaryColor = resources.getColor(R.color.text_primary, theme)
            val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)
            val textHintColor = resources.getColor(R.color.text_hint, theme)
            val dividerLightColor = resources.getColor(R.color.divider_light, theme)
            val onPrimaryContainerColor = resources.getColor(R.color.on_primary_container, theme)
            
            // 更新 CoordinatorLayout 背景
            binding.root.setBackgroundColor(backgroundColor)
            
            // 更新 AppBarLayout 背景
            binding.appBar.setBackgroundColor(appBarBackgroundColor)
            
            // ✅ 更新 NestedScrollView 内部根 LinearLayout 的背景（确保与 CoordinatorLayout 一致）
            val nestedScrollView = binding.root.getChildAt(1) as? androidx.core.widget.NestedScrollView
            val contentLayout = nestedScrollView?.getChildAt(0) as? android.widget.LinearLayout
            contentLayout?.setBackgroundColor(backgroundColor)
            
            // 更新 Toolbar 标题和导航图标颜色
            binding.toolbar.setTitleTextColor(textPrimaryColor)
            binding.toolbar.navigationIcon?.setTint(textPrimaryColor)
            
            // 更新所有卡片背景
            // 注意：布局中有多个 MaterialCardView，需要通过 ID 或遍历更新
            contentLayout?.let { layout ->
                for (i in 0 until layout.childCount) {
                    val child = layout.getChildAt(i)
                    when (child) {
                        is com.google.android.material.card.MaterialCardView -> {
                            child.setCardBackgroundColor(surfaceColor)
                            // ✅ 移除默认的白色边框（Material3 默认有 1dp stroke）
                            child.strokeWidth = 0
                            // ✅ 强制刷新 Drawable 状态，确保背景色立即生效
                            child.post { child.invalidate() }
                        }
                    }
                }
            }
            
            // ✅ 直接更新所有标题和值 TextView（不再使用有 bug 的 updateTextColors）
            updateSettingsTexts(resources, theme)

            // 更新所有分隔线颜色
            updateDividerColors(binding.root, dividerLightColor)

            // 更新所有图标 tint 颜色
            updateIconTints(binding.root, textHintColor)

            // ✅ 更新 SeekBar（滑动条）颜色 — 使用 proper ColorStateList
            updateSeekBarColorsFixed(resources, theme)

            // ✅ 更新 SwitchMaterial（开关）颜色 — 使用 proper ColorStateList
            updateSwitchColorsFixed(resources, theme)
            
            Timber.d("SettingsActivity view backgrounds updated for theme change")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update SettingsActivity view backgrounds")
        }
    }
    
    /**
     * 直接更新设置页面所有 TextView 颜色（不依赖 currentTextColor 比较）
     * 根据 View 的 ID 或遍历时父布局特征来确定颜色角色
     */
    private fun updateSettingsTexts(resources: Resources, theme: Resources.Theme) {
        val textPrimaryColor = resources.getColor(R.color.text_primary, theme)
        val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)
        val textHintColor = resources.getColor(R.color.text_hint, theme)
        val onPrimaryContainerColor = resources.getColor(R.color.on_primary_container, theme)

        // ✅ 分组标题（有 ID，直接更新）
        binding.textSectionMovement?.setTextColor(onPrimaryContainerColor)
        binding.textSectionLocation?.setTextColor(onPrimaryContainerColor)
        binding.textSectionOther?.setTextColor(onPrimaryContainerColor)

        // ✅ 值 TextView（有 ID，直接更新）
        binding.textWalkingSpeed?.setTextColor(textSecondaryColor)
        binding.textRunningSpeed?.setTextColor(textSecondaryColor)
        binding.textCyclingSpeed?.setTextColor(textSecondaryColor)
        binding.textJoystickType?.setTextColor(textSecondaryColor)
        binding.textHistoryExpiry?.setTextColor(textSecondaryColor)
        binding.textLocationUpdateInterval?.setTextColor(textSecondaryColor)

        // ✅ 标签和描述 TextView（无 ID，按父布局特征遍历更新）
        // 注意：需要传入 textSecondaryColor 用于 SeekBar 范围标签
        updateLabelAndDescriptionTexts(binding.root, textPrimaryColor, textHintColor, textSecondaryColor)
    }

    /**
     * 遍历视图树，更新标签（text_primary）、描述（text_hint）和值（text_secondary）TextView
     * 标签特征：父布局是垂直 LinearLayout，且是第一个 TextView
     * 描述特征：父布局是垂直 LinearLayout，且是第二个 TextView（通常在上方有标签）
     * 值特征：SeekBar 旁边的范围标签（如 "3 km/h"）
     */
    private fun updateLabelAndDescriptionTexts(view: android.view.View, labelColor: Int, descColor: Int, valueColor: Int) {
        when (view) {
            is android.widget.TextView -> {
                if (view.id == R.id.text_section_movement ||
                    view.id == R.id.text_section_location ||
                    view.id == R.id.text_section_other) {
                    return
                }
                val parent = view.parent
                if (parent is android.view.ViewGroup) {
                    val role = determineTextRole(view, parent)
                    val color = when (role) {
                        "label" -> labelColor
                        "desc" -> descColor
                        "value" -> valueColor  // ✅ SeekBar 范围标签用 text_secondary
                        else -> labelColor
                    }
                    view.setTextColor(color)
                }
            }
            is android.view.ViewGroup -> {
                for (i in 0 until view.childCount) {
                    updateLabelAndDescriptionTexts(view.getChildAt(i), labelColor, descColor, valueColor)
                }
            }
        }
    }

    /**
     * 根据父布局结构判断 TextView 的颜色角色
     */
    private fun determineTextRole(textView: android.widget.TextView, parent: android.view.ViewGroup): String {
        // 情况1：父布局是水平 LinearLayout（设置项行或 SeekBar 范围标签）
        if (parent is android.widget.LinearLayout && parent.orientation == android.widget.LinearLayout.HORIZONTAL) {
            // 如果父布局包含 SeekBar，这些 TextView 是范围标签（如 "3 km/h"），使用 text_secondary
            for (i in 0 until parent.childCount) {
                if (parent.getChildAt(i) is android.widget.SeekBar) {
                    return "value"  // SeekBar 范围标签用 text_secondary
                }
            }
            // 否则第一个子 View 是标签
            if (parent.indexOfChild(textView) == 0) return "label"
        }
        // 情况2：父布局是垂直 LinearLayout，且第一个子 View 是 TextView → 那是标签，第二个是描述
        if (parent is android.widget.LinearLayout && parent.orientation == android.widget.LinearLayout.VERTICAL) {
            val firstChild = parent.getChildAt(0)
            val secondChild = if (parent.childCount > 1) parent.getChildAt(1) else null
            if (firstChild == textView) return "label"
            if (secondChild == textView && secondChild is android.widget.TextView) return "desc"
        }
        // 情况3：祖父布局是垂直 LinearLayout（设置项内部），本 TextView 是第一个 → 标签
        val grandParent = parent.parent
        if (grandParent is android.widget.LinearLayout && grandParent.orientation == android.widget.LinearLayout.VERTICAL) {
            val firstChild = grandParent.getChildAt(0)
            if (firstChild == parent || firstChild == textView) return "label"
        }
        return "label" // 默认当标签处理
    }
    
    /**
     * 递归更新所有分隔线颜色
     * 分隔线判断条件：背景不为空，且父布局是 LinearLayout（设置项之间）
     * 注意：在 onCreate 时调用时，View 可能还未完成布局测量，因此不依赖高度判断
     */
    private fun updateDividerColors(view: android.view.View, color: Int) {
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                updateDividerColors(view.getChildAt(i), color)
            }
        }
        val parent = view.parent
        if (view.background != null && parent is android.widget.LinearLayout) {
            val lp = view.layoutParams
            val isThinDivider = lp != null && lp.height >= 0 && lp.height <= (2 * resources.displayMetrics.density).toInt()
            if (isThinDivider) {
                view.setBackgroundColor(color)
            }
        }
    }
    
    /**
     * 递归更新所有 ImageView 的 tint 颜色
     */
    private fun updateIconTints(view: android.view.View, color: Int) {
        when (view) {
            is android.widget.ImageView -> {
                view.setColorFilter(color)
            }
            is android.view.ViewGroup -> {
                for (i in 0 until view.childCount) {
                    updateIconTints(view.getChildAt(i), color)
                }
            }
        }
    }
    
    /**
     * 更新 SeekBar 颜色（正确方式：使用带 checked/unchecked 状态的 ColorStateList）
     */
    private fun updateSeekBarColorsFixed(resources: Resources, theme: Resources.Theme) {
        try {
            val primaryColor = resources.getColor(R.color.primary, theme)
            val textHintColor = resources.getColor(R.color.text_hint, theme)

            // progressTintList：进度条颜色（checked = primary，unchecked = textHint）
            // 注意：SeekBar 没有 checked 状态，这里用 enabled/disabled 区分
            val progressTint = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(primaryColor, textHintColor)
            )

            listOf(binding.seekbarWalkingSpeed, binding.seekbarRunningSpeed, binding.seekbarCyclingSpeed).forEach { seekBar ->
                seekBar.progressTintList = progressTint
                seekBar.thumbTintList = progressTint
            }

            Timber.d("SeekBar colors updated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update SeekBar colors")
        }
    }

    /**
     * 更新 SwitchMaterial 颜色（正确方式：区分 checked/unchecked 状态）
     */
    private fun updateSwitchColorsFixed(resources: Resources, theme: Resources.Theme) {
        try {
            val primaryColor = resources.getColor(R.color.primary, theme)
            val textHintColor = resources.getColor(R.color.text_hint, theme)
            val surfaceVariantColor = resources.getColor(R.color.surface_variant, theme)

            // trackTintList：checked=primary，unchecked=textHint
            val trackTint = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()  // 默认状态（未选中）
                ),
                intArrayOf(primaryColor, textHintColor)
            )

            // thumbTintList：checked=on_primary（白色），unchecked=surface_variant（深灰/浅灰）
            val onPrimaryColor = resources.getColor(R.color.on_primary, theme)
            val thumbTint = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()  // 默认状态（未选中）
                ),
                intArrayOf(onPrimaryColor, surfaceVariantColor)
            )

            listOf(binding.switchRandomOffset, binding.switchAutoStart, binding.switchLog, binding.switchJoystickHaptic).forEach { switch ->
                switch.trackTintList = trackTint
                switch.thumbTintList = thumbTint
            }

            Timber.d("Switch colors updated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update Switch colors")
        }
    }
    
    /**
     * 扩展函数：sp 转 px
     */
    private fun Int.spToPx(): Int {
        return (this * resources.displayMetrics.scaledDensity).toInt()
    }
}

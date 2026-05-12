package com.mockloc.ui.main.delegate

import android.content.Context
import android.content.res.Configuration
import androidx.fragment.app.Fragment
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import timber.log.Timber

/**
 * 主题切换委托类
 *
 * 职责：
 * - 管理夜间模式状态（isNightMode / isManualLayerSelected）
 * - 更新所有 View 的背景色、文字颜色、图标 tint
 * - 更新 Chip 颜色（速度选择器）
 * - 更新路线进度条颜色
 * - 处理配置变更（日夜模式切换）
 * - 更新地图类型（夜间/白天）
 *
 * 设计说明：
 * - configChanges="uiMode" 阻止了 Activity 重建，XML 样式中静态引用的颜色不会重新解析，
 *   必须通过代码显式创建 ColorStateList 并赋值给 View。
 * - themedContext 由 newConfig 创建，确保 Resources 从正确的目录加载颜色资源。
 */
class ThemeDelegate(
    private val fragment: Fragment,
    private val binding: FragmentMainBinding
) {

    /** 当前是否为夜间模式 */
    private var isNightMode = false

    /** 用户是否手动选择了地图图层（手动选择后不再自动切换地图类型） */
    private var isManualLayerSelected = false

    /**
     * 初始化主题状态
     * 在 onViewCreated 中调用，读取当前系统夜间模式状态
     */
    fun init() {
        isNightMode = (fragment.resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Timber.d("ThemeDelegate initialized: isNightMode=$isNightMode")
    }

    /**
     * 获取当前夜间模式状态
     * @return true 为夜间模式，false 为白天模式
     */
    fun getNightMode(): Boolean = isNightMode

    /**
     * 设置用户是否手动选择了图层
     * 手动选择后，updateMapTypeForNightMode 将不再自动切换地图类型
     * @param selected true 表示用户手动选择了图层
     */
    fun setManualLayerSelected(selected: Boolean) {
        isManualLayerSelected = selected
    }

    /**
     * 根据夜间模式更新地图类型
     * 如果用户手动选择了图层（isManualLayerSelected=true），则跳过自动切换
     * @param aMap 高德地图 AMap 实例
     */
    fun updateMapTypeForNightMode(aMap: com.amap.api.maps.AMap) {
        if (isManualLayerSelected) {
            Timber.d("User manually selected layer, skip auto-switch")
            return
        }

        val targetMapType = if (isNightMode) {
            com.amap.api.maps.AMap.MAP_TYPE_NIGHT
        } else {
            com.amap.api.maps.AMap.MAP_TYPE_NORMAL
        }

        if (aMap.mapType != targetMapType) {
            aMap.mapType = targetMapType
            Timber.d("Map type updated to: ${if (isNightMode) "NIGHT" else "NORMAL"}")
        }
    }

    /**
     * 更新夜间模式状态（响应配置变更）
     * 比较新配置与当前状态，检测日夜模式是否发生变化
     * @param newConfig 新的 Configuration 对象
     * @return true 表示夜间模式发生了变化，false 表示未变化
     */
    fun updateNightModeStatus(newConfig: Configuration): Boolean {
        val currentNightMode = (newConfig.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (isNightMode != currentNightMode) {
            isNightMode = currentNightMode
            Timber.d("Night mode changed: $isNightMode")
            return true
        }
        return false
    }

    /**
     * 完整的夜间模式状态更新流程
     * 包含：检测变化 → 更新地图类型 → 更新所有 View 背景/颜色
     * 用于 onResume 和 onConfigurationChanged 中调用
     * @param aMap 高德地图 AMap 实例（可能未初始化，需判空）
     * @param themedContext 使用 newConfig 创建的主题 Context，确保颜色资源正确加载
     */
    fun handleThemeUpdate(aMap: com.amap.api.maps.AMap?, themedContext: Context) {
        try {
            val nightModeChanged = updateNightModeStatus(fragment.resources.configuration)
            if (nightModeChanged && aMap != null) {
                updateMapTypeForNightMode(aMap)
            }
            updateViewBackgrounds(themedContext)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update theme")
        }
    }

    /**
     * 更新所有 View 的背景和颜色
     * 遍历 binding 中所有需要响应主题变化的 View，设置对应的白天/夜间颜色
     * @param themedContext 使用 newConfig 创建的主题 Context，确保颜色资源正确加载
     */
    fun updateViewBackgrounds(themedContext: Context) {
        try {
            val resources = themedContext.resources
            val theme = themedContext.theme

            // 从 themedContext 同步夜间模式状态
            isNightMode = (resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // ── 加载颜色资源 ──────────────────────────────────────────
            val surfaceColor = resources.getColor(R.color.surface, theme)
            val backgroundColor = resources.getColor(R.color.background, theme)
            val primaryColor = resources.getColor(R.color.primary, theme)
            val surfaceVariantColor = resources.getColor(R.color.surface_variant, theme)
            val dividerColor = resources.getColor(R.color.divider, theme)
            val textPrimaryColor = resources.getColor(R.color.text_primary, theme)
            val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)

            // ── 主要容器背景 ──────────────────────────────────────────
            binding.fragmentRoot.setBackgroundColor(backgroundColor)
            binding.searchCard.setCardBackgroundColor(surfaceColor)
            binding.bottomSheet.background = null
            binding.bottomSheet.setBackgroundResource(R.drawable.bg_bottom_sheet)
            binding.locationInfoCard.setCardBackgroundColor(primaryColor)
            binding.bottomNav.setBackgroundColor(backgroundColor)
            binding.routeControlCard.setCardBackgroundColor(surfaceColor)
            binding.routePanel.setCardBackgroundColor(surfaceColor)

            // ── 搜索相关 ──────────────────────────────────────────────
            binding.searchResultList.setBackgroundColor(surfaceColor)
            binding.searchTopDivider.setBackgroundColor(dividerColor)
            binding.searchEdit.setTextColor(textPrimaryColor)
            binding.searchEdit.setHintTextColor(resources.getColor(R.color.text_hint, theme))

            // ── 操作按钮（输入坐标/历史/收藏/路线）──────────────────────
            updateButtonIconTint(binding.inputCoordsBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.historyBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.favoriteBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.routeBtn, primaryColor, textSecondaryColor)

            // ── 路线控制栏按钮 ─────────────────────────────────────────
            binding.routePlayFabBtn.setColorFilter(primaryColor)
            binding.routeSpeedFabBtn.setColorFilter(primaryColor)
            binding.routeStopFabBtn.setColorFilter(resources.getColor(R.color.error, theme))

            // ── 右侧固定栏按钮（缩放/图层/定位）────────────────────────
            updateButtonBackground(binding.zoomInBtn, surfaceVariantColor)
            updateButtonBackground(binding.zoomOutBtn, surfaceVariantColor)
            updateButtonBackground(binding.layerBtn, surfaceVariantColor)
            updateButtonBackground(binding.locationBtn, primaryColor)

            binding.zoomInBtn.setColorFilter(textPrimaryColor)
            binding.zoomOutBtn.setColorFilter(textPrimaryColor)
            binding.layerBtn.setColorFilter(textPrimaryColor)
            binding.locationBtn.setColorFilter(android.graphics.Color.WHITE)

            // ── FAB（模拟传送按钮）─────────────────────────────────────
            binding.fab.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            binding.fab.setColorFilter(android.graphics.Color.WHITE)

            // ── BottomNavigationView ────────────────────────────────────
            val navItemColorStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(primaryColor, textSecondaryColor)
            )
            binding.bottomNav.itemIconTintList = navItemColorStateList
            binding.bottomNav.itemTextColor = navItemColorStateList

            // ── TabLayout ──────────────────────────────────────────────
            binding.panelTabs.setBackgroundColor(surfaceColor)
            binding.tabLayoutDivider.setBackgroundColor(dividerColor)
            binding.panelTabs.setTabTextColors(
                resources.getColor(R.color.text_secondary, theme),
                primaryColor
            )
            binding.panelTabs.setSelectedTabIndicatorColor(primaryColor)

            // ── 位置信息卡片文字 ──────────────────────────────────────
            binding.latitudeText.setTextColor(android.graphics.Color.WHITE)
            binding.longitudeText.setTextColor(android.graphics.Color.WHITE)
            binding.addressText.setTextColor(android.graphics.Color.WHITE)

            // ── 状态徽章 ──────────────────────────────────────────────
            binding.statusBadge.setBackgroundResource(R.drawable.bg_status_badge)
            val statusTextColor = if (isNightMode) {
                resources.getColor(R.color.text_primary, theme)
            } else {
                android.graphics.Color.WHITE
            }
            binding.statusText.setTextColor(statusTextColor)

            // ── Chip 颜色（速度选择器）─────────────────────────────────
            updateChipColors(themedContext)

            // ── 路线进度条颜色 ────────────────────────────────────────
            updateRouteProgressColors(themedContext)

            Timber.d("View backgrounds updated for theme change")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update view backgrounds")
        }
    }

    /**
     * 更新速度 Chip 颜色
     * 手动重建 ColorStateList，强制从 themedContext 加载正确的夜间/白天颜色
     *
     * 背景：configChanges="uiMode" 阻止了 Activity 重建，XML 样式中静态引用的颜色不会重新解析，
     *       必须通过代码显式创建 ColorStateList 并赋值给 Chip。
     *
     * @param themedContext 使用 newConfig 创建的主题 Context，确保 Resources 从正确的目录加载
     */
    private fun updateChipColors(themedContext: Context) {
        val resources = themedContext.resources
        val theme = themedContext.theme

        // ── 公共颜色 ────────────────────────────────────────────────
        val primaryColor = resources.getColor(R.color.primary, theme)
        // chip_mode_choice_text = 未选中文字（白天 #666666，夜间 #CCCCCC）
        val chipTextUnselected = resources.getColor(R.color.chip_mode_choice_text, theme)
        // 未选中背景 = chip_mode_choice_bg（白天 #F0F0F0，夜间 #2A2A2A）── 不用透明，避免透出地图底色
        val chipBgUnselected = resources.getColor(R.color.chip_mode_choice_bg, theme)
        // 选中态文字固定白色（与 chip_mode_choice_text_selector.xml 保持一致）
        val chipTextSelected = android.graphics.Color.WHITE

        // ── 背景 ColorStateList（选中=primary，未选中=chip_mode_choice_bg）──
        val chipBgSelector = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(primaryColor, chipBgUnselected)
        )

        // ── 速度 Chip 文字（选中=白色，未选中=chip_mode_choice_text）──
        val speedTextSelector = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(chipTextSelected, chipTextUnselected)
        )

        // 只更新速度 Chip（模式 Chip 已移除）
        listOf(binding.speed05x, binding.speed1x, binding.speed2x, binding.speed4x).forEach { chip ->
            chip.chipBackgroundColor = chipBgSelector
            chip.setTextColor(speedTextSelector)
        }

        // 强制刷新 Chip 绘制
        listOf(binding.speed05x, binding.speed1x, binding.speed2x, binding.speed4x).forEach { chip ->
            chip.invalidate()
        }

        Timber.d("Chip colors updated: primary=#${Integer.toHexString(primaryColor)}, bgUnselected=#${Integer.toHexString(chipBgUnselected)}, textUnselected=#${Integer.toHexString(chipTextUnselected)}")
    }

    /**
     * 更新路线进度条颜色
     * @param themedContext 使用 newConfig 创建的主题 Context，确保颜色从正确目录加载
     */
    private fun updateRouteProgressColors(themedContext: Context) {
        try {
            val resources = themedContext.resources
            val theme = themedContext.theme

            // 获取最新的颜色（从正确的 themedContext 加载，避免旧 Context 返回白天模式颜色）
            val primaryColor = resources.getColor(R.color.primary, theme)
            val dividerColor = resources.getColor(R.color.divider, theme)

            // 更新进度条前景色（progressTint）
            binding.routeProgress.progressTintList = android.content.res.ColorStateList.valueOf(primaryColor)

            // 更新进度条背景色（progressBackgroundTint）
            binding.routeProgress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(dividerColor)

            Timber.d("Route progress bar colors updated for theme change")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update route progress bar colors")
        }
    }

    /**
     * 更新单个按钮的背景颜色（圆角矩形）
     * @param button 目标 ImageButton
     * @param color 背景颜色值
     */
    private fun updateButtonBackground(button: android.widget.ImageButton, color: Int) {
        button.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12.dpToPx().toFloat()
            setColor(color)
        }
    }

    /**
     * 更新操作按钮的图标 tint 和文字颜色
     * 按钮容器结构：LinearLayout > ImageView + TextView
     * @param buttonContainer 包含图标和文字的 LinearLayout 容器
     * @param iconColor 图标 tint 颜色值
     * @param textColor 文字颜色值
     */
    private fun updateButtonIconTint(buttonContainer: android.widget.LinearLayout, iconColor: Int, textColor: Int) {
        try {
            // 更新图标 tint
            val icon = buttonContainer.getChildAt(0) as? android.widget.ImageView
            icon?.setColorFilter(iconColor)

            // 更新文字颜色
            val text = buttonContainer.getChildAt(1) as? android.widget.TextView
            text?.setTextColor(textColor)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update button icon tint")
        }
    }

    /**
     * dp 转 px 扩展属性
     */
    private fun Int.dpToPx(): Int {
        return (this * fragment.resources.displayMetrics.density).toInt()
    }
}

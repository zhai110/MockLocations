package com.mockloc.ui.main.delegate

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.amap.api.maps.AMap
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import timber.log.Timber

/**
 * 主题切换委托类
 * 
 * 职责：
 * - 管理夜间模式状态
 * - 更新所有 View 的背景色、文字颜色、图标 tint
 * - 处理配置变更（日夜模式切换）
 * - 更新地图类型（夜间/白天）
 */
class ThemeDelegate(
    private val fragment: Fragment,
    private val binding: FragmentMainBinding
) {
    
    private var isNightMode = false
    private var isManualLayerSelected = false
    
    /**
     * 初始化主题状态
     */
    fun init() {
        // 初始化夜间模式状态
        isNightMode = (fragment.resources.configuration.uiMode 
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Timber.d("ThemeDelegate initialized: isNightMode=$isNightMode")
    }
    
    /**
     * 获取当前夜间模式状态
     */
    fun getNightMode(): Boolean = isNightMode
    
    /**
     * 设置用户是否手动选择了图层
     */
    fun setManualLayerSelected(selected: Boolean) {
        isManualLayerSelected = selected
    }
    
    /**
     * 根据夜间模式更新地图类型
     */
    fun updateMapTypeForNightMode(aMap: AMap) {
        // 如果用户手动选择了图层，则不自动切换
        if (isManualLayerSelected) {
            Timber.d("User manually selected layer, skip auto-switch")
            return
        }
        
        val targetMapType = if (isNightMode) {
            AMap.MAP_TYPE_NIGHT
        } else {
            AMap.MAP_TYPE_NORMAL
        }
        
        if (aMap.mapType != targetMapType) {
            aMap.mapType = targetMapType
            Timber.d("Map type updated to: ${if (isNightMode) "NIGHT" else "NORMAL"}")
        }
    }
    
    /**
     * 更新夜间模式状态（响应配置变更）
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
     * 更新所有 View 的背景和颜色（简化版，核心逻辑保留）
     */
    fun updateViewBackgrounds(themedContext: Context) {
        try {
            val resources = themedContext.resources
            val theme = themedContext.theme
            
            // 更新夜间模式状态
            isNightMode = (resources.configuration.uiMode 
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            val surfaceColor = resources.getColor(R.color.surface, theme)
            val backgroundColor = resources.getColor(R.color.background, theme)
            val primaryColor = resources.getColor(R.color.primary, theme)
            val surfaceVariantColor = resources.getColor(R.color.surface_variant, theme)
            val dividerColor = resources.getColor(R.color.divider, theme)
            val textPrimaryColor = resources.getColor(R.color.text_primary, theme)
            val textSecondaryColor = resources.getColor(R.color.text_secondary, theme)
            
            // 更新主要容器背景
            binding.fragmentRoot.setBackgroundColor(backgroundColor)
            binding.searchCard.setCardBackgroundColor(surfaceColor)
            binding.bottomSheet.background = null
            binding.bottomSheet.setBackgroundResource(R.drawable.bg_bottom_sheet)
            binding.locationInfoCard.setCardBackgroundColor(primaryColor)
            binding.bottomNav.setBackgroundColor(backgroundColor)
            binding.routeControlCard.setCardBackgroundColor(surfaceColor)
            binding.routePanel.setCardBackgroundColor(surfaceColor)
            
            // 更新搜索相关
            binding.searchResultList.setBackgroundColor(surfaceColor)
            binding.searchTopDivider.setBackgroundColor(dividerColor)
            binding.searchEdit.setTextColor(textPrimaryColor)
            binding.searchEdit.setHintTextColor(resources.getColor(R.color.text_hint, theme))
            
            // 更新操作按钮
            updateButtonIconTint(binding.inputCoordsBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.historyBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.favoriteBtn, primaryColor, textSecondaryColor)
            updateButtonIconTint(binding.routeBtn, primaryColor, textSecondaryColor)
            
            // 更新路线控制栏按钮
            binding.routePlayFabBtn.setColorFilter(primaryColor)
            binding.routeSpeedFabBtn.setColorFilter(primaryColor)
            binding.routeStopFabBtn.setColorFilter(resources.getColor(R.color.error, theme))
            
            // 更新右侧固定栏按钮
            updateButtonBackground(binding.zoomInBtn, surfaceVariantColor)
            updateButtonBackground(binding.zoomOutBtn, surfaceVariantColor)
            updateButtonBackground(binding.layerBtn, surfaceVariantColor)
            updateButtonBackground(binding.locationBtn, primaryColor)
            
            binding.zoomInBtn.setColorFilter(textPrimaryColor)
            binding.zoomOutBtn.setColorFilter(textPrimaryColor)
            binding.layerBtn.setColorFilter(textPrimaryColor)
            binding.locationBtn.setColorFilter(android.graphics.Color.WHITE)
            
            // 更新 FAB
            binding.fab.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            binding.fab.setColorFilter(android.graphics.Color.WHITE)
            
            // 更新 BottomNavigationView
            val navItemColorStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(primaryColor, textSecondaryColor)
            )
            binding.bottomNav.itemIconTintList = navItemColorStateList
            binding.bottomNav.itemTextColor = navItemColorStateList
            
            // 更新 TabLayout
            binding.panelTabs.setBackgroundColor(surfaceColor)
            binding.tabLayoutDivider.setBackgroundColor(dividerColor)
            binding.panelTabs.setTabTextColors(
                resources.getColor(R.color.text_secondary, theme),
                primaryColor
            )
            binding.panelTabs.setSelectedTabIndicatorColor(primaryColor)
            
            // 更新位置信息卡片文字
            binding.latitudeText.setTextColor(android.graphics.Color.WHITE)
            binding.longitudeText.setTextColor(android.graphics.Color.WHITE)
            binding.addressText.setTextColor(android.graphics.Color.WHITE)
            
            // 更新状态徽章
            binding.statusBadge.setBackgroundResource(R.drawable.bg_status_badge)
            val statusTextColor = if (isNightMode) {
                resources.getColor(R.color.text_primary, theme)
            } else {
                android.graphics.Color.WHITE
            }
            binding.statusText.setTextColor(statusTextColor)
            
            Timber.d("View backgrounds updated for theme change")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update view backgrounds")
        }
    }
    
    /**
     * 更新单个按钮的背景颜色
     */
    private fun updateButtonBackground(button: android.widget.ImageButton, color: Int) {
        button.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12.dpToPx().toFloat()
            setColor(color)
        }
    }
    
    /**
     * 更新操作按钮的图标和文字颜色
     */
    private fun updateButtonIconTint(buttonContainer: android.widget.LinearLayout, iconColor: Int, textColor: Int) {
        // 更新图标 tint
        val icon = buttonContainer.getChildAt(0) as? android.widget.ImageView
        icon?.setColorFilter(iconColor)
        
        // 更新文字颜色
        val text = buttonContainer.getChildAt(1) as? android.widget.TextView
        text?.setTextColor(textColor)
    }
    
    /**
     * dp 转 px
     */
    private fun Int.dpToPx(): Int {
        return (this * fragment.resources.displayMetrics.density).toInt()
    }
}

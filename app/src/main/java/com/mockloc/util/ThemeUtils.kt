package com.mockloc.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import com.mockloc.R

object ThemeUtils {
    /**
     * 创建带主题的 Context
     * @param context 已经包含正确 Configuration 的 Context（例如通过 createConfigurationContext 创建）
     * @return Pair<带主题的Context, 是否夜间模式>
     */
    fun createThemedContext(context: Context): Pair<Context, Boolean> {
        // ✅ 直接从传入的 context 读取配置，不再重新创建 Configuration
        val isNight = (context.resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        // ✅ 直接包裹主题，不修改 Configuration
        return Pair(ContextThemeWrapper(context, R.style.Theme_VirtualLocation), isNight)
    }
}

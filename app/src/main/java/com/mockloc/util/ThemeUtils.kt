package com.mockloc.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import com.mockloc.R

object ThemeUtils {
    fun createThemedContext(context: Context): Pair<Context, Boolean> {
        val isNight = (context.resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val nightModeFlags = if (isNight) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val config = Configuration(context.resources.configuration).also {
            it.uiMode = (it.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightModeFlags
        }
        val configContext = context.createConfigurationContext(config)
        return Pair(ContextThemeWrapper(configContext, R.style.Theme_VirtualLocation), isNight)
    }
}

package com.mockloc.util

import org.junit.Assert.*
import org.junit.Test

/**
 * PrefsConfig 单元测试
 * 测试配置常量的正确性
 */
class PrefsConfigTest {

    /**
     * 测试配置文件名不为空
     */
    @Test
    fun testPrefsNamesAreNotEmpty() {
        assertNotNull("MAP_STATE 不应为空", PrefsConfig.MAP_STATE)
        assertNotNull("SETTINGS 不应为空", PrefsConfig.SETTINGS)
        assertNotNull("ADDRESS_CACHE 不应为空", PrefsConfig.ADDRESS_CACHE)
        assertTrue("MAP_STATE 不应为空字符串", PrefsConfig.MAP_STATE.isNotEmpty())
        assertTrue("SETTINGS 不应为空字符串", PrefsConfig.SETTINGS.isNotEmpty())
        assertTrue("ADDRESS_CACHE 不应为空字符串", PrefsConfig.ADDRESS_CACHE.isNotEmpty())
    }

    /**
     * 测试配置文件名不包含特殊字符
     */
    @Test
    fun testPrefsNamesFormat() {
        val prefsNamePattern = Regex("^[a-z_]+$")
        
        assertTrue("MAP_STATE 应该只包含小写字母和下划线", 
            prefsNamePattern.matches(PrefsConfig.MAP_STATE))
        assertTrue("SETTINGS 应该只包含小写字母和下划线", 
            prefsNamePattern.matches(PrefsConfig.SETTINGS))
        assertTrue("ADDRESS_CACHE 应该只包含小写字母和下划线", 
            prefsNamePattern.matches(PrefsConfig.ADDRESS_CACHE))
    }

    /**
     * 测试 Settings 键名不为空
     */
    @Test
    fun testSettingsKeysAreNotEmpty() {
        assertNotNull("KEY_THEME_MODE 不应为空", PrefsConfig.Settings.KEY_THEME_MODE)
        assertNotNull("KEY_LAST_LAT 不应为空", PrefsConfig.Settings.KEY_LAST_LAT)
        assertNotNull("KEY_LAST_LNG 不应为空", PrefsConfig.Settings.KEY_LAST_LNG)
        assertNotNull("KEY_LAST_ALT 不应为空", PrefsConfig.Settings.KEY_LAST_ALT)
        assertNotNull("KEY_WALK_SPEED 不应为空", PrefsConfig.Settings.KEY_WALK_SPEED)
        assertNotNull("KEY_RUN_SPEED 不应为空", PrefsConfig.Settings.KEY_RUN_SPEED)
        assertNotNull("KEY_BIKE_SPEED 不应为空", PrefsConfig.Settings.KEY_BIKE_SPEED)
        assertNotNull("KEY_SPEED_MODE 不应为空", PrefsConfig.Settings.KEY_SPEED_MODE)
    }

    /**
     * 测试 MapState 键名不为空
     */
    @Test
    fun testMapStateKeysAreNotEmpty() {
        assertNotNull("KEY_LATITUDE 不应为空", PrefsConfig.MapState.KEY_LATITUDE)
        assertNotNull("KEY_LONGITUDE 不应为空", PrefsConfig.MapState.KEY_LONGITUDE)
        assertNotNull("KEY_ZOOM 不应为空", PrefsConfig.MapState.KEY_ZOOM)
    }
}

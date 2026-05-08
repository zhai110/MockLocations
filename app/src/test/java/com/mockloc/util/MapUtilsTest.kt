package com.mockloc.util

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * MapUtils 单元测试
 * 测试坐标转换算法的正确性
 */
class MapUtilsTest {

    /**
     * 测试中国境内坐标转换
     * WGS84 -> GCJ02 -> WGS84 应该能还原（有一定误差）
     */
    @Test
    fun testWgs84ToGcj02AndBack() {
        // 北京天安门坐标
        val originalLng = 116.397128
        val originalLat = 39.916527

        // WGS84 -> GCJ02
        val gcj02 = MapUtils.wgs84ToGcj02(originalLng, originalLat)
        val gcj02Lng = gcj02[0]
        val gcj02Lat = gcj02[1]

        // 转换后坐标应该在合理范围内
        assertTrue("经度应该在合理范围", gcj02Lng > 116.3 && gcj02Lng < 116.5)
        assertTrue("纬度应该在合理范围", gcj02Lat > 39.9 && gcj02Lat < 40.0)

        // GCJ02 -> WGS84
        val wgs84 = MapUtils.gcj02ToWgs84(gcj02Lng, gcj02Lat)
        val backLng = wgs84[0]
        val backLat = wgs84[1]

        // 还原后应该接近原始坐标（误差在合理范围内）
        val lngDiff = abs(backLng - originalLng)
        val latDiff = abs(backLat - originalLat)

        assertTrue("经度还原误差应该小于0.001", lngDiff < 0.001)
        assertTrue("纬度还原误差应该小于0.001", latDiff < 0.001)
    }

    /**
     * 测试中国境外坐标不做转换
     * 日本东京坐标应该保持不变
     */
    @Test
    fun testOutOfChinaCoordinates() {
        // 日本东京坐标
        val tokyoLng = 139.6917
        val tokyoLat = 35.6895

        // WGS84 -> GCJ02 应该保持不变
        val gcj02 = MapUtils.wgs84ToGcj02(tokyoLng, tokyoLat)
        assertEquals("境外经度应该保持不变", tokyoLng, gcj02[0], 0.0001)
        assertEquals("境外纬度应该保持不变", tokyoLat, gcj02[1], 0.0001)
    }

    /**
     * 测试中国边境坐标
     * 验证边界处理正确
     */
    @Test
    fun testBorderCoordinates() {
        // 新疆喀什（西部边境）
        val kashgarLng = 75.9920
        val kashgarLat = 39.4700

        val gcj02 = MapUtils.wgs84ToGcj02(kashgarLng, kashgarLat)
        
        // 边境坐标应该仍然被转换
        assertTrue("西部边境坐标应该被转换", gcj02[0] != kashgarLng || gcj02[1] != kashgarLat)
    }

    /**
     * 测试上海坐标
     */
    @Test
    fun testShanghaiCoordinates() {
        // 上海人民广场坐标
        val shanghaiLng = 121.4716
        val shanghaiLat = 31.2304

        val gcj02 = MapUtils.wgs84ToGcj02(shanghaiLng, shanghaiLat)

        // 转换后应该在合理范围
        assertTrue("上海经度转换后应该在合理范围", gcj02[0] > 121.4 && gcj02[0] < 121.6)
        assertTrue("上海纬度转换后应该在合理范围", gcj02[1] > 31.2 && gcj02[1] < 31.3)

        // 还原测试
        val back = MapUtils.gcj02ToWgs84(gcj02[0], gcj02[1])
        assertTrue("上海坐标还原误差应该小于0.001", 
            abs(back[0] - shanghaiLng) < 0.001 && abs(back[1] - shanghaiLat) < 0.001)
    }

    /**
     * 测试深圳坐标
     */
    @Test
    fun testShenzhenCoordinates() {
        // 深圳坐标
        val shenzhenLng = 114.0579
        val shenzhenLat = 22.5431

        val gcj02 = MapUtils.wgs84ToGcj02(shenzhenLng, shenzhenLat)
        val back = MapUtils.gcj02ToWgs84(gcj02[0], gcj02[1])

        assertTrue("深圳坐标还原误差应该小于0.001",
            abs(back[0] - shenzhenLng) < 0.001 && abs(back[1] - shenzhenLat) < 0.001)
    }

    /**
     * 测试 BD09 转换
     */
    @Test
    fun testBd09Conversion() {
        // 百度地图测试坐标
        val bdLng = 116.404
        val bdLat = 39.915

        // BD09 -> WGS84
        val wgs84 = MapUtils.bd09ToWgs84(bdLng, bdLat)
        
        // 验证转换后是有效的 WGS84 坐标
        assertTrue("WGS84 经度应该在有效范围", wgs84[0] > 0 && wgs84[0] < 180)
        assertTrue("WGS84 纬度应该在有效范围", wgs84[1] > 0 && wgs84[1] < 90)

        // WGS84 -> BD09
        val backBd = MapUtils.wgs84ToBd09(wgs84[0], wgs84[1])
        
        // 还原应该接近原始坐标
        assertTrue("BD09 还原误差应该小于0.01",
            abs(backBd[0] - bdLng) < 0.01 && abs(backBd[1] - bdLat) < 0.01)
    }
}

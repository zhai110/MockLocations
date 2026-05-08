package com.mockloc.data.db

import org.junit.Assert.*
import org.junit.Test

/**
 * 数据实体单元测试
 * 测试数据模型的基本属性和方法
 */
class DataEntityTest {

    /**
     * 测试 HistoryLocation 创建和属性
     */
    @Test
    fun testHistoryLocationCreation() {
        val location = HistoryLocation(
            id = 1L,
            name = "测试位置",
            address = "测试地址",
            latitude = 39.916527,
            longitude = 116.397128,
            timestamp = System.currentTimeMillis()
        )

        assertEquals("ID 应该为 1", 1L, location.id)
        assertEquals("名称应该正确", "测试位置", location.name)
        assertEquals("地址应该正确", "测试地址", location.address)
        assertEquals("纬度应该正确", 39.916527, location.latitude, 0.0001)
        assertEquals("经度应该正确", 116.397128, location.longitude, 0.0001)
        assertTrue("时间戳应该大于0", location.timestamp > 0)
    }

    /**
     * 测试 FavoriteLocation 创建
     */
    @Test
    fun testFavoriteLocationCreation() {
        val favorite = FavoriteLocation(
            id = 1L,
            name = "收藏位置",
            address = "收藏地址",
            latitude = 31.2304,
            longitude = 121.4716,
            timestamp = System.currentTimeMillis()
        )

        assertEquals("名称应该正确", "收藏位置", favorite.name)
        assertEquals("纬度应该正确", 31.2304, favorite.latitude, 0.0001)
    }

    /**
     * 测试 SearchHistory 创建
     */
    @Test
    fun testSearchHistoryCreation() {
        val search = SearchHistory(
            id = 1L,
            keyword = "搜索关键词",
            name = "搜索结果名称",
            address = "搜索结果地址",
            latitude = 39.916527,
            longitude = 116.397128,
            timestamp = System.currentTimeMillis()
        )

        assertEquals("关键词应该正确", "搜索关键词", search.keyword)
        assertEquals("名称应该正确", "搜索结果名称", search.name)
    }

    /**
     * 测试 SavedRoute 创建
     */
    @Test
    fun testSavedRouteCreation() {
        val route = SavedRoute(
            id = 1L,
            name = "测试路线",
            routeGroup = "route_group_1",
            latitude = 39.916527,
            longitude = 116.397128,
            pointOrder = 0,
            timestamp = System.currentTimeMillis()
        )

        assertEquals("路线名称应该正确", "测试路线", route.name)
        assertEquals("路线组应该正确", "route_group_1", route.routeGroup)
        assertEquals("点顺序应该正确", 0, route.pointOrder)
    }

    /**
     * 测试数据类的 copy 方法
     */
    @Test
    fun testEntityCopy() {
        val original = HistoryLocation(
            id = 1L,
            name = "原始名称",
            address = "原始地址",
            latitude = 39.0,
            longitude = 116.0,
            timestamp = 1000L
        )

        val copied = original.copy(name = "新名称")

        assertEquals("ID 应该保持", original.id, copied.id)
        assertEquals("新名称应该正确", "新名称", copied.name)
        assertEquals("其他属性应该保持", original.address, copied.address)
        assertEquals("纬度应该保持", original.latitude, copied.latitude, 0.0001)
    }

    /**
     * 测试数据类的 equals 方法
     */
    @Test
    fun testEntityEquality() {
        val timestamp = System.currentTimeMillis()
        
        val location1 = HistoryLocation(
            id = 1L,
            name = "测试",
            address = "地址",
            latitude = 39.0,
            longitude = 116.0,
            timestamp = timestamp
        )

        val location2 = HistoryLocation(
            id = 1L,
            name = "测试",
            address = "地址",
            latitude = 39.0,
            longitude = 116.0,
            timestamp = timestamp
        )

        // 相同属性的实体应该相等
        assertEquals("相同属性的实体应该相等", location1, location2)
    }
}

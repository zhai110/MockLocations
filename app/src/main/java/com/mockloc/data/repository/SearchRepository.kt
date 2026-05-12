package com.mockloc.data.repository

import com.mockloc.core.common.AppResult
import com.mockloc.core.common.safeCall
import com.mockloc.data.db.SearchHistory
import com.mockloc.data.db.SearchHistoryDao
import com.mockloc.repository.PoiSearchHelper
import timber.log.Timber

/**
 * 搜索数据仓库 — 统一管理 POI 搜索和搜索历史
 *
 * 收口点：
 * - MainViewModel: saveToSearchHistory()、searchPlaces()
 * - HistoryActivity: deleteSearchItem()、loadData()
 * - MainFragment: 临时 PoiSearchHelper 实例（3处逆地理编码）
 */
class SearchRepository(
    private val searchHistoryDao: SearchHistoryDao,
    private val poiSearchHelper: PoiSearchHelper
) {

    // ==================== POI 搜索 ====================

    /**
     * 搜索地点
     */
    fun searchPlace(
        keyword: String,
        centerLat: Double? = null,
        centerLng: Double? = null,
        radius: Int = 20000,
        callback: (List<PoiSearchHelper.PlaceItem>) -> Unit
    ) {
        poiSearchHelper.searchPlace(keyword, callback, centerLat, centerLng, radius)
    }

    /**
     * 逆地理编码：坐标 → 地址
     */
    fun reverseGeocode(lat: Double, lng: Double, callback: (String, String) -> Unit) {
        poiSearchHelper.latLngToAddress(lat, lng) { name, address ->
            callback(name, address)
        }
    }

    // ==================== 搜索历史 ====================

    /**
     * 获取全部搜索历史（按时间倒序）
     */
    suspend fun getAllSearchHistory(): List<SearchHistory> = safeCall {
        searchHistoryDao.getAll()
    }.getOrDefault(emptyList())

    /**
     * 保存搜索历史（含去重逻辑）
     *
     * 逻辑：
     * 1. 统一坐标精度为6位小数
     * 2. 检查是否已存在相同坐标
     * 3. 已存在则更新时间戳，不存在则插入
     * 4. 限制最多100条记录
     */
    suspend fun saveToSearchHistory(
        keyword: String,
        name: String,
        address: String,
        latitude: Double,
        longitude: Double
    ): AppResult<Unit> {
        return safeCall {
            val roundedLat = roundCoordinate(latitude)
            val roundedLng = roundCoordinate(longitude)

            val existing = searchHistoryDao.findByCoordinates(roundedLat, roundedLng)

            if (existing != null) {
                // 已存在：更新时间戳
                searchHistoryDao.updateTimestamp(existing.id, System.currentTimeMillis())
                Timber.d("Updated search history timestamp: $name")
            } else {
                // 不存在：插入新记录
                val searchHistory = SearchHistory(
                    keyword = keyword,
                    name = name,
                    address = address,
                    latitude = roundedLat,
                    longitude = roundedLng
                )
                searchHistoryDao.insert(searchHistory)
                Timber.d("Saved to search history: $name")

                // 限制最多100条记录
                searchHistoryDao.limitRecords()
            }
        }
    }

    /**
     * 根据ID删除搜索历史
     */
    suspend fun deleteSearchHistoryById(id: Long): AppResult<Unit> = safeCall {
        searchHistoryDao.deleteById(id)
    }

    /**
     * 清空全部搜索历史
     */
    suspend fun clearAllSearchHistory(): AppResult<Unit> = safeCall {
        searchHistoryDao.clearAll()
    }

    companion object {
        /**
         * 统一坐标精度为 6 位小数（约 0.1 米精度）
         */
        private fun roundCoordinate(value: Double): Double {
            return Math.round(value * 1_000_000) / 1_000_000.0
        }
    }
}

package com.mockloc.data.repository

import com.mockloc.core.common.AppResult
import com.mockloc.core.common.safeCall
import com.mockloc.data.db.FavoriteLocation
import com.mockloc.data.db.FavoriteLocationDao
import com.mockloc.data.db.HistoryLocation
import com.mockloc.data.db.HistoryLocationDao
import timber.log.Timber

/**
 * 位置数据仓库 — 统一管理历史记录和收藏位置的数据访问
 *
 * 收口点：
 * - MainFragment: saveToHistory()、addToFavorite()
 * - HistoryWindowController: refreshHistory()
 * - HistoryActivity: deleteLocationItem()、loadData()
 * - FavoriteActivity: deleteItem()、loadData()
 * - LocationService: cleanupExpiredHistory()
 */
class LocationRepository(
    private val historyDao: HistoryLocationDao,
    private val favoriteDao: FavoriteLocationDao
) {

    // ==================== 历史记录 ====================

    /**
     * 获取全部历史记录（按时间倒序）
     */
    suspend fun getAllHistory(): List<HistoryLocation> = safeCall {
        historyDao.getAll()
    }.getOrDefault(emptyList())

    /**
     * 保存到历史记录（含去重逻辑）
     *
     * 逻辑：
     * 1. 获取所有已有记录
     * 2. 如果与最近一条坐标相同（6位精度），更新该条记录
     * 3. 否则插入新记录
     * 4. 清理保留最近100条
     *
     * @return AppResult 包含最终记录数
     */
    suspend fun saveToHistory(name: String, address: String, latitude: Double, longitude: Double): AppResult<Int> {
        return safeCall {
            val allRecords = historyDao.getAll()

            if (allRecords.isNotEmpty()) {
                val lastRecord = allRecords.first()
                val isSameLocation = roundCoordinate(lastRecord.latitude) == roundCoordinate(latitude) &&
                        roundCoordinate(lastRecord.longitude) == roundCoordinate(longitude)

                if (isSameLocation) {
                    // 坐标相同：更新名称和地址，刷新时间戳
                    val updatedRecord = lastRecord.copy(
                        name = name,
                        address = address,
                        timestamp = System.currentTimeMillis()
                    )
                    historyDao.update(updatedRecord)
                    Timber.d("Updated existing history record: $name")
                } else {
                    // 坐标不同：插入新记录
                    val historyLocation = HistoryLocation(
                        name = name,
                        address = address,
                        latitude = latitude,
                        longitude = longitude
                    )
                    historyDao.insert(historyLocation)
                    Timber.d("Inserted new history record: $name")
                }
            } else {
                // 无记录：直接插入
                val historyLocation = HistoryLocation(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )
                historyDao.insert(historyLocation)
                Timber.d("Inserted first history record: $name")
            }

            // 清理保留最近100条
            historyDao.keepRecentRecords(100)

            historyDao.getAll().size
        }
    }

    /**
     * 根据ID删除历史记录
     */
    suspend fun deleteHistoryById(id: Long): AppResult<Unit> = safeCall {
        historyDao.deleteById(id)
    }

    /**
     * 清理过期历史记录
     * @param cutoffTime 截止时间戳，早于该时间的记录将被删除
     */
    suspend fun deleteHistoryOlderThan(cutoffTime: Long): AppResult<Unit> = safeCall {
        historyDao.deleteOlderThan(cutoffTime)
        Timber.d("Cleaned up history older than: $cutoffTime")
    }

    // ==================== 收藏位置 ====================

    /**
     * 获取全部收藏位置（按时间倒序）
     */
    suspend fun getAllFavorites(): List<FavoriteLocation> = safeCall {
        favoriteDao.getAll()
    }.getOrDefault(emptyList())

    /**
     * 添加到收藏（先检查是否已存在）
     */
    suspend fun addToFavorite(name: String, address: String, latitude: Double, longitude: Double): AppResult<Boolean> {
        return safeCall {
            val exists = favoriteDao.exists(latitude, longitude)
            if (!exists) {
                val favorite = FavoriteLocation(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )
                favoriteDao.insert(favorite)
                Timber.d("Added to favorite: $name")
                true
            } else {
                Timber.d("Already in favorite: $name")
                false
            }
        }
    }

    /**
     * 检查是否已收藏
     */
    suspend fun isFavorite(latitude: Double, longitude: Double): Boolean = safeCall {
        favoriteDao.exists(latitude, longitude)
    }.getOrDefault(false)

    /**
     * 根据ID删除收藏
     */
    suspend fun deleteFavoriteById(id: Long): AppResult<Unit> = safeCall {
        favoriteDao.deleteById(id)
    }

    companion object {
        /**
         * 统一坐标精度为 6 位小数（约 0.1 米精度）
         * 用于确保数据库查询逻辑一致
         */
        private fun roundCoordinate(value: Double): Double {
            return Math.round(value * 1_000_000) / 1_000_000.0
        }
    }
}

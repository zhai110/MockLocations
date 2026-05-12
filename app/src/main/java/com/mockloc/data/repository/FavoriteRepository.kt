package com.mockloc.data.repository

import com.mockloc.core.common.AppResult
import com.mockloc.core.common.safeCall
import com.mockloc.data.db.FavoriteLocation
import com.mockloc.data.db.FavoriteLocationDao
import timber.log.Timber

/**
 * 收藏数据仓库 — 统一管理收藏位置的增删查
 *
 * 收口点：
 * - FavoriteActivity: deleteItem()、loadData()
 * - MainFragment: addToFavorite()
 *
 * 注意：LocationRepository 也包含收藏相关方法（addToFavorite/isFavorite），
 * 那是因为 MainFragment 同时使用历史和收藏功能。
 * FavoriteRepository 是独立的仓库，供 FavoriteActivity 单独使用。
 */
class FavoriteRepository(
    private val favoriteDao: FavoriteLocationDao
) {

    /**
     * 获取全部收藏位置（按时间倒序）
     */
    suspend fun getAllFavorites(): List<FavoriteLocation> = safeCall {
        favoriteDao.getAll()
    }.getOrDefault(emptyList())

    /**
     * 根据ID删除收藏
     */
    suspend fun deleteById(id: Long): AppResult<Unit> = safeCall {
        favoriteDao.deleteById(id)
        Timber.d("Favorite deleted: id=$id")
    }

    /**
     * 检查是否已收藏
     */
    suspend fun isFavorite(lat: Double, lng: Double): Boolean = safeCall {
        favoriteDao.exists(lat, lng)
    }.getOrDefault(false)

    /**
     * 添加收藏
     */
    suspend fun addFavorite(location: FavoriteLocation): AppResult<Boolean> {
        return safeCall {
            val exists = favoriteDao.exists(location.latitude, location.longitude)
            if (!exists) {
                favoriteDao.insert(location)
                Timber.d("Added to favorite: ${location.name}")
                true
            } else {
                Timber.d("Already in favorite: ${location.name}")
                false
            }
        }
    }
}

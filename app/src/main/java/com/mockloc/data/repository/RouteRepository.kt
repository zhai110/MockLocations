package com.mockloc.data.repository

import com.amap.api.maps.model.LatLng
import com.mockloc.core.common.AppResult
import com.mockloc.core.common.safeCall
import com.mockloc.data.db.SavedRoute
import com.mockloc.data.db.SavedRouteDao
import com.mockloc.service.RoutePoint
import timber.log.Timber

/**
 * 路线数据仓库 — 统一管理路线的保存/加载/删除
 *
 * 收口点：
 * - MainViewModel: saveRouteToDb()、loadRouteFromDb()、getSavedRouteGroups()
 */
class RouteRepository(
    private val savedRouteDao: SavedRouteDao
) {

    /**
     * 保存路线到数据库
     *
     * @param name 路线名称
     * @param points 路线点列表
     * @return AppResult 包含路线组名
     */
    suspend fun saveRoute(name: String, points: List<RoutePoint>): AppResult<String> {
        return safeCall {
            val group = "route_${System.currentTimeMillis()}"
            val routes = points.mapIndexed { index, point ->
                SavedRoute(
                    name = name,
                    routeGroup = group,
                    latitude = point.latLng.latitude,
                    longitude = point.latLng.longitude,
                    pointOrder = index
                )
            }
            savedRouteDao.insertAll(routes)
            Timber.d("Route saved: $name, ${points.size} points, group=$group")
            group
        }
    }

    /**
     * 从数据库加载路线
     *
     * @param group 路线组名
     * @return AppResult 包含 RoutePoint 列表
     */
    suspend fun loadRoute(group: String): AppResult<List<RoutePoint>> {
        return safeCall {
            val saved = savedRouteDao.getByGroup(group)
            if (saved.isEmpty()) {
                Timber.w("No route found for group: $group")
                emptyList()
            } else {
                val points = saved.map { RoutePoint(LatLng(it.latitude, it.longitude), it.timestamp) }
                Timber.d("Route loaded: $group, ${points.size} points")
                points
            }
        }
    }

    /**
     * 获取所有路线组名称（按最新时间戳降序排列）
     */
    suspend fun getAllGroups(): List<String> = safeCall {
        savedRouteDao.getAllGroups()
    }.getOrDefault(emptyList())

    /**
     * 删除指定路线组
     */
    suspend fun deleteByGroup(group: String): AppResult<Unit> = safeCall {
        savedRouteDao.deleteByGroup(group)
        Timber.d("Route deleted: $group")
    }

    /**
     * 获取路线组数量
     */
    suspend fun getGroupCount(): Int = safeCall {
        savedRouteDao.getGroupCount()
    }.getOrDefault(0)
}

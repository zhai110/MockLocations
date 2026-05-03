package com.mockloc.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_route WHERE route_group = :group ORDER BY pointOrder ASC")
    suspend fun getByGroup(group: String): List<SavedRoute>

    /**
     * 获取所有路线组名称，按最新时间戳降序排列
     * 子查询获取每个组的最大时间戳，确保最近修改的路线排在前面
     */
    @Query("""
        SELECT DISTINCT route_group 
        FROM saved_route 
        ORDER BY (
            SELECT MAX(timestamp) 
            FROM saved_route sr2 
            WHERE sr2.route_group = saved_route.route_group
        ) DESC
    """)
    suspend fun getAllGroups(): List<String>

    @Query("SELECT COUNT(DISTINCT route_group) FROM saved_route")
    suspend fun getGroupCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<SavedRoute>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: SavedRoute)

    @Delete
    suspend fun delete(route: SavedRoute)

    @Query("DELETE FROM saved_route WHERE route_group = :group")
    suspend fun deleteByGroup(group: String)

    @Query("DELETE FROM saved_route")
    suspend fun deleteAll()
}

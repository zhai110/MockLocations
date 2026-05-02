package com.mockloc.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_route WHERE route_group = :group ORDER BY pointOrder ASC")
    fun getByGroup(group: String): List<SavedRoute>

    @Query("SELECT DISTINCT route_group FROM saved_route ORDER BY route_group")
    fun getAllGroups(): List<String>

    @Query("SELECT COUNT(DISTINCT route_group) FROM saved_route")
    fun getGroupCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(routes: List<SavedRoute>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(route: SavedRoute)

    @Delete
    fun delete(route: SavedRoute)

    @Query("DELETE FROM saved_route WHERE route_group = :group")
    fun deleteByGroup(group: String)

    @Query("DELETE FROM saved_route")
    fun deleteAll()
}

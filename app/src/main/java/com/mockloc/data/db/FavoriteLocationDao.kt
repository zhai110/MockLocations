package com.mockloc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update

/**
 * 收藏位置DAO
 */
@Dao
interface FavoriteLocationDao {
    
    @Query("SELECT * FROM favorite_location ORDER BY timestamp DESC")
    suspend fun getAll(): List<FavoriteLocation>
    
    @Query("SELECT * FROM favorite_location WHERE name LIKE :keyword OR address LIKE :keyword ORDER BY timestamp DESC")
    suspend fun search(keyword: String): List<FavoriteLocation>
    
    @Query("SELECT * FROM favorite_location WHERE id = :id")
    suspend fun getById(id: Long): FavoriteLocation?
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_location WHERE ROUND(latitude, 6) = ROUND(:lat, 6) AND ROUND(longitude, 6) = ROUND(:lng, 6))")
    suspend fun exists(lat: Double, lng: Double): Boolean
    
    @Insert
    suspend fun insert(location: FavoriteLocation)
    
    @Update
    suspend fun update(location: FavoriteLocation)
    
    @Delete
    suspend fun delete(location: FavoriteLocation)
    
    @Query("DELETE FROM favorite_location")
    suspend fun deleteAll()
    
    @Query("DELETE FROM favorite_location WHERE id = :id")
    suspend fun deleteById(id: Long)
}

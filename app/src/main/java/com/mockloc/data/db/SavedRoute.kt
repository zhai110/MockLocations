package com.mockloc.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_route",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["route_group"])
    ]
)
data class SavedRoute(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "route_group")
    val routeGroup: String = "",
    val latitude: Double,
    val longitude: Double,
    val pointOrder: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

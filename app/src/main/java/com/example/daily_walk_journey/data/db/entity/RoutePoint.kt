package com.example.daily_walk_journey.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_points",
    indices = [Index(value = ["date", "timestamp"])]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val date: String
)

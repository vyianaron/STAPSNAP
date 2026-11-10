package com.example.daily_walk_journey.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.daily_walk_journey.data.db.entity.RoutePoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePointDao {
    @Insert
    suspend fun insert(routePoint: RoutePoint): Long

    @Query("SELECT * FROM route_points WHERE date = :date ORDER BY timestamp ASC")
    fun observeByDate(date: String): Flow<List<RoutePoint>>

    @Query("SELECT * FROM route_points WHERE date = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForDate(date: String): RoutePoint?
}

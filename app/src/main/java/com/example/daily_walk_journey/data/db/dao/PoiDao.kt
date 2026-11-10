package com.example.daily_walk_journey.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.daily_walk_journey.data.db.entity.Poi
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Query("SELECT * FROM pois WHERE date = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<Poi>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(poi: Poi): Long

    @Update
    suspend fun update(poi: Poi)

    @Delete
    suspend fun delete(poi: Poi)
}

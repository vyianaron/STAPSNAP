package com.example.daily_walk_journey.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.daily_walk_journey.data.db.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: Achievement): Long

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun observeAll(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE seenAt IS NULL ORDER BY unlockedAt ASC")
    fun observeUnseen(): Flow<List<Achievement>>

    @Query("UPDATE achievements SET seenAt = :seenAt WHERE `key` = :key")
    suspend fun markSeen(key: String, seenAt: Long)
}

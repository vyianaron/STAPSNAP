package com.example.daily_walk_journey.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val key: String,
    val unlockedAt: Long = System.currentTimeMillis(),
    val seenAt: Long? = null
)

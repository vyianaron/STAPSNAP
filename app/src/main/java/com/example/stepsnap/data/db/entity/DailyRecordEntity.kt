package com.example.stepsnap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_records")
data class DailyRecordEntity(
    @PrimaryKey
    val date: String,
    val steps: Long,
    val targetSteps: Long,
    val updatedAt: Long
)

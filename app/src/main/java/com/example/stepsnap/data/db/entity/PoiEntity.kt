package com.example.stepsnap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val note: String? = null,
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

package com.example.stepsnap.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "poi_photos",
    foreignKeys = [
        ForeignKey(
            entity = PoiEntity::class,
            parentColumns = ["id"],
            childColumns = ["poiId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("poiId")]
)
data class PoiPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val poiId: Long,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)

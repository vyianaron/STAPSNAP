package com.example.stepsnap.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PoiWithPhotos(
    @Embedded
    val poi: PoiEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "poiId"
    )
    val photos: List<PoiPhotoEntity>
)

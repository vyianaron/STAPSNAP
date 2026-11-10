package com.example.stepsnap.domain.model

enum class PoiCategory {
    SHOPPING,
    PHOTO_SPOT,
    PARK
}

data class PoiPhoto(
    val id: Long = 0,
    val poiId: Long,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Poi(
    val id: Long = 0,
    val name: String,
    val category: PoiCategory = PoiCategory.PHOTO_SPOT,
    val latitude: Double,
    val longitude: Double,
    val note: String? = null,
    val photoPath: String? = null,
    val photos: List<PoiPhoto> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

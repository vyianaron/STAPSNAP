package com.example.stepsnap.data.repository

import com.example.stepsnap.data.db.StepSnapDatabase
import com.example.stepsnap.data.db.entity.DailyRecordEntity
import com.example.stepsnap.data.db.entity.PhotoEntity
import com.example.stepsnap.data.db.entity.PoiEntity
import com.example.stepsnap.data.db.entity.PoiPhotoEntity
import com.example.stepsnap.data.db.entity.PoiWithPhotos
import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import com.example.stepsnap.domain.model.PoiPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class StepSnapRepository(
    private val database: StepSnapDatabase
) {
    fun observeDailyRecords(): Flow<List<DailyRecordEntity>> =
        database.dailyRecordDao().observeAll()

    fun observePhotos(): Flow<List<PhotoEntity>> =
        database.photoDao().observeAll()

    fun observePois(): Flow<List<Poi>> =
        database.poiDao().observeAllWithPhotos().map { entitiesWithPhotos ->
            entitiesWithPhotos.map { it.toDomain() }
        }

    fun observePoiWithPhotos(poiId: Long): Flow<Poi?> =
        database.poiDao().observePoiWithPhotos(poiId).map { poiWithPhotos ->
            poiWithPhotos?.toDomain()
        }

    suspend fun seedDefaultPoisIfEmpty(defaultPois: List<Poi>) {
        if (database.poiDao().count() != 0) return
        defaultPois.forEach { savePoi(it) }
    }

    suspend fun savePoi(poi: Poi) {
        val insertedId = database.poiDao().insert(poi.toEntity())
        val effectivePoiId = if (insertedId > 0) insertedId else poi.id
        val photoPath = poi.photoPath
        if (!photoPath.isNull_or_blank()) {
            database.poiDao().insertPhoto(
                PoiPhotoEntity(
                    poiId = effectivePoiId,
                    path = photoPath!!,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun addPhotoToPoi(poiId: Long, photoPath: String) {
        database.poiDao().insertPhoto(
            PoiPhotoEntity(
                poiId = poiId,
                path = photoPath,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePoiPhoto(photoId: Long, photoPath: String) {
        database.poiDao().deletePhoto(photoId)
        try {
            File(photoPath).delete()
        } catch (_: Exception) {}
    }

    suspend fun deletePoi(poiId: Long) {
        val poiWithPhotos = database.poiDao().getPoiWithPhotosSync(poiId)
        poiWithPhotos?.photos?.forEach { photo ->
            try {
                File(photo.path).delete()
            } catch (_: Exception) {}
        }
        database.poiDao().deletePoi(poiId)
    }

    suspend fun getDailyRecord(date: String): DailyRecordEntity? =
        database.dailyRecordDao().getByDate(date)

    suspend fun saveDailyRecord(record: DailyRecordEntity) {
        database.dailyRecordDao().upsert(record)
    }

    suspend fun savePhoto(photo: PhotoEntity) {
        database.photoDao().insert(photo)
    }

    private fun PoiWithPhotos.toDomain(): Poi {
        val domainPhotos = photos.sortedBy { it.createdAt }.map { photo ->
            PoiPhoto(
                id = photo.id,
                poiId = photo.poiId,
                path = photo.path,
                createdAt = photo.createdAt
            )
        }
        val firstPath = domainPhotos.firstOrNull()?.path ?: poi.photoPath
        return Poi(
            id = poi.id,
            name = poi.name,
            category = try { PoiCategory.valueOf(poi.category) } catch (_: Exception) { PoiCategory.PHOTO_SPOT },
            latitude = poi.latitude,
            longitude = poi.longitude,
            note = poi.note,
            photoPath = firstPath,
            photos = domainPhotos,
            createdAt = poi.createdAt
        )
    }

    private fun Poi.toEntity() = PoiEntity(
        id = id,
        name = name,
        category = category.name,
        latitude = latitude,
        longitude = longitude,
        note = note,
        photoPath = photoPath,
        createdAt = createdAt
    )
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

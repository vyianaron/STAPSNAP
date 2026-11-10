package com.example.stepsnap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.stepsnap.data.db.entity.PoiEntity
import com.example.stepsnap.data.db.entity.PoiPhotoEntity
import com.example.stepsnap.data.db.entity.PoiWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Query("SELECT * FROM pois ORDER BY name ASC")
    fun observeAll(): Flow<List<PoiEntity>>

    @Transaction
    @Query("SELECT * FROM pois ORDER BY name ASC")
    fun observeAllWithPhotos(): Flow<List<PoiWithPhotos>>

    @Transaction
    @Query("SELECT * FROM pois WHERE id = :id")
    fun observePoiWithPhotos(id: Long): Flow<PoiWithPhotos?>

    @Query("SELECT COUNT(*) FROM pois")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(poi: PoiEntity): Long

    @Insert
    suspend fun insertAll(pois: List<PoiEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PoiPhotoEntity): Long

    @Query("DELETE FROM poi_photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: Long)

    @Transaction
    @Query("SELECT * FROM pois WHERE id = :id")
    suspend fun getPoiWithPhotosSync(id: Long): PoiWithPhotos?

    @Query("DELETE FROM pois WHERE id = :poiId")
    suspend fun deletePoi(poiId: Long)
}

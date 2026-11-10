package com.example.daily_walk_journey.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckpointDao {

    /**
     * Menyimpan checkpoint baru ke database.
     * Returns ID baris yang baru diinsert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkpoint: Checkpoint): Long

    /**
     * Update path foto untuk checkpoint tertentu berdasarkan ID.
     */
    @Query("UPDATE checkpoints SET photoPath = :photoPath, note = :note WHERE id = :id")
    suspend fun updatePhotoAndNote(id: Long, photoPath: String, note: String?)

    /**
     * Ambil semua checkpoint untuk tanggal tertentu, diurutkan berdasarkan nomor.
     */
    @Query("SELECT * FROM checkpoints WHERE date = :date ORDER BY number ASC")
    fun getCheckpointsByDate(date: String): Flow<List<Checkpoint>>

    @Query("SELECT * FROM checkpoints WHERE date = :date AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestamp ASC")
    fun getLocatedCheckpointsByDate(date: String): Flow<List<Checkpoint>>

    /**
     * Ambil checkpoint yang belum memiliki foto (antrean) untuk tanggal tertentu.
     */
    @Query("SELECT * FROM checkpoints WHERE date = :date AND photoPath IS NULL AND totalSteps >= 1000 ORDER BY number ASC")
    fun getPendingCheckpoints(date: String): Flow<List<Checkpoint>>

    /**
     * Hitung jumlah checkpoint hari ini (untuk menentukan nomor checkpoint berikutnya).
     */
    @Query("SELECT COUNT(*) FROM checkpoints WHERE date = :date")
    suspend fun getCheckpointCountForDate(date: String): Int

    /** Ambil checkpoint berdasarkan ID untuk intent notifikasi. */
    @Query("SELECT * FROM checkpoints WHERE id = :id LIMIT 1")
    suspend fun getCheckpointById(id: Long): Checkpoint?

    /**
     * Ambil checkpoint pertama dari antrean (paling lama belum difoto).
     */
    @Query("SELECT * FROM checkpoints WHERE date = :date AND photoPath IS NULL AND totalSteps >= 1000 ORDER BY number ASC LIMIT 1")
    suspend fun getFirstPendingCheckpoint(date: String): Checkpoint?

    /**
     * Hapus semua checkpoint untuk tanggal tertentu (untuk reset harian jika diperlukan).
     */
    @Query("DELETE FROM checkpoints WHERE date = :date")
    suspend fun deleteCheckpointsByDate(date: String)

    @Query("SELECT COUNT(*) FROM checkpoints")
    suspend fun getTotalCheckpointCount(): Int

    @Query("SELECT DISTINCT date FROM checkpoints ORDER BY date DESC")
    fun observeActivityDates(): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM checkpoints ORDER BY date DESC")
    suspend fun getActivityDates(): List<String>

    @Query("""
        SELECT COALESCE(SUM(dailySteps), 0)
        FROM (
            SELECT MAX(totalSteps) AS dailySteps
            FROM checkpoints
            WHERE date < :today
            GROUP BY date
        )
    """)
    fun observeHistoricalStepTotal(today: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM checkpoints WHERE photoPath IS NOT NULL")
    fun observeTotalPhotos(): Flow<Int>
}

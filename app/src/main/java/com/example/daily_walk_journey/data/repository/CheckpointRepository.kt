package com.example.daily_walk_journey.data.repository

import com.example.daily_walk_journey.data.db.dao.CheckpointDao
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository sebagai single source of truth untuk data Checkpoint.
 * Menjadi jembatan antara ViewModel/Service dengan Room DAO.
 */
class CheckpointRepository(private val dao: CheckpointDao) {

    /**
     * Simpan checkpoint baru. Nomor urut dihitung otomatis berdasarkan jumlah
     * checkpoint yang sudah ada hari ini.
     * Returns ID checkpoint yang baru dibuat.
     */
    suspend fun saveCheckpoint(totalSteps: Long): Long {
        val today = LocalDate.now().toString()
        val existingCount = dao.getCheckpointCountForDate(today)
        val newCheckpoint = Checkpoint(
            number = existingCount + 1,
            totalSteps = totalSteps,
            photoPath = null,
            date = today
        )
        return dao.insert(newCheckpoint)
    }

    /**
     * Update path foto untuk checkpoint tertentu.
     */
    suspend fun updateCheckpointPhoto(id: Long, photoPath: String, note: String?) {
        dao.updatePhotoAndNote(id, photoPath, note)
    }

    /**
     * Ambil semua checkpoint hari ini sebagai Flow (reaktif).
     */
    fun getTodayCheckpoints(): Flow<List<Checkpoint>> {
        return dao.getCheckpointsByDate(LocalDate.now().toString())
    }

    /**
     * Ambil checkpoint yang belum difoto hari ini sebagai Flow (reaktif).
     */
    fun getTodayPendingCheckpoints(): Flow<List<Checkpoint>> {
        return dao.getPendingCheckpoints(LocalDate.now().toString())
    }

    suspend fun getCheckpointById(id: Long): Checkpoint? = dao.getCheckpointById(id)

    /**
     * Ambil checkpoint pertama yang menunggu foto (paling lama).
     */
    suspend fun getFirstPendingCheckpoint(): Checkpoint? {
        return dao.getFirstPendingCheckpoint(LocalDate.now().toString())
    }
}

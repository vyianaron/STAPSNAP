package com.example.daily_walk_journey.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity Room Database untuk setiap checkpoint yang tercapai.
 *
 * @param id Primary key auto-generate.
 * @param number Nomor urut checkpoint hari ini (1, 2, 3, ...).
 * @param totalSteps Total langkah hari ini saat checkpoint tercapai.
 * @param photoPath Path absolut file foto (null = belum difoto / masuk antrean).
 * @param latitude Koordinat lintang GPS (null = tidak tersedia).
 * @param longitude Koordinat bujur GPS (null = tidak tersedia).
 * @param timestamp Timestamp Unix (ms) saat checkpoint tercapai.
 * @param date Tanggal string "YYYY-MM-DD" untuk filter per hari.
 */
@Entity(tableName = "checkpoints")
data class Checkpoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: Int,
    val totalSteps: Long,
    val photoPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val date: String
)

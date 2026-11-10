package com.example.daily_walk_journey.data.repository

import com.example.daily_walk_journey.data.db.dao.PoiDao
import com.example.daily_walk_journey.data.db.entity.Poi
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class PoiRepository(private val dao: PoiDao) {
    fun getTodayPois(): Flow<List<Poi>> = dao.getByDate(LocalDate.now().toString())
    suspend fun save(poi: Poi) {
        if (poi.id == 0L) dao.insert(poi) else dao.update(poi)
    }
    suspend fun delete(poi: Poi) = dao.delete(poi)
}

package com.example.daily_walk_journey.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daily_walk_journey.data.db.AppDatabase
import com.example.daily_walk_journey.data.db.entity.Achievement
import com.example.daily_walk_journey.data.preferences.StepPreferences
import com.example.daily_walk_journey.data.repository.AchievementBadge
import com.example.daily_walk_journey.data.repository.AchievementCatalog
import com.example.daily_walk_journey.data.repository.AchievementRepository
import com.example.daily_walk_journey.data.repository.currentStreak
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AchievementProfileUiState(
    val totalSteps: Long = 0L,
    val totalPhotos: Int = 0,
    val streakDays: Int = 0,
    val badges: List<AchievementBadge> = AchievementCatalog.definitions.map {
        AchievementBadge(it, unlockedAt = null)
    }
)

class AchievementViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val preferences = StepPreferences(application)
    private val repository = AchievementRepository(
        database.checkpointDao(),
        database.achievementDao()
    )

    private val profileData = repository.observeProfileData(LocalDate.now().toString())

    val profileState: StateFlow<AchievementProfileUiState> = combine(
        profileData,
        preferences.todaySteps
    ) { data, todaySteps ->
        val unlockedByKey = data.unlocked.associateBy(Achievement::key)
        val dates = data.activityDates.toMutableSet()
        if (todaySteps > 0L) dates += LocalDate.now().toString()

        AchievementProfileUiState(
            totalSteps = data.historicalSteps + todaySteps,
            totalPhotos = data.totalPhotos,
            streakDays = currentStreak(dates, LocalDate.now()),
            badges = AchievementCatalog.definitions.map { definition ->
                AchievementBadge(
                    definition = definition,
                    unlockedAt = unlockedByKey[definition.key]?.unlockedAt
                )
            }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AchievementProfileUiState()
    )

    val pendingUnlocks: StateFlow<List<Achievement>> = repository.observePendingUnlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markUnlockSeen(key: String) {
        viewModelScope.launch { repository.markSeen(key) }
    }
}

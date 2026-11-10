package com.example.daily_walk_journey.data.repository

import com.example.daily_walk_journey.data.db.dao.AchievementDao
import com.example.daily_walk_journey.data.db.dao.CheckpointDao
import com.example.daily_walk_journey.data.db.entity.Achievement
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class AchievementDefinition(
    val key: String,
    val title: String,
    val description: String,
    val emoji: String,
    val requiredCheckpointCount: Int? = null,
    val requiredDailySteps: Long? = null,
    val requiredStreakDays: Int? = null
)

data class AchievementBadge(
    val definition: AchievementDefinition,
    val unlockedAt: Long?
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}

data class AchievementProfileData(
    val unlocked: List<Achievement>,
    val historicalSteps: Long,
    val totalPhotos: Int,
    val activityDates: List<String>
)

object AchievementCatalog {
    val definitions = listOf(
        AchievementDefinition(
            key = "checkpoint_1",
            title = "Checkpoint #1",
            description = "Abadikan momen perjalanan pertamamu.",
            emoji = "🥇",
            requiredCheckpointCount = 1
        ),
        AchievementDefinition(
            key = "checkpoint_10",
            title = "Checkpoint #10",
            description = "Capai sepuluh checkpoint perjalanan.",
            emoji = "🏅",
            requiredCheckpointCount = 10
        ),
        AchievementDefinition(
            key = "checkpoint_50",
            title = "Checkpoint #50",
            description = "Capai lima puluh checkpoint perjalanan.",
            emoji = "🏆",
            requiredCheckpointCount = 50
        ),
        AchievementDefinition(
            key = "steps_10000",
            title = "10.000 Langkah",
            description = "Berjalan 10.000 langkah dalam satu hari.",
            emoji = "👟",
            requiredDailySteps = 10_000
        ),
        AchievementDefinition(
            key = "streak_7",
            title = "Streak 7 Hari",
            description = "Berjalan selama tujuh hari berturut-turut.",
            emoji = "🔥",
            requiredStreakDays = 7
        )
    )

    fun find(key: String): AchievementDefinition? =
        definitions.firstOrNull { it.key == key }
}

class AchievementRepository(
    private val checkpointDao: CheckpointDao,
    private val achievementDao: AchievementDao
) {
    fun observePendingUnlocks(): Flow<List<Achievement>> = achievementDao.observeUnseen()

    fun observeProfileData(today: String): Flow<AchievementProfileData> = combine(
        achievementDao.observeAll(),
        checkpointDao.observeHistoricalStepTotal(today),
        checkpointDao.observeTotalPhotos(),
        checkpointDao.observeActivityDates()
    ) { unlocked, historicalSteps, totalPhotos, activityDates ->
        AchievementProfileData(
            unlocked = unlocked,
            historicalSteps = historicalSteps,
            totalPhotos = totalPhotos,
            activityDates = activityDates
        )
    }

    suspend fun evaluateAfterCheckpoint(checkpointId: Long): List<Achievement> {
        val checkpoint = checkpointDao.getCheckpointById(checkpointId) ?: return emptyList()
        val totalCheckpoints = checkpointDao.getTotalCheckpointCount()
        val activeDates = checkpointDao.getActivityDates().toMutableSet().apply {
            add(checkpoint.date)
        }
        val streak = currentStreak(activeDates, LocalDate.parse(checkpoint.date))

        val unlockedNow = mutableListOf<Achievement>()
        AchievementCatalog.definitions.forEach { definition ->
            val eligible = when {
                definition.requiredCheckpointCount != null ->
                    totalCheckpoints >= definition.requiredCheckpointCount
                definition.requiredDailySteps != null ->
                    checkpoint.totalSteps >= definition.requiredDailySteps
                definition.requiredStreakDays != null ->
                    streak >= definition.requiredStreakDays
                else -> false
            }
            if (eligible) {
                val achievement = Achievement(key = definition.key)
                if (achievementDao.insert(achievement) != -1L) {
                    unlockedNow += achievement
                }
            }
        }
        return unlockedNow
    }

    suspend fun markSeen(key: String) {
        achievementDao.markSeen(key, System.currentTimeMillis())
    }
}

fun currentStreak(activeDates: Collection<String>, throughDate: LocalDate): Int {
    val dateSet = activeDates.toSet()
    var date = throughDate
    if (date.toString() !in dateSet) return 0

    var streak = 0
    while (date.toString() in dateSet) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

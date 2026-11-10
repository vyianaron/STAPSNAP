package com.example.stepsnap.domain.usecase

import com.example.stepsnap.domain.model.DailyProgress
import java.time.LocalDate

object StreakCalculator {
    fun currentStreak(
        progress: List<DailyProgress>,
        today: LocalDate
    ): Int {
        val completedDates = progress
            .filter { it.targetSteps > 0 && it.steps >= it.targetSteps }
            .mapTo(mutableSetOf()) { it.date }

        var dateToCheck = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        while (dateToCheck in completedDates) {
            streak += 1
            dateToCheck = dateToCheck.minusDays(1)
        }
        return streak
    }
}

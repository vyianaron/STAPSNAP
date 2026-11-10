package com.example.stepsnap.domain.usecase

import com.example.stepsnap.domain.model.DailyProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun completedTodayCountsCurrentDayAndConsecutiveDays() {
        val progress = listOf(
            DailyProgress(today, steps = 1_200, targetSteps = 1_000),
            DailyProgress(today.minusDays(1), steps = 1_000, targetSteps = 1_000),
            DailyProgress(today.minusDays(2), steps = 1_500, targetSteps = 1_000)
        )

        assertEquals(3, StreakCalculator.currentStreak(progress, today))
    }

    @Test
    fun unfinishedTodayKeepsYesterdayStreakUntilDayEnds() {
        val progress = listOf(
            DailyProgress(today, steps = 250, targetSteps = 1_000),
            DailyProgress(today.minusDays(1), steps = 1_000, targetSteps = 1_000),
            DailyProgress(today.minusDays(2), steps = 1_100, targetSteps = 1_000)
        )

        assertEquals(2, StreakCalculator.currentStreak(progress, today))
    }

    @Test
    fun missedDayResetsStreak() {
        val progress = listOf(
            DailyProgress(today, steps = 0, targetSteps = 1_000),
            DailyProgress(today.minusDays(1), steps = 300, targetSteps = 1_000),
            DailyProgress(today.minusDays(2), steps = 1_000, targetSteps = 1_000)
        )

        assertEquals(0, StreakCalculator.currentStreak(progress, today))
    }
}

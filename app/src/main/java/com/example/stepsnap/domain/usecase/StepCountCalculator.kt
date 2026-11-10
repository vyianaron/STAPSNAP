package com.example.stepsnap.domain.usecase

import java.time.LocalDate

data class StepCountUpdate(
    val date: String,
    val sensorBaseline: Long,
    val stepsToday: Long
)

object StepCountCalculator {
    fun calculate(
        sensorTotal: Long,
        storedDate: String?,
        storedBaseline: Long?,
        today: LocalDate,
        existingStepsToday: Long = 0L
    ): StepCountUpdate {
        val todayKey = today.toString()
        val baselineIsValid =
            storedDate == todayKey && storedBaseline != null && sensorTotal >= storedBaseline

        val baseline = if (baselineIsValid) {
            storedBaseline!!
        } else {
            (sensorTotal - existingStepsToday).coerceAtLeast(0L)
        }
        val steps = if (baselineIsValid) sensorTotal - baseline else sensorTotal - baseline

        return StepCountUpdate(
            date = todayKey,
            sensorBaseline = baseline,
            stepsToday = steps
        )
    }
}

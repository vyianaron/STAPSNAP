package com.example.stepsnap.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StepCountCalculatorTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun firstSensorReadingEstablishesBaseline() {
        val result = StepCountCalculator.calculate(
            sensorTotal = 8_420,
            storedDate = null,
            storedBaseline = null,
            today = today
        )

        assertEquals(today.toString(), result.date)
        assertEquals(8_420L, result.sensorBaseline)
        assertEquals(0L, result.stepsToday)
    }

    @Test
    fun sameDayReadingSubtractsStoredBaseline() {
        val result = StepCountCalculator.calculate(
            sensorTotal = 8_670,
            storedDate = today.toString(),
            storedBaseline = 8_420,
            today = today
        )

        assertEquals(250L, result.stepsToday)
    }

    @Test
    fun newDayEstablishesNewBaseline() {
        val tomorrow = today.plusDays(1)
        val result = StepCountCalculator.calculate(
            sensorTotal = 9_020,
            storedDate = today.toString(),
            storedBaseline = 8_420,
            today = tomorrow
        )

        assertEquals(tomorrow.toString(), result.date)
        assertEquals(9_020L, result.sensorBaseline)
        assertEquals(0L, result.stepsToday)
    }

    @Test
    fun sensorCounterResetEstablishesNewBaseline() {
        val result = StepCountCalculator.calculate(
            sensorTotal = 12,
            storedDate = today.toString(),
            storedBaseline = 8_420,
            today = today
        )

        assertEquals(12L, result.sensorBaseline)
        assertEquals(0L, result.stepsToday)
    }
}

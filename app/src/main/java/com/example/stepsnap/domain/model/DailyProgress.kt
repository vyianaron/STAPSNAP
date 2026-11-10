package com.example.stepsnap.domain.model

import java.time.LocalDate

data class DailyProgress(
    val date: LocalDate,
    val steps: Long,
    val targetSteps: Long
)

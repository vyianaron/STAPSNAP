package com.example.stepsnap.domain.model

import java.time.LocalDate

data class DailyRecap(
    val date: LocalDate,
    val steps: Long,
    val photoPath: String? = null
)

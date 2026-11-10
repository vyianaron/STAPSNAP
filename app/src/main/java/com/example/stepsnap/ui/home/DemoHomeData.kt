package com.example.stepsnap.ui.home

import com.example.stepsnap.domain.model.DailyRecap
import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import java.time.LocalDate

internal object DemoHomeData {
    const val steps = 180L
    const val target = 1_000L
    const val streak = 2

    fun pois(
        marketName: String,
        photoSpotName: String,
        parkName: String
    ) = listOf(
        Poi(
            id = 1,
            name = marketName,
            category = PoiCategory.SHOPPING,
            latitude = -7.9788,
            longitude = 112.6303
        ),
        Poi(
            id = 2,
            name = photoSpotName,
            category = PoiCategory.PHOTO_SPOT,
            latitude = -7.9811,
            longitude = 112.6305
        ),
        Poi(
            id = 3,
            name = parkName,
            category = PoiCategory.PARK,
            latitude = -7.9821,
            longitude = 112.6308
        )
    )

    fun recaps(today: LocalDate) = listOf(
        DailyRecap(today, steps),
        DailyRecap(today.minusDays(1), 1_240),
        DailyRecap(today.minusDays(2), 1_030)
    )
}

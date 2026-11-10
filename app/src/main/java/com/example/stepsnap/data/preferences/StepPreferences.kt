package com.example.stepsnap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.stepsnap.domain.usecase.StepCountCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

private val Context.stepSnapDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "stepsnap_settings")

class StepPreferences(context: Context) {
    private val dataStore = context.applicationContext.stepSnapDataStore
    private val updateMutex = Mutex()

    suspend fun updateFromSensor(
        sensorTotal: Long,
        today: LocalDate,
        existingStepsToday: Long = 0L
    ): Long = updateMutex.withLock {
        val stored = dataStore.data.first()
        val storedDate = stored[DATE_KEY]
        val storedBaseline = stored[BASELINE_KEY]
        val update = StepCountCalculator.calculate(
            sensorTotal = sensorTotal,
            storedDate = storedDate,
            storedBaseline = storedBaseline,
            today = today,
            existingStepsToday = existingStepsToday
        )

        if (storedDate != update.date || storedBaseline != update.sensorBaseline) {
            dataStore.edit { preferences ->
                preferences[DATE_KEY] = update.date
                preferences[BASELINE_KEY] = update.sensorBaseline
            }
        }
        update.stepsToday
    }

    private companion object {
        val DATE_KEY = stringPreferencesKey("step_baseline_date")
        val BASELINE_KEY = longPreferencesKey("step_sensor_baseline")
    }
}

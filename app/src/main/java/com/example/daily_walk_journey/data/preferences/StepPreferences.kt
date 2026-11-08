package com.example.daily_walk_journey.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "step_preferences")

class StepPreferences(private val context: Context) {

    companion object {
        private val KEY_TODAY_STEPS = longPreferencesKey("today_steps")
        private val KEY_LAST_SENSOR_STEPS = longPreferencesKey("last_sensor_steps")
        private val KEY_LAST_CHECKPOINT_STEPS = longPreferencesKey("last_checkpoint_steps")
        private val KEY_STEP_INTERVAL = intPreferencesKey("step_interval")
        private val KEY_IS_TRACKING = booleanPreferencesKey("is_tracking")
        private val KEY_LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
    }

    val todaySteps: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_TODAY_STEPS] ?: 0L
    }

    val lastSensorSteps: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_SENSOR_STEPS] ?: 0L
    }

    val lastCheckpointSteps: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_CHECKPOINT_STEPS] ?: 0L
    }

    val stepInterval: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_STEP_INTERVAL] ?: 1000
    }

    val isTracking: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_TRACKING] ?: false
    }

    val lastResetDate: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_RESET_DATE] ?: ""
    }

    /**
     * Memproses pembacaan sensor hardware TYPE_STEP_COUNTER.
     * Mengembalikan pair: (todaySteps: Long, isCheckpointReached: Boolean)
     */
    suspend fun processSensorStep(currentSensorValue: Long): Pair<Long, Boolean> {
        val today = LocalDate.now().toString()
        var updatedTodaySteps = 0L
        var checkpointReached = false

        context.dataStore.edit { preferences ->
            val savedDate = preferences[KEY_LAST_RESET_DATE] ?: ""
            val lastSensor = preferences[KEY_LAST_SENSOR_STEPS] ?: 0L
            var currentToday = preferences[KEY_TODAY_STEPS] ?: 0L
            var lastCheckpoint = preferences[KEY_LAST_CHECKPOINT_STEPS] ?: 0L
            val interval = preferences[KEY_STEP_INTERVAL] ?: 1000

            // 1. Deteksi pergantian hari (reset harian)
            if (savedDate != today) {
                currentToday = 0L
                lastCheckpoint = 0L
                preferences[KEY_LAST_RESET_DATE] = today
            }

            // 2. Deteksi instalasi awal atau reboot HP
            val delta = when {
                lastSensor == 0L -> {
                    // Pertama kali aplikasi membaca sensor
                    0L
                }
                currentSensorValue < lastSensor -> {
                    // Terjadi reboot: sensor counter kembali dari 0
                    currentSensorValue
                }
                else -> {
                    currentSensorValue - lastSensor
                }
            }

            currentToday += delta
            preferences[KEY_TODAY_STEPS] = currentToday
            preferences[KEY_LAST_SENSOR_STEPS] = currentSensorValue

            // 3. Deteksi pencapaian checkpoint
            if (currentToday - lastCheckpoint >= interval && currentToday > 0) {
                preferences[KEY_LAST_CHECKPOINT_STEPS] = currentToday
                checkpointReached = true
            }

            updatedTodaySteps = currentToday
        }

        return Pair(updatedTodaySteps, checkpointReached)
    }

    suspend fun setTracking(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_TRACKING] = active
            if (!active) {
                // Saat berhenti, kita reset lastSensorSteps agar saat mulai lagi tidak loncat
                preferences[KEY_LAST_SENSOR_STEPS] = 0L
            }
        }
    }

    suspend fun setStepInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STEP_INTERVAL] = interval
        }
    }

    suspend fun resetToday() {
        context.dataStore.edit { preferences ->
            preferences[KEY_TODAY_STEPS] = 0L
            preferences[KEY_LAST_CHECKPOINT_STEPS] = 0L
            preferences[KEY_LAST_RESET_DATE] = LocalDate.now().toString()
        }
    }
}

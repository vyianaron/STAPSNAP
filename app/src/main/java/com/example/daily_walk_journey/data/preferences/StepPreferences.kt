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

data class StepProgressResult(
    val todaySteps: Long,
    val checkpointTargetReached: Long?
)

class StepPreferences(private val context: Context) {

    companion object {
        private val KEY_TODAY_STEPS = longPreferencesKey("today_steps")
        private val KEY_LAST_SENSOR_STEPS = longPreferencesKey("last_sensor_steps")
        const val DEFAULT_DAILY_TARGET_STEPS = 1_000L
        const val DAILY_TARGET_INCREMENT_STEPS = 1_000L
        private val KEY_DAILY_TARGET_STEPS = longPreferencesKey("daily_target_steps")
        private val KEY_LAST_CHECKPOINT_STEPS = longPreferencesKey("last_checkpoint_steps")
        private val KEY_LAST_CHECKPOINT_INTERVAL = intPreferencesKey("last_checkpoint_interval")
        private val KEY_IS_TRACKING = booleanPreferencesKey("is_tracking")
        private val KEY_LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
    }

    val todaySteps: Flow<Long> = context.dataStore.data.map { preferences ->
        val isToday = preferences[KEY_LAST_RESET_DATE] == LocalDate.now().toString()
        if (isToday) preferences[KEY_TODAY_STEPS] ?: 0L else 0L
    }

    val lastSensorSteps: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_SENSOR_STEPS] ?: 0L
    }

    val lastCheckpointSteps: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_CHECKPOINT_STEPS] ?: 0L
    }

    val dailyTargetSteps: Flow<Long> = context.dataStore.data.map { preferences ->
        if (preferences[KEY_LAST_RESET_DATE] == LocalDate.now().toString()) {
            preferences[KEY_DAILY_TARGET_STEPS] ?: DEFAULT_DAILY_TARGET_STEPS
        } else {
            DEFAULT_DAILY_TARGET_STEPS
        }
    }

    val isTracking: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_TRACKING] ?: false
    }

    val lastResetDate: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_RESET_DATE] ?: ""
    }

    /**
     * Memproses pembacaan sensor hardware TYPE_STEP_COUNTER.
     * Mengembalikan jumlah langkah hari ini dan target checkpoint yang baru tercapai.
     */
    suspend fun processSensorStep(currentSensorValue: Long): StepProgressResult {
        val today = LocalDate.now().toString()
        var updatedTodaySteps = 0L
        var checkpointTargetReached: Long? = null

        context.dataStore.edit { preferences ->
            val savedDate = preferences[KEY_LAST_RESET_DATE] ?: ""
            val lastSensor = preferences[KEY_LAST_SENSOR_STEPS] ?: 0L
            var currentToday = preferences[KEY_TODAY_STEPS] ?: 0L
            var lastCheckpoint = preferences[KEY_LAST_CHECKPOINT_STEPS] ?: 0L
            var currentTarget = preferences[KEY_DAILY_TARGET_STEPS] ?: DEFAULT_DAILY_TARGET_STEPS
            val savedInterval = preferences[KEY_LAST_CHECKPOINT_INTERVAL] ?: 0

            // 1. Deteksi pergantian hari (reset harian)
            if (savedDate != today) {
                currentToday = 0L
                lastCheckpoint = 0L
                currentTarget = DEFAULT_DAILY_TARGET_STEPS
                preferences[KEY_LAST_RESET_DATE] = today
                preferences[KEY_DAILY_TARGET_STEPS] = currentTarget
            }
            if (savedInterval != DAILY_TARGET_INCREMENT_STEPS.toInt()) {
                lastCheckpoint = 0L
                preferences[KEY_LAST_CHECKPOINT_STEPS] = 0L
                preferences[KEY_LAST_CHECKPOINT_INTERVAL] = DAILY_TARGET_INCREMENT_STEPS.toInt()
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

            // 3. Checkpoint hanya dibuat setelah target yang aktif tercapai.
            if (currentToday >= currentTarget && lastCheckpoint < currentTarget) {
                lastCheckpoint = currentTarget
                preferences[KEY_LAST_CHECKPOINT_STEPS] = lastCheckpoint
                checkpointTargetReached = currentTarget
            }

            updatedTodaySteps = currentToday
        }

        return StepProgressResult(updatedTodaySteps, checkpointTargetReached)
    }

    suspend fun addDailyTarget(): Boolean {
        val today = LocalDate.now().toString()
        var targetAdded = false

        context.dataStore.edit { preferences ->
            val isToday = preferences[KEY_LAST_RESET_DATE] == today
            val currentSteps = if (isToday) preferences[KEY_TODAY_STEPS] ?: 0L else 0L
            val currentTarget = if (isToday) {
                preferences[KEY_DAILY_TARGET_STEPS] ?: DEFAULT_DAILY_TARGET_STEPS
            } else {
                DEFAULT_DAILY_TARGET_STEPS
            }

            val lastCheckpoint = if (isToday) {
                preferences[KEY_LAST_CHECKPOINT_STEPS] ?: 0L
            } else {
                0L
            }
            if (currentSteps >= currentTarget && lastCheckpoint >= currentTarget) {
                preferences[KEY_DAILY_TARGET_STEPS] =
                    currentTarget + DAILY_TARGET_INCREMENT_STEPS
                targetAdded = true
            }
        }

        return targetAdded
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

    suspend fun resetToday() {
        context.dataStore.edit { preferences ->
            preferences[KEY_TODAY_STEPS] = 0L
            preferences[KEY_LAST_CHECKPOINT_STEPS] = 0L
            preferences[KEY_DAILY_TARGET_STEPS] = DEFAULT_DAILY_TARGET_STEPS
            preferences[KEY_LAST_RESET_DATE] = LocalDate.now().toString()
        }
    }
}

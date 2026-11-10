package com.example.stepsnap.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

class StepCounterManager(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var sensorListener: SensorEventListener? = null

    fun start(onStepCount: (Long) -> Unit): Boolean {
        if (sensorListener != null) return true
        if (!hasActivityRecognitionPermission()) return false

        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val totalSteps = event
                    ?.takeIf { it.sensor.type == Sensor.TYPE_STEP_COUNTER }
                    ?.values
                    ?.firstOrNull()
                    ?.toLong()
                if (totalSteps != null) onStepCount(totalSteps)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        return try {
            val registered = sensorManager?.registerListener(
                listener,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            ) == true
            if (registered) sensorListener = listener
            registered
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

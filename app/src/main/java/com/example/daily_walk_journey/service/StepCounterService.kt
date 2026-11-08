package com.example.daily_walk_journey.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.daily_walk_journey.MainActivity
import com.example.daily_walk_journey.data.preferences.StepPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StepCounterService : Service(), SensorEventListener {

    private val binder = StepBinder()
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferences: StepPreferences

    inner class StepBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }

    companion object {
        const val CHANNEL_TRACKING_ID = "step_counter_tracking_channel"
        const val CHANNEL_CHECKPOINT_ID = "step_counter_checkpoint_channel"
        const val NOTIFICATION_ID_TRACKING = 1001
        const val NOTIFICATION_ID_CHECKPOINT = 1002

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_OPEN_CAMERA = "open_camera"

        private val _stepCount = MutableStateFlow(0L)
        val stepCount: StateFlow<Long> = _stepCount.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = StepPreferences(this)
        createNotificationChannels()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Muat nilai langkah yang tersimpan sebelumnya
        serviceScope.launch {
            val current = preferences.todaySteps.first()
            _stepCount.value = current
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
            ACTION_START, null -> {
                startForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = buildTrackingNotification(_stepCount.value)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_TRACKING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID_TRACKING, notification)
        }

        stepSensor?.let { sensor ->
            // Menggunakan normal delay untuk efisiensi baterai saat background tracking
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        _isServiceRunning.value = true

        serviceScope.launch {
            preferences.setTracking(true)
        }
    }

    private fun stopTracking() {
        sensorManager?.unregisterListener(this)
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)

        serviceScope.launch {
            preferences.setTracking(false)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val sensorTotal = event.values[0].toLong()

            serviceScope.launch {
                val (todaySteps, checkpointReached) = preferences.processSensorStep(sensorTotal)
                _stepCount.value = todaySteps
                updateTrackingNotification(todaySteps)

                if (checkpointReached) {
                    showCheckpointNotification(todaySteps)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateTrackingNotification(steps: Long) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_TRACKING, buildTrackingNotification(steps))
    }

    private fun buildTrackingNotification(steps: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_TRACKING_ID)
            .setContentTitle("STEPSNAP - Melacak Langkah")
            .setContentText("Langkah hari ini: $steps")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCheckpointNotification(steps: Long) {
        val openCameraIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CAMERA, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openCameraIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_CHECKPOINT_ID)
            .setContentTitle("🎉 Checkpoint Tercapai!")
            .setContentText("Anda telah mencapai $steps langkah. Abadikan momen perjalanan Anda sekarang!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_CHECKPOINT, notification)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // 1. Channel untuk Ongoing Foreground Service
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING_ID,
            "Pelacakan Langkah Aktif",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Menampilkan jumlah langkah saat pelacakan aktif berjalan di latar belakang"
        }

        // 2. Channel untuk Notifikasi Checkpoint
        val checkpointChannel = NotificationChannel(
            CHANNEL_CHECKPOINT_ID,
            "Pengingat Checkpoint Foto",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Memberitahu pengguna saat target kelipatan langkah tercapai untuk foto"
            enableVibration(true)
        }

        manager.createNotificationChannel(trackingChannel)
        manager.createNotificationChannel(checkpointChannel)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }
}

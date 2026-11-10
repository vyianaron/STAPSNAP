package com.example.daily_walk_journey.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.daily_walk_journey.MainActivity
import com.example.daily_walk_journey.data.db.AppDatabase
import com.example.daily_walk_journey.data.db.dao.RoutePointDao
import com.example.daily_walk_journey.data.db.entity.RoutePoint
import com.example.daily_walk_journey.data.preferences.StepPreferences
import com.example.daily_walk_journey.data.repository.AchievementRepository
import com.example.daily_walk_journey.data.repository.CheckpointRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.time.Instant
import java.time.ZoneId

class StepCounterService : Service(), SensorEventListener {

    private val binder = StepBinder()
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var stepSensorRegistered = false
    private lateinit var routePointDao: RoutePointDao
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private val routeSaveMutex = Mutex()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferences: StepPreferences
    private lateinit var checkpointRepository: CheckpointRepository
    private lateinit var achievementRepository: AchievementRepository

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
        const val ACTION_ENABLE_LOCATION = "ACTION_ENABLE_LOCATION"
        const val EXTRA_OPEN_CAMERA = "open_camera"
        const val EXTRA_CHECKPOINT_ID = "checkpoint_id"

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

        fun enableLocationTracking(context: Context) {
            if (!_isServiceRunning.value) return
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_ENABLE_LOCATION
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = StepPreferences(this)
        val database = AppDatabase.getInstance(this)
        checkpointRepository = CheckpointRepository(database.checkpointDao())
        routePointDao = database.routePointDao()
        achievementRepository = AchievementRepository(
            database.checkpointDao(),
            database.achievementDao()
        )
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
            ACTION_START, ACTION_ENABLE_LOCATION, null -> {
                startForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val locationPermissionGranted = hasLocationPermission()
        val notification = buildTrackingNotification(_stepCount.value)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                (if (locationPermissionGranted) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                })
            try {
                startForeground(NOTIFICATION_ID_TRACKING, notification, serviceType)
            } catch (_: SecurityException) {
                startForeground(
                    NOTIFICATION_ID_TRACKING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            }
        } else {
            startForeground(NOTIFICATION_ID_TRACKING, notification)
        }

        if (!stepSensorRegistered) {
            stepSensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                stepSensorRegistered = true
            }
        }
        _isServiceRunning.value = true

        if (locationPermissionGranted) startLocationUpdates()

        serviceScope.launch {
            preferences.setTracking(true)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun startLocationUpdates() {
        if (locationCallback != null || !hasLocationPermission()) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    serviceScope.launch { persistRouteLocation(location) }
                }
            }
        }
        locationCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (locationCallback === callback) locationCallback = null
                }
        } catch (_: SecurityException) {
            locationCallback = null
        }
    }

    private suspend fun persistRouteLocation(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > 50f) return

        routeSaveMutex.withLock {
            val timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            val date = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            val previousPoint = routePointDao.getLatestForDate(date)
            if (previousPoint != null) {
                val previousLocation = Location("stepsnap-route").apply {
                    latitude = previousPoint.latitude
                    longitude = previousPoint.longitude
                }
                if (previousLocation.distanceTo(location) < 5f) return
            }

            routePointDao.insert(
                RoutePoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = timestamp,
                    date = date
                )
            )
        }
    }

    private fun stopTracking() {
        sensorManager?.unregisterListener(this)
        stepSensorRegistered = false
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
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
                val progress = preferences.processSensorStep(sensorTotal)
                val todaySteps = progress.todaySteps
                _stepCount.value = todaySteps
                updateTrackingNotification(todaySteps)

                progress.checkpointTargetReached?.let { target ->
                    // Simpan checkpoint ke Room Database secara otomatis
                    val checkpointId = checkpointRepository.saveCheckpoint(todaySteps)
                    achievementRepository.evaluateAfterCheckpoint(checkpointId)
                    showCheckpointNotification(target, todaySteps, checkpointId)
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
            .setContentText(
                if (hasLocationPermission()) {
                    "Langkah hari ini: $steps • rute direkam"
                } else {
                    "Langkah hari ini: $steps"
                }
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCheckpointNotification(targetSteps: Long, steps: Long, checkpointId: Long = -1L) {
        val openCameraIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CAMERA, true)
            putExtra(EXTRA_CHECKPOINT_ID, checkpointId)
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
            .setContentText("Target ${targetSteps / 1000}.000 langkah tercapai. Total hari ini: $steps. Abadikan checkpoint!")
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
            description = "Memberitahu pengguna setiap target checkpoint 1.000 langkah tercapai untuk foto"
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

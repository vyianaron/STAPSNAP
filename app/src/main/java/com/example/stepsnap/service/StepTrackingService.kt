package com.example.stepsnap.service

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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.stepsnap.MainActivity
import com.example.stepsnap.R
import com.example.stepsnap.StepSnapApplication
import com.example.stepsnap.data.db.entity.DailyRecordEntity
import com.example.stepsnap.data.preferences.StepPreferences
import com.example.stepsnap.data.repository.StepSnapRepository
import com.example.stepsnap.domain.model.MapLocation
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class StepTrackingService : Service(), SensorEventListener {

    private val binder = StepBinder()
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var activeSensorType: Int? = null
    private var sensorRegistered = false

    private var lastAccelStepTimestamp = 0L

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferences: StepPreferences
    private lateinit var repository: StepSnapRepository
    private val sensorUpdateMutex = Mutex()

    inner class StepBinder : Binder() {
        fun getService(): StepTrackingService = this@StepTrackingService
    }

    companion object {
        const val CHANNEL_TRACKING_ID = "stepsnap_tracking_channel"
        const val CHANNEL_WARNING_ID = "stepsnap_warning_channel"
        const val NOTIFICATION_ID_TRACKING = 2001
        const val NOTIFICATION_ID_WARNING = 2002

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        private const val MAX_WALKING_SPEED_KMH = 15.0f

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _stepCount = MutableStateFlow(0L)
        val stepCount: StateFlow<Long> = _stepCount.asStateFlow()

        private val _speedWarningMessage = MutableStateFlow<String?>(null)
        val speedWarningMessage: StateFlow<String?> = _speedWarningMessage.asStateFlow()

        private val _currentLocation = MutableStateFlow<MapLocation?>(null)
        val currentLocation: StateFlow<MapLocation?> = _currentLocation.asStateFlow()

        private val _routePoints = MutableStateFlow<List<MapLocation>>(emptyList())
        val routePoints: StateFlow<List<MapLocation>> = _routePoints.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, StepTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearWarning() {
            _speedWarningMessage.value = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = StepPreferences(this)
        repository = (application as StepSnapApplication).repository
        createNotificationChannels()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        serviceScope.launch {
            val today = LocalDate.now().toString()
            val existingRecord = repository.getDailyRecord(today)
            if (existingRecord != null && existingRecord.steps > _stepCount.value) {
                _stepCount.value = existingRecord.steps
            }
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
        _speedWarningMessage.value = null
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

        if (!sensorRegistered) {
            val targetSensor = stepSensor ?: stepDetectorSensor ?: accelerometerSensor
            if (targetSensor != null) {
                sensorManager?.registerListener(this, targetSensor, SensorManager.SENSOR_DELAY_NORMAL)
                activeSensorType = targetSensor.type
                sensorRegistered = true
            } else {
                _speedWarningMessage.value = getString(R.string.sensor_unavailable)
            }
        }
        _isTracking.value = true

        if (locationPermissionGranted) {
            startLocationUpdates()
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
                    processLocationUpdate(location)
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

    private fun processLocationUpdate(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > 35f) return

        var speedKmH = 0f
        if (location.hasSpeed() && location.speed > 0f) {
            speedKmH = location.speed * 3.6f
        } else {
            val prev = lastLocation
            if (prev != null) {
                val timeDeltaSec = (location.time - prev.time) / 1000f
                if (timeDeltaSec in 1.0f..30.0f) {
                    val distMeters = prev.distanceTo(location)
                    speedKmH = (distMeters / timeDeltaSec) * 3.6f
                }
            }
        }
        lastLocation = location

        if (speedKmH > MAX_WALKING_SPEED_KMH) {
            // Unnatural speed detected!
            val speedInt = speedKmH.toInt()
            val warning = getString(R.string.unnatural_speed_warning_detail, speedInt)
            _speedWarningMessage.value = warning
            showSpeedWarningNotification(warning)
            stopTracking()
            stopSelf()
            return
        }

        // Normal speed -> Update map location and route points
        _currentLocation.value = MapLocation(location.latitude, location.longitude)
        val lastPoint = _routePoints.value.lastOrNull()
        val movedEnough = if (lastPoint == null) {
            true
        } else {
            val dist = FloatArray(1)
            Location.distanceBetween(
                lastPoint.latitude,
                lastPoint.longitude,
                location.latitude,
                location.longitude,
                dist
            )
            dist[0] >= 5f
        }
        if (movedEnough) {
            _routePoints.value = _routePoints.value + MapLocation(location.latitude, location.longitude)
        }
    }

    private fun stopTracking() {
        sensorManager?.unregisterListener(this)
        sensorRegistered = false
        activeSensorType = null
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        _isTracking.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorType = event?.sensor?.type ?: return
        when (sensorType) {
            Sensor.TYPE_STEP_COUNTER -> {
                val sensorTotal = event.values[0].toLong()
                serviceScope.launch {
                    sensorUpdateMutex.withLock {
                        val today = LocalDate.now()
                        val dateStr = today.toString()
                        val existingRecord = repository.getDailyRecord(dateStr)
                        val existingSteps = existingRecord?.steps ?: 0L
                        val todaySteps = preferences.updateFromSensor(sensorTotal, today, existingSteps)
                        val newSteps = maxOf(todaySteps, existingSteps)
                        _stepCount.value = newSteps

                        repository.saveDailyRecord(
                            DailyRecordEntity(
                                date = dateStr,
                                steps = newSteps,
                                targetSteps = existingRecord?.targetSteps ?: 1_000L,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        updateTrackingNotification(newSteps)
                    }
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                serviceScope.launch {
                    sensorUpdateMutex.withLock {
                        incrementStepCount()
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val now = System.currentTimeMillis()

                if (magnitude > 12.0f && (now - lastAccelStepTimestamp) >= 300L) {
                    lastAccelStepTimestamp = now
                    serviceScope.launch {
                        sensorUpdateMutex.withLock {
                            incrementStepCount()
                        }
                    }
                }
            }
        }
    }

    private suspend fun incrementStepCount() {
        val today = LocalDate.now()
        val dateStr = today.toString()
        val currentRecord = repository.getDailyRecord(dateStr)
        val currentSteps = maxOf(_stepCount.value, currentRecord?.steps ?: 0L) + 1L
        _stepCount.value = currentSteps

        repository.saveDailyRecord(
            DailyRecordEntity(
                date = dateStr,
                steps = currentSteps,
                targetSteps = currentRecord?.targetSteps ?: 1_000L,
                updatedAt = System.currentTimeMillis()
            )
        )

        updateTrackingNotification(currentSteps)
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

        val stopIntent = Intent(this, StepTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_TRACKING_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text, steps))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_step),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showSpeedWarningNotification(message: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_WARNING_ID)
            .setContentTitle(getString(R.string.unnatural_speed_warning_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_WARNING, notification)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING_ID,
            "Pelacakan Langkah Aktif",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Menampilkan jumlah langkah dan status pelacakan di latar belakang"
        }

        val warningChannel = NotificationChannel(
            CHANNEL_WARNING_ID,
            "Peringatan Kecepatan",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Peringatan jika terdeteksi kecepatan di luar batas jalan kaki"
            enableVibration(true)
        }

        manager.createNotificationChannel(trackingChannel)
        manager.createNotificationChannel(warningChannel)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }
}

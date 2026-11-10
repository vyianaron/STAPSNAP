package com.example.stepsnap.ui.home

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stepsnap.R
import com.example.stepsnap.StepSnapApplication
import com.example.stepsnap.data.db.entity.DailyRecordEntity
import com.example.stepsnap.data.db.entity.PhotoEntity
import com.example.stepsnap.domain.model.DailyProgress
import com.example.stepsnap.domain.model.DailyRecap
import com.example.stepsnap.domain.model.MapLocation
import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import com.example.stepsnap.domain.usecase.StreakCalculator
import com.example.stepsnap.location.LocationTrackingManager
import com.example.stepsnap.service.StepTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

data class HomeUiState(
    val isLoading: Boolean = true,
    val steps: Long = 0,
    val targetSteps: Long = 1_000,
    val streak: Int = 0,
    val recaps: List<DailyRecap> = emptyList(),
    val pois: List<Poi> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StepSnapApplication).repository
    private val locationTrackingManager = LocationTrackingManager(application)

    val isTracking: StateFlow<Boolean> = StepTrackingService.isTracking

    private val _currentLocation = MutableStateFlow<MapLocation?>(null)
    val currentLocation: StateFlow<MapLocation?> = _currentLocation.asStateFlow()
    private val _routePoints = MutableStateFlow<List<MapLocation>>(emptyList())
    val routePoints: StateFlow<List<MapLocation>> = _routePoints.asStateFlow()
    private val _locationMessage = MutableStateFlow<String?>(null)
    val locationMessage: StateFlow<String?> = _locationMessage.asStateFlow()
    private val _activeCheckInPoiId = MutableStateFlow<Long?>(null)
    val activeCheckInPoiId: StateFlow<Long?> = _activeCheckInPoiId.asStateFlow()

    private val _appMessage = MutableStateFlow<String?>(null)
    val appMessage: StateFlow<String?> = _appMessage.asStateFlow()

    private val _sensorMessage = MutableStateFlow<String?>(null)
    val sensorMessage: StateFlow<String?> = _sensorMessage.asStateFlow()
    private val _cameraMessage = MutableStateFlow<String?>(null)
    val cameraMessage: StateFlow<String?> = _cameraMessage.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeDailyRecords(),
        repository.observePhotos(),
        repository.observePois(),
        StepTrackingService.stepCount
    ) { records, photos, pois, serviceSteps ->
        val today = LocalDate.now()
        val todayRecord = records.firstOrNull { it.date == today.toString() }
        val photosByDate = photos.groupBy { it.date }
        val dailyProgress = records.map(DailyRecordEntity::toProgress)
        val recaps = records.map { record ->
            val latestPhoto = photosByDate[record.date]?.maxByOrNull { it.timestamp }
            DailyRecap(
                date = LocalDate.parse(record.date),
                steps = record.steps,
                photoPath = latestPhoto?.filePath
            )
        }

        val currentSteps = maxOf(todayRecord?.steps ?: 0L, serviceSteps)

        HomeUiState(
            isLoading = false,
            steps = currentSteps,
            targetSteps = todayRecord?.targetSteps ?: 1_000L,
            streak = StreakCalculator.currentStreak(dailyProgress, today),
            recaps = recaps,
            pois = pois
        )
    }.catch {
        _appMessage.value = getApplication<Application>().getString(R.string.data_load_failed)
        emit(HomeUiState(isLoading = false))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            try {
                repository.seedDefaultPoisIfEmpty(defaultPois())
            } catch (_: Exception) {
                reportDataError()
            }
        }
        viewModelScope.launch {
            StepTrackingService.speedWarningMessage.collect { warning ->
                if (warning != null) {
                    _sensorMessage.value = warning
                    StepTrackingService.clearWarning()
                }
            }
        }
        viewModelScope.launch {
            StepTrackingService.currentLocation.collect { loc ->
                if (loc != null) _currentLocation.value = loc
            }
        }
        viewModelScope.launch {
            StepTrackingService.routePoints.collect { points ->
                if (points.isNotEmpty()) _routePoints.value = points
            }
        }
    }

    fun startStepTracking() {
        _sensorMessage.value = null
        StepTrackingService.start(getApplication())
    }

    fun stopStepTracking() {
        StepTrackingService.stop(getApplication())
    }

    fun startLocationTracking() {
        val started = locationTrackingManager.start(
            onLocation = ::recordLocation,
            onError = {
                _locationMessage.value = getApplication<Application>()
                    .getString(R.string.location_unavailable)
            }
        )
        if (!started) {
            _locationMessage.value = getApplication<Application>()
                .getString(R.string.location_permission_required)
        }
    }

    fun stopLocationTracking() {
        locationTrackingManager.stop()
    }

    fun refreshAndCheckIn() {
        locationTrackingManager.getCurrentLocation { location ->
            if (location == null) {
                _locationMessage.value = getApplication<Application>()
                    .getString(R.string.location_unavailable)
                return@getCurrentLocation
            }
            recordLocation(location)
            val nearestPoi = uiState.value.pois.minByOrNull { poi ->
                FloatArray(1).also { distance ->
                    Location.distanceBetween(
                        location.latitude,
                        location.longitude,
                        poi.latitude,
                        poi.longitude,
                        distance
                    )
                }[0]
            }
            val distance = nearestPoi?.let { poi ->
                FloatArray(1).also { result ->
                    Location.distanceBetween(
                        location.latitude,
                        location.longitude,
                        poi.latitude,
                        poi.longitude,
                        result
                    )
                }[0]
            }
            if (nearestPoi != null && distance != null && distance <= CHECK_IN_RADIUS_METERS) {
                _activeCheckInPoiId.value = nearestPoi.id
                _locationMessage.value = getApplication<Application>().getString(
                    R.string.checkin_success,
                    nearestPoi.name
                )
            } else {
                _activeCheckInPoiId.value = null
                _locationMessage.value = getApplication<Application>()
                    .getString(R.string.no_nearby_spot)
            }
        }
    }

    fun clearLocationMessage() {
        _locationMessage.value = null
    }

    fun saveCapturedPhoto(filePath: String) {
        viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val date = today.toString()
                val existingRecord = repository.getDailyRecord(date)
                repository.saveDailyRecord(
                    existingRecord ?: DailyRecordEntity(
                        date = date,
                        steps = uiState.value.steps,
                        targetSteps = 1_000L,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                repository.savePhoto(
                    PhotoEntity(
                        date = date,
                        filePath = filePath,
                        poiId = _activeCheckInPoiId.value,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _activeCheckInPoiId.value = null
                _cameraMessage.value = getApplication<Application>().getString(R.string.photo_saved)
            } catch (_: Exception) {
                _appMessage.value = getApplication<Application>().getString(R.string.photo_database_failed)
            }
        }
    }

    fun clearCameraMessage() {
        _cameraMessage.value = null
    }

    fun clearAppMessage() {
        _appMessage.value = null
    }

    fun showCameraCaptureFailed() {
        _cameraMessage.value = getApplication<Application>().getString(R.string.photo_capture_failed)
    }

    fun showPhotoTargetRequired() {
        val formattedTarget = NumberFormat.getIntegerInstance(Locale("id", "ID"))
            .format(uiState.value.targetSteps)
        _cameraMessage.value = getApplication<Application>().getString(
            R.string.photo_target_required,
            formattedTarget
        )
    }

    fun addPoi(poi: Poi) {
        viewModelScope.launch {
            try {
                repository.savePoi(poi.copy(id = 0))
            } catch (_: Exception) {
                reportDataError()
            }
        }
    }

    fun clearSensorMessage() {
        _sensorMessage.value = null
    }

    private fun reportDataError() {
        _appMessage.value = getApplication<Application>().getString(R.string.data_load_failed)
    }

    private fun recordLocation(location: Location) {
        _currentLocation.value = MapLocation(location.latitude, location.longitude)
        val lastPoint = _routePoints.value.lastOrNull()
        val movedEnough = if (lastPoint == null) {
            true
        } else {
            val distance = FloatArray(1)
            Location.distanceBetween(
                lastPoint.latitude,
                lastPoint.longitude,
                location.latitude,
                location.longitude,
                distance
            )
            distance[0] >= MIN_ROUTE_POINT_DISTANCE_METERS
        }
        if (movedEnough) {
            _routePoints.value = _routePoints.value + MapLocation(location.latitude, location.longitude)
        }
    }

    private companion object {
        const val CHECK_IN_RADIUS_METERS = 100f
        const val MIN_ROUTE_POINT_DISTANCE_METERS = 5f
    }

    private fun defaultPois(): List<Poi> {
        val application = getApplication<Application>()
        return listOf(
            Poi(
                id = 0,
                name = application.getString(R.string.poi_market_name),
                category = PoiCategory.SHOPPING,
                latitude = -7.9788,
                longitude = 112.6303
            ),
            Poi(
                id = 0,
                name = application.getString(R.string.poi_photo_spot_name),
                category = PoiCategory.PHOTO_SPOT,
                latitude = -7.9811,
                longitude = 112.6305
            ),
            Poi(
                id = 0,
                name = application.getString(R.string.poi_park_name),
                category = PoiCategory.PARK,
                latitude = -7.9821,
                longitude = 112.6308
            )
        )
    }
}

private fun DailyRecordEntity.toProgress() = DailyProgress(
    date = LocalDate.parse(date),
    steps = steps,
    targetSteps = targetSteps
)

package com.example.stepsnap.ui.poi

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.stepsnap.R
import com.example.stepsnap.StepSnapApplication
import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SetPointUiState(
    val name: String = "",
    val description: String = "",
    val photoPath: String? = null,
    val tempPhotoPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isLocationLoading: Boolean = false,
    val locationError: String? = null,
    val nameError: String? = null,
    val showDiscardConfirm: Boolean = false,
    val isSaved: Boolean = false
)

class SetPointViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {
    private val repository = (application as StepSnapApplication).repository
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(application)
    }

    private val initialName: String = savedStateHandle[KEY_NAME] ?: ""
    private val initialDescription: String = savedStateHandle[KEY_DESC] ?: ""
    private val initialPhotoPath: String? = savedStateHandle[KEY_PHOTO]
    private val initialLat: Double? = savedStateHandle[KEY_LAT]
    private val initialLng: Double? = savedStateHandle[KEY_LNG]

    private val _uiState = MutableStateFlow(
        SetPointUiState(
            name = initialName,
            description = initialDescription,
            photoPath = initialPhotoPath,
            tempPhotoPath = initialPhotoPath,
            latitude = initialLat,
            longitude = initialLng
        )
    )
    val uiState: StateFlow<SetPointUiState> = _uiState.asStateFlow()

    fun onNameChanged(newName: String) {
        savedStateHandle[KEY_NAME] = newName
        val trimmed = newName.trim()
        val error = when {
            trimmed.isEmpty() -> getApplication<Application>().getString(R.string.name_required_error)
            newName.length > 60 -> getApplication<Application>().getString(R.string.name_too_long_error)
            else -> null
        }
        _uiState.update { it.copy(name = newName, nameError = error) }
    }

    fun onDescriptionChanged(newDesc: String) {
        savedStateHandle[KEY_DESC] = newDesc
        _uiState.update { it.copy(description = newDesc) }
    }

    fun onPhotoCaptured(path: String) {
        savedStateHandle[KEY_PHOTO] = path
        _uiState.update { it.copy(photoPath = path, tempPhotoPath = path) }
    }

    fun onRetakePhoto() {
        val currentTemp = _uiState.value.tempPhotoPath
        if (currentTemp != null) {
            try {
                File(currentTemp).delete()
            } catch (_: Exception) {}
        }
        savedStateHandle[KEY_PHOTO] = null
        _uiState.update { it.copy(photoPath = null, tempPhotoPath = null) }
    }

    fun setLocation(lat: Double, lng: Double) {
        savedStateHandle[KEY_LAT] = lat
        savedStateHandle[KEY_LNG] = lng
        _uiState.update { it.copy(latitude = lat, longitude = lng, isLocationLoading = false) }
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(fallbackLat: Double? = null, fallbackLng: Double? = null) {
        val app = getApplication<Application>()
        val instant = getInstantLocation(app)
        if (instant != null) {
            setLocation(instant.latitude, instant.longitude)
        } else if (fallbackLat != null && fallbackLng != null) {
            setLocation(fallbackLat, fallbackLng)
        }

        _uiState.update { it.copy(isLocationLoading = true, locationError = null) }
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    setLocation(location.latitude, location.longitude)
                } else if (_uiState.value.latitude == null) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            setLocation(lastLoc.latitude, lastLoc.longitude)
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLocationLoading = false,
                                    locationError = app.getString(R.string.location_unavailable)
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLocationLoading = false) }
                }
            }.addOnFailureListener {
                _uiState.update { it.copy(isLocationLoading = false) }
            }
        } catch (_: SecurityException) {
            if (_uiState.value.latitude == null) {
                _uiState.update {
                    it.copy(
                        isLocationLoading = false,
                        locationError = app.getString(R.string.location_permission_required)
                    )
                }
            } else {
                _uiState.update { it.copy(isLocationLoading = false) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getInstantLocation(context: android.content.Context): android.location.Location? {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return null
        var bestLocation: android.location.Location? = null
        try {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
        } catch (_: SecurityException) {}
        return bestLocation
    }

    fun savePoint(onSuccess: (Poi) -> Unit) {
        val state = _uiState.value
        val nameTrimmed = state.name.trim()
        if (nameTrimmed.isEmpty() || nameTrimmed.length > 60) {
            onNameChanged(state.name)
            return
        }

        val lat = state.latitude
        val lng = state.longitude

        if (lat == null || lng == null) {
            _uiState.update {
                it.copy(
                    locationError = getApplication<Application>()
                        .getString(R.string.location_unavailable)
                )
            }
            return
        }

        val newPoi = Poi(
            id = 0,
            name = nameTrimmed,
            category = PoiCategory.PHOTO_SPOT,
            latitude = lat,
            longitude = lng,
            note = state.description.trim().ifEmpty { null },
            photoPath = state.photoPath,
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                repository.savePoi(newPoi)
                _uiState.update { it.copy(isSaved = true) }
                savedStateHandle[KEY_NAME] = null
                savedStateHandle[KEY_DESC] = null
                savedStateHandle[KEY_PHOTO] = null
                savedStateHandle[KEY_LAT] = null
                savedStateHandle[KEY_LNG] = null
                _uiState.update { SetPointUiState() }
                onSuccess(newPoi)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        locationError = getApplication<Application>()
                            .getString(R.string.data_load_failed)
                    )
                }
            }
        }
    }

    fun onRequestClose(onClose: () -> Unit) {
        val state = _uiState.value
        val hasChanges = state.name.isNotBlank() ||
            state.description.isNotBlank() ||
            state.photoPath != null

        if (hasChanges) {
            _uiState.update { it.copy(showDiscardConfirm = true) }
        } else {
            cleanTempPhoto()
            onClose()
        }
    }

    fun confirmDiscard(onClose: () -> Unit) {
        cleanTempPhoto()
        savedStateHandle[KEY_NAME] = null
        savedStateHandle[KEY_DESC] = null
        savedStateHandle[KEY_PHOTO] = null
        savedStateHandle[KEY_LAT] = null
        savedStateHandle[KEY_LNG] = null
        _uiState.update { SetPointUiState() }
        onClose()
    }

    fun cancelDiscard() {
        _uiState.update { it.copy(showDiscardConfirm = false) }
    }

    private fun cleanTempPhoto() {
        val temp = _uiState.value.tempPhotoPath
        if (temp != null && !_uiState.value.isSaved) {
            try {
                File(temp).delete()
            } catch (_: Exception) {}
        }
    }

    private companion object {
        const val KEY_NAME = "poi_name"
        const val KEY_DESC = "poi_desc"
        const val KEY_PHOTO = "poi_photo"
        const val KEY_LAT = "poi_lat"
        const val KEY_LNG = "poi_lng"
    }
}

package com.example.stepsnap.ui.poi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.stepsnap.R
import com.example.stepsnap.StepSnapApplication
import com.example.stepsnap.domain.model.Poi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PoiDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

    private val repository = (application as StepSnapApplication).repository
    val poiId: Long = savedStateHandle["poiId"] ?: 0L

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleteConfirmPhoto = MutableStateFlow<Pair<Long, String>?>(null)
    val deleteConfirmPhoto: StateFlow<Pair<Long, String>?> = _deleteConfirmPhoto.asStateFlow()

    val poi: StateFlow<Poi?> = repository.observePoiWithPhotos(poiId)
        .catch {
            _message.value = getApplication<Application>().getString(R.string.data_load_failed)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPhotoCaptured(path: String) {
        if (poiId <= 0L) return
        viewModelScope.launch {
            try {
                repository.addPhotoToPoi(poiId, path)
                _message.value = getApplication<Application>().getString(R.string.photo_added_success)
            } catch (_: Exception) {
                _message.value = getApplication<Application>().getString(R.string.photo_database_failed)
            }
        }
    }

    fun requestDeletePhoto(photoId: Long, path: String) {
        _deleteConfirmPhoto.value = Pair(photoId, path)
    }

    fun confirmDeletePhoto() {
        val target = _deleteConfirmPhoto.value ?: return
        _deleteConfirmPhoto.value = null
        viewModelScope.launch {
            try {
                repository.deletePoiPhoto(target.first, target.second)
                _message.value = getApplication<Application>().getString(R.string.photo_deleted_success)
            } catch (_: Exception) {
                _message.value = getApplication<Application>().getString(R.string.data_load_failed)
            }
        }
    }

    fun cancelDeletePhoto() {
        _deleteConfirmPhoto.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showCameraPermissionDenied() {
        _message.value = getApplication<Application>().getString(R.string.camera_permission_required_photo)
    }

    fun showCameraCaptureFailed() {
        _message.value = getApplication<Application>().getString(R.string.photo_capture_failed)
    }
}

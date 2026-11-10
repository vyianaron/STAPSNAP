package com.example.stepsnap.ui.poi

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stepsnap.R
import com.example.stepsnap.StepSnapApplication
import com.example.stepsnap.domain.model.Poi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PoiDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as StepSnapApplication).repository

    private val _poiId = MutableStateFlow<Long>(0L)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleteConfirmPhoto = MutableStateFlow<Pair<Long, String>?>(null)
    val deleteConfirmPhoto: StateFlow<Pair<Long, String>?> = _deleteConfirmPhoto.asStateFlow()

    private val _showDeletePoiConfirm = MutableStateFlow(false)
    val showDeletePoiConfirm: StateFlow<Boolean> = _showDeletePoiConfirm.asStateFlow()

    val poi: StateFlow<Poi?> = _poiId.flatMapLatest { id ->
        try {
            if (id <= 0L) flowOf(null)
            else repository.observePoiWithPhotos(id)
        } catch (e: Exception) {
            Log.e("POI_DETAIL", "Error observing POI with photos for id=$id", e)
            flowOf(null)
        }
    }.catch { e ->
        Log.e("POI_DETAIL", "Error in poi flow", e)
        _message.value = getApplication<Application>().getString(R.string.data_load_failed)
        emit(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        try {
            Log.d("POI_DETAIL", "Loading POI id=$id")
            if (_poiId.value != id) {
                _poiId.value = id
            }
        } catch (e: Exception) {
            Log.e("POI_DETAIL", "Error loading POI id=$id", e)
        }
    }

    fun onPhotoCaptured(path: String) {
        val currentId = _poiId.value
        if (currentId <= 0L) return
        viewModelScope.launch {
            try {
                repository.addPhotoToPoi(currentId, path)
                _message.value = getApplication<Application>().getString(R.string.photo_added_success)
            } catch (e: Exception) {
                Log.e("POI_DETAIL", "Error adding photo to POI id=$currentId", e)
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
            } catch (e: Exception) {
                Log.e("POI_DETAIL", "Error deleting photo id=${target.first}", e)
                _message.value = getApplication<Application>().getString(R.string.data_load_failed)
            }
        }
    }

    fun cancelDeletePhoto() {
        _deleteConfirmPhoto.value = null
    }

    fun requestDeletePoi() {
        _showDeletePoiConfirm.value = true
    }

    fun confirmDeletePoi(onSuccess: () -> Unit) {
        _showDeletePoiConfirm.value = false
        val currentId = _poiId.value
        if (currentId <= 0L) return
        viewModelScope.launch {
            try {
                repository.deletePoi(currentId)
                _message.value = getApplication<Application>().getString(R.string.poi_deleted_success)
                onSuccess()
            } catch (e: Exception) {
                Log.e("POI_DETAIL", "Error deleting POI id=$currentId", e)
                _message.value = getApplication<Application>().getString(R.string.data_load_failed)
            }
        }
    }

    fun cancelDeletePoi() {
        _showDeletePoiConfirm.value = false
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

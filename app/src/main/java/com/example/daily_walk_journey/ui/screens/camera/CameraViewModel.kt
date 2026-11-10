package com.example.daily_walk_journey.ui.screens.camera

import android.app.Application
import android.content.Context
import android.os.Environment
import java.io.File
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daily_walk_journey.data.db.AppDatabase
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import com.example.daily_walk_journey.data.preferences.StepPreferences
import com.example.daily_walk_journey.data.repository.CheckpointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraUiState(
    val targetCheckpoint: Checkpoint? = null,
    val isCapturing: Boolean = false,
    val captureSuccess: Boolean = false,
    val errorMessage: String? = null,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CheckpointRepository(
        AppDatabase.getInstance(application).checkpointDao()
    )

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    var imageCapture: ImageCapture? = null

    init {
        loadFirstPendingCheckpoint()
    }

    /**
     * Muat checkpoint pertama yang menunggu foto dari database.
     */
    fun loadFirstPendingCheckpoint() {
        viewModelScope.launch {
            val pending = repository.getFirstPendingCheckpoint()
            _uiState.value = _uiState.value.copy(targetCheckpoint = pending)
        }
    }

    /**
     * Muat checkpoint berdasarkan ID tertentu (dari intent kamera).
     */
    fun loadCheckpointById(checkpointId: Long) {
        viewModelScope.launch {
            val checkpoint = repository.getCheckpointById(checkpointId)
                ?.takeIf { it.totalSteps >= StepPreferences.DEFAULT_DAILY_TARGET_STEPS }
            _uiState.value = _uiState.value.copy(targetCheckpoint = checkpoint)
        }
    }

    /**
     * Toggle antara kamera depan dan belakang.
     */
    fun toggleLensFacing() {
        val newFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.value = _uiState.value.copy(lensFacing = newFacing)
    }

    /**
     * Ambil foto dan simpan ke MediaStore, lalu update path di database.
     */
    fun capturePhoto(context: Context, note: String, onPhotoSaved: (String) -> Unit) {
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Kamera belum siap. Coba lagi sebentar.")
            return
        }
        val checkpoint = _uiState.value.targetCheckpoint
        if (
            checkpoint == null ||
            checkpoint.totalSteps < StepPreferences.DEFAULT_DAILY_TARGET_STEPS
        ) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Checkpoint foto hanya tersedia setelah target 1.000 langkah tercapai."
            )
            return
        }
        val checkpointId = checkpoint.id

        _uiState.value = _uiState.value.copy(isCapturing = true, errorMessage = null)

        val photoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.let { File(it, "STEPSNAP").apply { mkdirs() } }
        if (photoDirectory == null) {
            _uiState.value = _uiState.value.copy(
                isCapturing = false,
                errorMessage = "Penyimpanan foto tidak tersedia."
            )
            return
        }
        val photoFile = File(photoDirectory, "checkpoint_${checkpointId}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = photoFile.absolutePath
                    viewModelScope.launch {
                        repository.updateCheckpointPhoto(checkpointId, savedUri, note.trim().takeIf { it.isNotEmpty() })
                        // Cek apakah masih ada checkpoint pending lain
                        val nextPending = repository.getFirstPendingCheckpoint()
                        _uiState.value = _uiState.value.copy(
                            isCapturing = false,
                            captureSuccess = true,
                            targetCheckpoint = nextPending
                        )
                    }
                    onPhotoSaved(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        errorMessage = "Gagal mengambil foto: ${exception.message}"
                    )
                }
            }
        )
    }

    /**
     * Tandai checkpoint ini sebagai "nanti" (tidak hapus dari antrean,
     * cukup lewati dan kembali ke layar sebelumnya).
     */
    fun skipToQueue() {
        // Checkpoint tetap di DB dengan photoPath = null (antrean)
        // Tidak perlu aksi database, cukup navigasi balik dari UI
        // Navigasi ditangani langsung oleh UI; status sukses hanya untuk hasil foto.
    }

    fun reportCameraError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun clearCameraError() {
        if (_uiState.value.errorMessage?.startsWith("Kamera gagal dibuka:") == true) {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    fun resetCaptureState() {
        _uiState.value = _uiState.value.copy(captureSuccess = false, errorMessage = null)
    }

    fun getCameraProvider(context: Context): ProcessCameraProvider {
        return ProcessCameraProvider.getInstance(context).get()
    }
}

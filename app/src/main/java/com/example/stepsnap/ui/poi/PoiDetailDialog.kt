package com.example.stepsnap.ui.poi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.stepsnap.R
import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import com.example.stepsnap.domain.model.PoiPhoto
import com.example.stepsnap.ui.components.PhotoPager
import com.example.stepsnap.ui.theme.StepSnapColors
import com.example.stepsnap.ui.theme.StepSnapTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PoiDetailDialog(
    poi: Poi?,
    onAddPhoto: (String) -> Unit,
    onDeletePhoto: (PoiPhoto) -> Unit,
    onDeletePoiRequested: () -> Unit,
    onDismissRequest: () -> Unit,
    onCameraPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePhotoTarget by remember { mutableStateOf<PoiPhoto?>(null) }

    val photos = poi?.photos ?: emptyList()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (captured && path != null && File(path).length() > 0L) {
            onAddPhoto(path)
        } else {
            path?.let { File(it).delete() }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val photoDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir
                photoDir.mkdirs()
                val photoFile = File(photoDir, "poi_${System.currentTimeMillis()}.jpg")
                val photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                pendingPhotoPath = photoFile.absolutePath
                cameraLauncher.launch(photoUri)
            } catch (_: Exception) {
                pendingPhotoPath?.let { File(it).delete() }
                pendingPhotoPath = null
            }
        } else {
            onCameraPermissionDenied()
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val photoDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir
                photoDir.mkdirs()
                val photoFile = File(photoDir, "poi_${System.currentTimeMillis()}.jpg")
                val photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                pendingPhotoPath = photoFile.absolutePath
                cameraLauncher.launch(photoUri)
            } catch (_: Exception) {
                pendingPhotoPath?.let { File(it).delete() }
                pendingPhotoPath = null
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = StepSnapColors.PointCardOuter),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StepSnapColors.PointCardInner)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Photo Gallery Area
                        PhotoPager(
                            photos = photos,
                            onDeletePhoto = { target ->
                                deletePhotoTarget = target
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Button "Tambah Foto"
                        Button(
                            onClick = { launchCamera() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .border(
                                    width = 1.dp,
                                    color = StepSnapColors.FieldBorder,
                                    shape = RoundedCornerShape(50)
                                ),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = StepSnapColors.Ink
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = stringResource(R.string.add_photo_button),
                                    tint = StepSnapColors.Ink,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.add_photo_button),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = StepSnapColors.Ink
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Info Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = StepSnapColors.FieldBorder,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                // Place Name
                                Text(
                                    text = poi?.name ?: "",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = StepSnapColors.Ink
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Description Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 110.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(StepSnapColors.DescBoxBg)
                                        .padding(14.dp)
                                ) {
                                    val noteText = poi?.note
                                    if (!noteText.isNull_or_blank()) {
                                        Text(
                                            text = noteText!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StepSnapColors.Ink
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.no_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StepSnapColors.Muted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                TextButton(
                                    onClick = onDeletePoiRequested,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        text = stringResource(R.string.delete_poi_button),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletePhotoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deletePhotoTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_photo_title)) },
            text = { Text(stringResource(R.string.confirm_delete_photo_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deletePhotoTarget = null
                    onDeletePhoto(target)
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePhotoTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

@Preview(showBackground = true)
@Composable
private fun PoiDetailDialogNoPhotosPreview() {
    StepSnapTheme {
        PoiDetailDialog(
            poi = Poi(
                id = 1,
                name = "Kajoetangan Heritage Village",
                category = PoiCategory.PHOTO_SPOT,
                latitude = -7.9811,
                longitude = 112.6305,
                note = "Kajoetangan Heritage adalah kawasan cagar budaya yang memiliki arsitektur era kolonial yang ikonik dan sangat estetik untuk fotografi.",
                photos = emptyList()
            ),
            onAddPhoto = {},
            onDeletePhoto = {},
            onDeletePoiRequested = {},
            onDismissRequest = {},
            onCameraPermissionDenied = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PoiDetailDialogOnePhotoPreview() {
    StepSnapTheme {
        PoiDetailDialog(
            poi = Poi(
                id = 1,
                name = "Kajoetangan Heritage Village",
                category = PoiCategory.PHOTO_SPOT,
                latitude = -7.9811,
                longitude = 112.6305,
                note = "Spot foto favorit di Malang dengan arsitektur klasik.",
                photos = listOf(
                    PoiPhoto(id = 1, poiId = 1, path = "/path/to/photo1.jpg")
                )
            ),
            onAddPhoto = {},
            onDeletePhoto = {},
            onDeletePoiRequested = {},
            onDismissRequest = {},
            onCameraPermissionDenied = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PoiDetailDialogThreePhotosPreview() {
    StepSnapTheme {
        PoiDetailDialog(
            poi = Poi(
                id = 1,
                name = "Kajoetangan Heritage Village",
                category = PoiCategory.PHOTO_SPOT,
                latitude = -7.9811,
                longitude = 112.6305,
                note = "Spot foto favorit di Malang dengan banyak bangunan tua bersejarah.",
                photos = listOf(
                    PoiPhoto(id = 1, poiId = 1, path = "/path/to/photo1.jpg"),
                    PoiPhoto(id = 2, poiId = 1, path = "/path/to/photo2.jpg"),
                    PoiPhoto(id = 3, poiId = 1, path = "/path/to/photo3.jpg")
                )
            ),
            onAddPhoto = {},
            onDeletePhoto = {},
            onDeletePoiRequested = {},
            onDismissRequest = {},
            onCameraPermissionDenied = {}
        )
    }
}

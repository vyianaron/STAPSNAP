package com.example.stepsnap.ui.poi

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.stepsnap.R
import com.example.stepsnap.ui.components.OutlinedText
import com.example.stepsnap.ui.theme.StepSnapColors
import com.example.stepsnap.ui.theme.StepSnapTheme
import java.io.File

@Composable
fun SetPointDialog(
    uiState: SetPointUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPhotoCaptured: (String) -> Unit,
    onRetakePhoto: () -> Unit,
    onFetchLocation: () -> Unit,
    onSavePoint: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onCancelDiscard: () -> Unit
) {
    val context = LocalContext.current
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (captured && path != null && File(path).length() > 0L) {
            onPhotoCaptured(path)
        } else {
            path?.let { File(it).delete() }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val photoDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
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
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val photoDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
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

    LaunchedEffect(Unit) {
        onFetchLocation()
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
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onDismissRequest() },
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedText(
                            text = "X",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            strokeWidth = 6f
                        )
                    }

                    OutlinedText(
                        text = stringResource(R.string.set_a_point_title),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        strokeWidth = 7f,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.size(36.dp))
                }

                // Inner Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StepSnapColors.PointCardInner)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Photo Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(StepSnapColors.PhotoPlaceholder)
                                .then(
                                    if (uiState.photoPath == null) {
                                        Modifier.clickable { launchCamera() }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.photoPath == null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_camera_flash),
                                        contentDescription = stringResource(R.string.take_a_picture),
                                        tint = StepSnapColors.PhotoCameraIcon,
                                        modifier = Modifier.size(68.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.take_a_picture),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StepSnapColors.Ink
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = File(uiState.photoPath),
                                        contentDescription = stringResource(R.string.recap_photo_description),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Button(
                                        onClick = onRetakePhoto,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StepSnapColors.Ink.copy(alpha = 0.75f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.retake_photo),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Name Place Field
                        Text(
                            text = stringResource(R.string.name_place_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = StepSnapColors.Ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = onNameChange,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.name_place_placeholder),
                                    color = StepSnapColors.Muted
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = StepSnapColors.FieldBorder,
                                unfocusedBorderColor = StepSnapColors.FieldBorder,
                                focusedTextColor = StepSnapColors.Ink,
                                unfocusedTextColor = StepSnapColors.Ink
                            ),
                            isError = uiState.nameError != null,
                            supportingText = if (uiState.nameError != null) {
                                { Text(uiState.nameError, color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description Field
                        Text(
                            text = stringResource(R.string.description_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = StepSnapColors.Ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = onDescriptionChange,
                            minLines = 4,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.description_placeholder),
                                    color = StepSnapColors.Muted
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = StepSnapColors.FieldBorder,
                                unfocusedBorderColor = StepSnapColors.FieldBorder,
                                focusedTextColor = StepSnapColors.Ink,
                                unfocusedTextColor = StepSnapColors.Ink
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp)
                        )

                        if (uiState.isLocationLoading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = StepSnapColors.PointButton
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Mengambil lokasi...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StepSnapColors.Muted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Button "Set this Point"
                        val isFormValid = uiState.name.trim().isNotEmpty() &&
                            uiState.name.trim().length <= 60 &&
                            uiState.latitude != null &&
                            uiState.longitude != null

                        Button(
                            onClick = onSavePoint,
                            enabled = isFormValid,
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(52.dp)
                                .align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StepSnapColors.PointButton,
                                disabledContainerColor = StepSnapColors.PointButton.copy(alpha = 0.45f),
                                contentColor = StepSnapColors.Ink,
                                disabledContentColor = StepSnapColors.Ink.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.set_this_point_button),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text(stringResource(R.string.confirm_discard_title)) },
            text = { Text(stringResource(R.string.confirm_discard_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDiscard) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetPointDialogEmptyPreview() {
    StepSnapTheme {
        SetPointDialog(
            uiState = SetPointUiState(latitude = -7.9811, longitude = 112.6305),
            onNameChange = {},
            onDescriptionChange = {},
            onPhotoCaptured = {},
            onRetakePhoto = {},
            onFetchLocation = {},
            onSavePoint = {},
            onDismissRequest = {},
            onConfirmDiscard = {},
            onCancelDiscard = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetPointDialogWithErrorPreview() {
    StepSnapTheme {
        SetPointDialog(
            uiState = SetPointUiState(
                name = "",
                nameError = "Nama tempat wajib diisi",
                latitude = -7.9811,
                longitude = 112.6305
            ),
            onNameChange = {},
            onDescriptionChange = {},
            onPhotoCaptured = {},
            onRetakePhoto = {},
            onFetchLocation = {},
            onSavePoint = {},
            onDismissRequest = {},
            onConfirmDiscard = {},
            onCancelDiscard = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetPointDialogWithPhotoPreview() {
    StepSnapTheme {
        SetPointDialog(
            uiState = SetPointUiState(
                name = "Kajoetangan Heritage",
                description = "Spot foto estetik di Malang",
                photoPath = "/path/to/photo.jpg",
                latitude = -7.9811,
                longitude = 112.6305
            ),
            onNameChange = {},
            onDescriptionChange = {},
            onPhotoCaptured = {},
            onRetakePhoto = {},
            onFetchLocation = {},
            onSavePoint = {},
            onDismissRequest = {},
            onConfirmDiscard = {},
            onCancelDiscard = {}
        )
    }
}

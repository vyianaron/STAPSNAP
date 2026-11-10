package com.example.stepsnap.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.example.stepsnap.ui.poi.PoiDetailDialog
import com.example.stepsnap.ui.poi.PoiDetailViewModel
import com.example.stepsnap.ui.poi.SetPointDialog
import com.example.stepsnap.ui.poi.SetPointViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.example.stepsnap.R
import com.example.stepsnap.domain.model.DailyRecap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.stepsnap.ui.theme.StepSnapColors
import com.example.stepsnap.ui.theme.StepSnapTheme
import java.time.LocalDate

private fun Context.hasActivityRecognitionPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun shouldShowLocationRationale(activity: Activity?): Boolean =
    activity?.let {
        it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
    } == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val sensorMessage by viewModel.sensorMessage.collectAsState()
    val locationMessage by viewModel.locationMessage.collectAsState()
    val cameraMessage by viewModel.cameraMessage.collectAsState()
    val appMessage by viewModel.appMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var showPermissionSettings by rememberSaveable { mutableStateOf(false) }
    var showLocationRationale by rememberSaveable { mutableStateOf(false) }
    var showLocationSettings by rememberSaveable { mutableStateOf(false) }
    var activityPermissionRequestCount by rememberSaveable { mutableStateOf(0) }
    var locationPermissionRequestCount by rememberSaveable { mutableStateOf(0) }
    var pendingCheckIn by rememberSaveable { mutableStateOf(false) }
    var showSetPointDialog by rememberSaveable { mutableStateOf(false) }
    var pendingSetPointDialog by rememberSaveable { mutableStateOf(false) }
    var selectedPoiId by rememberSaveable { mutableStateOf<Long?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (captured && path != null && File(path).length() > 0L) {
            viewModel.saveCapturedPhoto(path)
        } else {
            path?.let { File(it).delete() }
            if (captured) viewModel.showCameraCaptureFailed()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (context.hasActivityRecognitionPermission() && context.hasLocationPermission()) {
            viewModel.startStepTracking()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (context.hasLocationPermission()) {
            showLocationRationale = false
            showLocationSettings = false
            viewModel.startLocationTracking()
            if (pendingCheckIn) {
                pendingCheckIn = false
                viewModel.refreshAndCheckIn()
            }
            if (pendingSetPointDialog) {
                pendingSetPointDialog = false
                showSetPointDialog = true
            }
        } else if (locationPermissionRequestCount >= 2 && !shouldShowLocationRationale(activity)) {
            showLocationSettings = true
            if (pendingSetPointDialog) {
                pendingSetPointDialog = false
                val msg = context.resources.getString(R.string.location_permission_required)
                scope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        } else {
            showLocationRationale = true
            if (pendingSetPointDialog) {
                pendingSetPointDialog = false
                val msg = context.resources.getString(R.string.location_permission_required)
                scope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    fun requestLocationPermission() {
        if (context.hasLocationPermission()) {
            viewModel.startLocationTracking()
            if (pendingCheckIn) {
                pendingCheckIn = false
                viewModel.refreshAndCheckIn()
            }
            return
        }
        if (locationPermissionRequestCount >= 1 && !shouldShowLocationRationale(activity)) {
            showLocationSettings = true
        } else {
            locationPermissionRequestCount += 1
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun openSetPointDialog() {
        if (context.hasLocationPermission()) {
            showSetPointDialog = true
        } else {
            pendingSetPointDialog = true
            requestLocationPermission()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || context.hasActivityRecognitionPermission()) {
            showPermissionRationale = false
            showPermissionSettings = false
            viewModel.startStepTracking()
        } else if (activityPermissionRequestCount >= 2 &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) != true
        ) {
            showPermissionSettings = true
        } else {
            showPermissionRationale = true
        }
        requestLocationPermission()
    }

    fun requestActivityPermission() {
        if (context.hasActivityRecognitionPermission()) {
            viewModel.startStepTracking()
            requestLocationPermission()
            return
        }
        if (activityPermissionRequestCount >= 1 &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) != true
        ) {
            showPermissionSettings = true
        } else if (
            activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == true
        ) {
            showPermissionRationale = true
        } else {
            activityPermissionRequestCount += 1
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    fun requestStepAndCheckIn() {
        if (isTracking) {
            viewModel.stopStepTracking()
        } else {
            if (!context.hasNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (context.hasActivityRecognitionPermission()) {
                viewModel.startStepTracking()
                if (context.hasLocationPermission()) {
                    viewModel.refreshAndCheckIn()
                } else {
                    pendingCheckIn = true
                    requestLocationPermission()
                }
            } else {
                pendingCheckIn = true
                requestActivityPermission()
            }
        }
    }

    fun launchCamera() {
        if (state.steps < state.targetSteps) {
            viewModel.showPhotoTargetRequired()
            return
        }
        try {
            val photoDirectory = File(context.filesDir, "photos").apply { mkdirs() }
            val photoFile = File(photoDirectory, "stepsnap_${System.currentTimeMillis()}.jpg")
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
            viewModel.showCameraCaptureFailed()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (context.hasLocationPermission()) {
                        viewModel.startLocationTracking()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.stopLocationTracking()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (context.hasLocationPermission()) viewModel.startLocationTracking()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationTracking()
        }
    }

    LaunchedEffect(sensorMessage) {
        sensorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSensorMessage()
        }
    }
    LaunchedEffect(locationMessage) {
        locationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearLocationMessage()
        }
    }
    LaunchedEffect(cameraMessage) {
        cameraMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCameraMessage()
        }
    }
    LaunchedEffect(appMessage) {
        appMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAppMessage()
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetPeekHeight = screenHeight * 0.40f

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = StepSnapColors.SheetCream,
        sheetTonalElevation = 0.dp,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .size(50.dp, 5.dp)
                    .background(StepSnapColors.Ink, RoundedCornerShape(50))
            )
        },
        sheetContent = {
            HomeBottomSheet(
                steps = state.steps,
                target = state.targetSteps,
                streak = state.streak,
                recapEntries = state.recaps,
                isTracking = isTracking,
                onGetStep = { requestStepAndCheckIn() },
                onTakePhoto = { launchCamera() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { _ ->
        MapSection(
            modifier = Modifier.fillMaxSize(),
            pois = state.pois,
            currentLocation = currentLocation,
            routePoints = routePoints,
            fabPaddingBottom = sheetPeekHeight,
            onOpenSetPointDialog = { openSetPointDialog() },
            onPoiSelected = { poiId -> selectedPoiId = poiId }
        )
    }

    if (showSetPointDialog) {
        val setPointViewModel: SetPointViewModel = viewModel()
        val setPointUiState by setPointViewModel.uiState.collectAsState()
        val pointSavedMsg = stringResource(R.string.point_saved_success)

        SetPointDialog(
            uiState = setPointUiState,
            onNameChange = setPointViewModel::onNameChanged,
            onDescriptionChange = setPointViewModel::onDescriptionChanged,
            onPhotoCaptured = setPointViewModel::onPhotoCaptured,
            onRetakePhoto = setPointViewModel::onRetakePhoto,
            onFetchLocation = {
                val loc = currentLocation
                setPointViewModel.fetchCurrentLocation(
                    fallbackLat = loc?.latitude,
                    fallbackLng = loc?.longitude
                )
            },
            onSavePoint = {
                setPointViewModel.savePoint { _ ->
                    showSetPointDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(pointSavedMsg)
                    }
                }
            },
            onDismissRequest = {
                setPointViewModel.onRequestClose {
                    showSetPointDialog = false
                }
            },
            onConfirmDiscard = {
                setPointViewModel.confirmDiscard {
                    showSetPointDialog = false
                }
            },
            onCancelDiscard = setPointViewModel::cancelDiscard
        )
    }

    selectedPoiId?.let { poiId ->
        val detailViewModel: PoiDetailViewModel = viewModel(
            key = "poi_detail_$poiId"
        ) {
            PoiDetailViewModel(
                application = context.applicationContext as android.app.Application,
                savedStateHandle = SavedStateHandle(mapOf("poiId" to poiId))
            )
        }
        val detailPoi by detailViewModel.poi.collectAsState()
        val detailMsg by detailViewModel.message.collectAsState()

        LaunchedEffect(detailMsg) {
            detailMsg?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                detailViewModel.clearMessage()
            }
        }

        PoiDetailDialog(
            poi = detailPoi,
            onAddPhoto = detailViewModel::onPhotoCaptured,
            onDeletePhoto = { photo ->
                detailViewModel.requestDeletePhoto(photo.id, photo.path)
            },
            onDismissRequest = { selectedPoiId = null },
            onCameraPermissionDenied = detailViewModel::showCameraPermissionDenied
        )

        val deleteConfirmTarget by detailViewModel.deleteConfirmPhoto.collectAsState()
        if (deleteConfirmTarget != null) {
            AlertDialog(
                onDismissRequest = detailViewModel::cancelDeletePhoto,
                title = { Text(stringResource(R.string.confirm_delete_photo_title)) },
                text = { Text(stringResource(R.string.confirm_delete_photo_message)) },
                confirmButton = {
                    TextButton(onClick = detailViewModel::confirmDeletePhoto) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = detailViewModel::cancelDeletePhoto) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StepSnapColors.Card.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = StepSnapColors.LimeDark)
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.activity_permission_title)) },
            text = { Text(stringResource(R.string.activity_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    activityPermissionRequestCount += 1
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }) {
                    Text(stringResource(R.string.permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    if (showPermissionSettings) {
        AlertDialog(
            onDismissRequest = { showPermissionSettings = false },
            title = { Text(stringResource(R.string.activity_permission_settings_title)) },
            text = { Text(stringResource(R.string.activity_permission_settings_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionSettings = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettings = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocationRationale = false
                    locationPermissionRequestCount += 1
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text(stringResource(R.string.permission_location_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationRationale = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    if (showLocationSettings) {
        AlertDialog(
            onDismissRequest = { showLocationSettings = false },
            title = { Text(stringResource(R.string.location_permission_settings_title)) },
            text = { Text(stringResource(R.string.location_permission_settings_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocationSettings = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettings = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }
}

@Composable
private fun HomeBottomSheet(
    steps: Long,
    target: Long,
    streak: Int,
    recapEntries: List<DailyRecap>,
    isTracking: Boolean,
    onGetStep: () -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 650.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepProgress(
            steps = steps,
            target = target,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Button(
                onClick = onGetStep,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (isTracking) StepSnapColors.Ink else StepSnapColors.Lime,
                    contentColor = if (isTracking) StepSnapColors.Lime else StepSnapColors.Ink
                )
            ) {
                Text(
                    text = if (isTracking) stringResource(R.string.stop_step) else stringResource(R.string.get_a_step),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            androidx.compose.material3.IconButton(
                onClick = onTakePhoto,
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        StepSnapColors.Lime,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.open_camera),
                    tint = StepSnapColors.Ink
                )
            }
        }

        Text(
            text = stringResource(R.string.streak_title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = StepSnapColors.Ink
        )
        StreakRow(streak = streak)

        RecapList(
            entries = recapEntries,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 850)
@Composable
private fun HomeBottomSheetPreview() {
    StepSnapTheme {
        HomeBottomSheet(
            steps = 180,
            target = 1_000,
            streak = 2,
            recapEntries = DemoHomeData.recaps(LocalDate.now()),
            isTracking = false,
            onGetStep = {},
            onTakePhoto = {}
        )
    }
}

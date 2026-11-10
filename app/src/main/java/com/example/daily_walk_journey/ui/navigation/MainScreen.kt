package com.example.daily_walk_journey.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.daily_walk_journey.data.repository.AchievementCatalog
import com.example.daily_walk_journey.data.preferences.StepPreferences
import kotlinx.coroutines.launch
import com.example.daily_walk_journey.service.StepCounterService
import com.example.daily_walk_journey.ui.screens.camera.CameraScreen
import com.example.daily_walk_journey.ui.screens.home.HomeScreen
import com.example.daily_walk_journey.ui.screens.map.MapScreen
import com.example.daily_walk_journey.ui.screens.profile.AchievementViewModel
import com.example.daily_walk_journey.ui.screens.profile.ProfileScreen
import com.example.daily_walk_journey.ui.screens.timeline.TimelineScreen

@Composable
fun MainScreen(
    openCameraDirectly: Boolean = false,
    checkpointIdFromIntent: Long = -1L
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { StepPreferences(context) }
    val achievementViewModel: AchievementViewModel = viewModel()
    val profileState by achievementViewModel.profileState.collectAsState()
    val pendingUnlocks by achievementViewModel.pendingUnlocks.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Ambil data langkah & status dari Service / Preferences
    val serviceStepCount by StepCounterService.stepCount.collectAsState()
    val savedStepCount by preferences.todaySteps.collectAsState(initial = 0L)
    val isTrackingService by StepCounterService.isServiceRunning.collectAsState()
    val isTrackingPref by preferences.isTracking.collectAsState(initial = false)
    val dailyTargetSteps by preferences.dailyTargetSteps.collectAsState(initial = 1000L)
    val lastCheckpointSteps by preferences.lastCheckpointSteps.collectAsState(initial = 0L)

    val currentSteps = if (serviceStepCount > 0L) serviceStepCount else savedStepCount
    val isTracking = isTrackingService || isTrackingPref

    // Jika dibuka dari notifikasi checkpoint, arahkan langsung ke tab Kamera
    LaunchedEffect(openCameraDirectly) {
        if (openCameraDirectly) {
            navController.navigate(Screen.Camera.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    // Permission launcher untuk ACTIVITY_RECOGNITION & POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true

        if (activityGranted) {
            StepCounterService.start(context)
        } else {
            Toast.makeText(
                context,
                "Izin aktivitas fisik diperlukan untuk menghitung langkah.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestPermissionsAndStart() {
        val permissionsToRequest = buildList {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        if (permissionsToRequest.isEmpty()) {
            StepCounterService.start(context)
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    stepCount = currentSteps,
                    dailyTarget = dailyTargetSteps,
                    canAddDailyTarget = currentSteps >= dailyTargetSteps &&
                        lastCheckpointSteps >= dailyTargetSteps,
                    isTracking = isTracking,
                    onStartTracking = { requestPermissionsAndStart() },
                    onStopTracking = { StepCounterService.stop(context) },
                    onAddDailyTarget = {
                        scope.launch { preferences.addDailyTarget() }
                    }
                )
            }
            composable(Screen.Camera.route) {
                CameraScreen(
                    checkpointIdFromIntent = checkpointIdFromIntent,
                    onPhotoTaken = {
                        navController.navigate(Screen.Timeline.route) {
                            popUpTo(Screen.Camera.route) { inclusive = true }
                        }
                    },
                    onTakeLater = {
                        navController.navigate(Screen.Timeline.route)
                    }
                )
            }
            composable(Screen.Timeline.route) {
                TimelineScreen(
                    onNavigateToCamera = {
                        navController.navigate(Screen.Camera.route)
                    }
                )
            }
            composable(Screen.Map.route) {
                MapScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(profileState = profileState)
            }
        }
    }

    pendingUnlocks.firstOrNull()?.let { unlocked ->
        val definition = AchievementCatalog.find(unlocked.key)
        AlertDialog(
            onDismissRequest = { achievementViewModel.markUnlockSeen(unlocked.key) },
            title = { Text("Lencana baru terbuka!") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = definition?.emoji ?: "🏅",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        text = definition?.title ?: "Achievement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = definition?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    achievementViewModel.markUnlockSeen(unlocked.key)
                }) {
                    Text("Keren!")
                }
            }
        )
    }
}

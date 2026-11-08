package com.example.daily_walk_journey.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.daily_walk_journey.data.preferences.StepPreferences
import com.example.daily_walk_journey.service.StepCounterService
import com.example.daily_walk_journey.ui.screens.camera.CameraScreen
import com.example.daily_walk_journey.ui.screens.home.HomeScreen
import com.example.daily_walk_journey.ui.screens.map.MapScreen
import com.example.daily_walk_journey.ui.screens.profile.ProfileScreen
import com.example.daily_walk_journey.ui.screens.timeline.TimelineScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(openCameraDirectly: Boolean = false) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { StepPreferences(context) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Ambil data langkah & status dari Service / Preferences
    val serviceStepCount by StepCounterService.stepCount.collectAsState()
    val savedStepCount by preferences.todaySteps.collectAsState(initial = 0L)
    val isTrackingService by StepCounterService.isServiceRunning.collectAsState()
    val isTrackingPref by preferences.isTracking.collectAsState(initial = false)
    val stepInterval by preferences.stepInterval.collectAsState(initial = 1000)

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
                    stepInterval = stepInterval,
                    isTracking = isTracking,
                    onStartTracking = { requestPermissionsAndStart() },
                    onStopTracking = { StepCounterService.stop(context) }
                )
            }
            composable(Screen.Camera.route) {
                val checkpointNum = if (currentSteps == 0L) 1 else ((currentSteps / stepInterval) + 1).toInt()
                CameraScreen(
                    checkpointNumber = checkpointNum,
                    onTakePhoto = {},
                    onTakeLater = {
                        navController.navigate(Screen.Timeline.route)
                    }
                )
            }
            composable(Screen.Timeline.route) {
                TimelineScreen()
            }
            composable(Screen.Map.route) {
                MapScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    selectedInterval = stepInterval,
                    onIntervalChanged = { newInterval ->
                        scope.launch {
                            preferences.setStepInterval(newInterval)
                        }
                    }
                )
            }
        }
    }
}

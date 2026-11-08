package com.example.daily_walk_journey.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.daily_walk_journey.service.StepCounterService
import com.example.daily_walk_journey.ui.screens.camera.CameraScreen
import com.example.daily_walk_journey.ui.screens.home.HomeScreen
import com.example.daily_walk_journey.ui.screens.map.MapScreen
import com.example.daily_walk_journey.ui.screens.profile.ProfileScreen
import com.example.daily_walk_journey.ui.screens.timeline.TimelineScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val stepCount by StepCounterService.stepCount.collectAsState()
    val isTracking by StepCounterService.isServiceRunning.collectAsState()

    // Permission launcher for ACTIVITY_RECOGNITION & POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true

        if (activityGranted) {
            startTrackingService(context)
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
            startTrackingService(context)
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
                    stepCount = stepCount,
                    isTracking = isTracking,
                    onStartTracking = { requestPermissionsAndStart() },
                    onStopTracking = { stopTrackingService(context) }
                )
            }
            composable(Screen.Camera.route) {
                CameraScreen(
                    checkpointNumber = if (stepCount == 0) 1 else (stepCount / 1000) + 1,
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
                ProfileScreen()
            }
        }
    }
}

private fun startTrackingService(context: Context) {
    val intent = Intent(context, StepCounterService::class.java).apply {
        action = StepCounterService.ACTION_START
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopTrackingService(context: Context) {
    val intent = Intent(context, StepCounterService::class.java).apply {
        action = StepCounterService.ACTION_STOP
    }
    context.startService(intent)
}

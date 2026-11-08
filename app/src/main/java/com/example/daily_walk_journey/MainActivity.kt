package com.example.daily_walk_journey

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.daily_walk_journey.service.StepCounterService
import com.example.daily_walk_journey.ui.navigation.MainScreen
import com.example.daily_walk_journey.ui.theme.Daily_walk_journeyTheme

class MainActivity : ComponentActivity() {

    private var openCameraDirectly by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        openCameraDirectly = intent?.getBooleanExtra(StepCounterService.EXTRA_OPEN_CAMERA, false) ?: false

        setContent {
            Daily_walk_journeyTheme {
                MainScreen(openCameraDirectly = openCameraDirectly)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCameraDirectly = intent.getBooleanExtra(StepCounterService.EXTRA_OPEN_CAMERA, false)
    }
}

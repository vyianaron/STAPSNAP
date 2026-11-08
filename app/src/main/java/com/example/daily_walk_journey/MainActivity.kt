package com.example.daily_walk_journey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.daily_walk_journey.ui.navigation.MainScreen
import com.example.daily_walk_journey.ui.theme.Daily_walk_journeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Daily_walk_journeyTheme {
                MainScreen()
            }
        }
    }
}

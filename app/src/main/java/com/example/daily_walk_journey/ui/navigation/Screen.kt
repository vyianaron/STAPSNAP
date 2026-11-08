package com.example.daily_walk_journey.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Beranda", Icons.Default.DirectionsWalk)
    object Camera : Screen("camera", "Kamera", Icons.Default.PhotoCamera)
    object Timeline : Screen("timeline", "Timeline", Icons.Default.PhotoLibrary)
    object Map : Screen("map", "Peta", Icons.Default.Map)
    object Profile : Screen("profile", "Lencana", Icons.Default.EmojiEvents)

    companion object {
        val bottomNavItems = listOf(Home, Camera, Timeline, Map, Profile)
    }
}

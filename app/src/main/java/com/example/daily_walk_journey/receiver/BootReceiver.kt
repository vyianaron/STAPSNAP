package com.example.daily_walk_journey.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.daily_walk_journey.data.preferences.StepPreferences
import com.example.daily_walk_journey.service.StepCounterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scope = CoroutineScope(Dispatchers.IO)
            val preferences = StepPreferences(context)

            scope.launch {
                val shouldTrack = preferences.isTracking.first()
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

                if (shouldTrack && hasPermission) {
                    StepCounterService.start(context)
                }
            }
        }
    }
}

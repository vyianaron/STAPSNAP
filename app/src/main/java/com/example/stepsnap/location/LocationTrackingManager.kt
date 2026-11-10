package com.example.stepsnap.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class LocationTrackingManager(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start(
        onLocation: (Location) -> Unit,
        onError: (Exception) -> Unit = {}
    ): Boolean {
        if (locationCallback != null) return true
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(onLocation)
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MILLIS
        )
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .build()

        return try {
            locationCallback = callback
            client.lastLocation.addOnSuccessListener { it?.let(onLocation) }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    locationCallback = null
                    onError(error)
                }
            true
        } catch (error: SecurityException) {
            locationCallback = null
            onError(error)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onLocation: (Location?) -> Unit) {
        try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            )
                .addOnSuccessListener(onLocation)
                .addOnFailureListener { onLocation(null) }
        } catch (_: SecurityException) {
            onLocation(null)
        }
    }

    fun stop() {
        locationCallback?.let(client::removeLocationUpdates)
        locationCallback = null
    }

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 8_000L
        const val MIN_DISTANCE_METERS = 5f
    }
}

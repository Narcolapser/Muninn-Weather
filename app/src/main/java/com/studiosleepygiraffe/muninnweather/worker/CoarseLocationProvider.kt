package com.studiosleepygiraffe.muninnweather.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class CoarseLocationProvider(private val context: Context) {
    suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnown = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < MAX_LAST_KNOWN_AGE_MILLIS) {
            return lastKnown
        }

        if (!runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            return lastKnown
        }

        return withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }

                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                runCatching {
                    manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                }.onFailure {
                    manager.removeUpdates(listener)
                    if (continuation.isActive) continuation.resume(lastKnown)
                }
            }
        } ?: lastKnown
    }

    companion object {
        private const val MAX_LAST_KNOWN_AGE_MILLIS = 2 * 60 * 60 * 1000L
        private const val LOCATION_TIMEOUT_MILLIS = 10 * 1000L
    }
}

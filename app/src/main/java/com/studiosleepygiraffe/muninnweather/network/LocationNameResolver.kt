package com.studiosleepygiraffe.muninnweather.network

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationNameResolver(private val context: Context) {
    suspend fun resolve(latitude: Double, longitude: Double): String? {
        return resolveWithAndroidGeocoder(latitude, longitude)
            ?: OpenMeteoClient().reverseGeocode(latitude, longitude)
    }

    private suspend fun resolveWithAndroidGeocoder(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val address = Geocoder(context, Locale.US)
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?: return@runCatching null
                listOfNotNull(
                    address.locality?.takeIf { it.isNotBlank() }
                        ?: address.subAdminArea?.takeIf { it.isNotBlank() },
                    address.adminArea?.takeIf { it.isNotBlank() },
                    address.countryCode?.takeIf { it.isNotBlank() }
                ).joinToString(", ").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
}

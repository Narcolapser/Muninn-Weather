package com.studiosleepygiraffe.muninnweather.worker

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import com.studiosleepygiraffe.muninnweather.data.WeatherPacket
import com.studiosleepygiraffe.muninnweather.network.HaClient
import com.studiosleepygiraffe.muninnweather.network.LocationNameResolver
import com.studiosleepygiraffe.muninnweather.network.OpenMeteoClient
import org.json.JSONObject

class WeatherWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storage = WeatherStorage(applicationContext)
        val config = storage.getConfig() ?: return Result.retry()
        val entityId = storage.getEntityId() ?: return Result.retry()
        val packet = fetchBestPacket(storage, config, entityId) ?: return Result.retry()

        storage.appendPacket(packet)
        sendToGadgetbridge(packet)
        return Result.success()
    }

    private suspend fun fetchBestPacket(
        storage: WeatherStorage,
        config: WeatherStorage.HaConfig,
        entityId: String
    ): WeatherPacket? {
        val homeLocale = storage.getHomeLocale()
        if (homeLocale == null) {
            return HaClient().fetchPacket(config, entityId)
        }

        val locationProvider = CoarseLocationProvider(applicationContext)
        val currentLocation = locationProvider.getCurrentLocation()
        val stableLocation = currentLocation ?: storage.getCurrentLocale()?.toLocation()

        if (currentLocation != null && !isOutsideHomeLocale(currentLocation, homeLocale)) {
            storage.saveCurrentLocale(
                WeatherStorage.CurrentLocale(
                    name = homeLocale.name,
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    timestampMillis = currentLocation.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        }

        if (stableLocation != null && isOutsideHomeLocale(stableLocation, homeLocale)) {
            val meteoClient = OpenMeteoClient()
            val cachedLocale = storage.getCurrentLocale()
            val cachedName = cachedLocale?.name?.takeUnless { it == WeatherStorage.CURRENT_LOCATION_PLACEHOLDER }
            val locationName = if (currentLocation == null && cachedName != null) {
                cachedName
            } else {
                LocationNameResolver(applicationContext).resolve(stableLocation.latitude, stableLocation.longitude)
                    ?: cachedName
                    ?: WeatherStorage.CURRENT_LOCATION_PLACEHOLDER
            }
            if (currentLocation != null) {
                storage.saveCurrentLocale(
                    WeatherStorage.CurrentLocale(
                        name = locationName,
                        latitude = currentLocation.latitude,
                        longitude = currentLocation.longitude,
                        timestampMillis = currentLocation.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
            }
            val packet = meteoClient.fetchCurrent(stableLocation.latitude, stableLocation.longitude, locationName)
            if (locationName != WeatherStorage.CURRENT_LOCATION_PLACEHOLDER) {
                storage.replaceCurrentLocationPacketNames(locationName)
            }
            if (packet != null) return packet
        }

        return HaClient().fetchPacket(config, entityId)
    }

    private fun WeatherStorage.CurrentLocale.toLocation(): Location =
        Location("muninn_cached").also {
            it.latitude = latitude
            it.longitude = longitude
            it.time = timestampMillis
        }

    private fun isOutsideHomeLocale(
        currentLocation: Location,
        homeLocale: WeatherStorage.HomeLocale
    ): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude,
            currentLocation.longitude,
            homeLocale.latitude,
            homeLocale.longitude,
            results
        )
        return results[0] > HOME_RADIUS_METERS
    }

    private fun sendToGadgetbridge(packet: WeatherPacket) {
        val tempKelvin = toKelvin(packet.temperature, packet.unit)
        val payload = JSONObject()
        payload.put("timestamp", (packet.timestampMillis / 1000).toInt())
        payload.put("location", packet.locationName)
        payload.put("currentTemp", tempKelvin)
        payload.put("todayMinTemp", tempKelvin)
        payload.put("todayMaxTemp", tempKelvin)
        payload.put("currentCondition", packet.condition)
        payload.put("currentConditionCode", packet.conditionCode)
        payload.put("currentHumidity", 50)
        val payloadString = payload.toString()

        Log.i(TAG, "Sending weather broadcast: $payloadString")

        val intent = Intent(ACTION_GADGETBRIDGE_WEATHER).apply {
            setPackage(GADGETBRIDGE_PACKAGE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(EXTRA_WEATHER_JSON, payloadString)
        }
        applicationContext.sendBroadcast(intent)

        val fallbackIntent = Intent(ACTION_GADGETBRIDGE_WEATHER).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(EXTRA_WEATHER_JSON, payloadString)
        }
        applicationContext.sendBroadcast(fallbackIntent)
    }

    private fun toKelvin(value: Double, unit: String): Int {
        val normalized = unit.trim().lowercase()
        val celsius = when {
            normalized.contains("f") -> (value - 32.0) * 5.0 / 9.0
            normalized.contains("c") -> value
            else -> value
        }
        return kotlin.math.round(celsius + 273.15).toInt()
    }

    companion object {
        private const val TAG = "MuninnWeather"
        private const val ACTION_GADGETBRIDGE_WEATHER = "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        private const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
        private const val EXTRA_WEATHER_JSON = "WeatherJson"
        private const val HOME_RADIUS_METERS = 25_000f
    }
}

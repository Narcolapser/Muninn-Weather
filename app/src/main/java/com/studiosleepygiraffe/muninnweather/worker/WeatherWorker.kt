package com.studiosleepygiraffe.muninnweather.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import com.studiosleepygiraffe.muninnweather.network.HaClient
import org.json.JSONObject

class WeatherWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storage = WeatherStorage(applicationContext)
        val config = storage.getConfig() ?: return Result.retry()
        val entityId = storage.getEntityId() ?: return Result.retry()
        val packet = HaClient().fetchPacket(config, entityId) ?: return Result.retry()

        storage.appendPacket(packet)
        sendToGadgetbridge(packet)
        return Result.success()
    }

    private fun sendToGadgetbridge(packet: com.studiosleepygiraffe.muninnweather.data.WeatherPacket) {
        val tempKelvin = toKelvin(packet.temperature, packet.unit)
        val payload = JSONObject()
        payload.put("timestamp", (packet.timestampMillis / 1000).toInt())
        payload.put("location", "Home Assistant")
        payload.put("currentTemp", tempKelvin)
        payload.put("todayMinTemp", tempKelvin)
        payload.put("todayMaxTemp", tempKelvin)
        payload.put("currentCondition", "Unknown")
        payload.put("currentConditionCode", 800)
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

    private fun toKelvin(value: Double, unit: String): Double {
        val normalized = unit.trim().lowercase()
        val celsius = when {
            normalized.contains("f") -> (value - 32.0) * 5.0 / 9.0
            normalized.contains("c") -> value
            else -> value
        }
        val kelvin = celsius + 273.15
        return kotlin.math.round(kelvin * 10.0) / 10.0
    }

    companion object {
        private const val TAG = "MuninnWeather"
        private const val ACTION_GADGETBRIDGE_WEATHER = "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        private const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
        private const val EXTRA_WEATHER_JSON = "WeatherJson"
    }
}

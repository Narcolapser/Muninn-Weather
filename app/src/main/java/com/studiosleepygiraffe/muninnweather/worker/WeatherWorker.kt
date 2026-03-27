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
        val packet = HaClient().fetchPacket(config, ENTITY_ID) ?: return Result.retry()

        storage.appendPacket(packet)
        sendToGadgetbridge(packet)
        return Result.success()
    }

    private fun sendToGadgetbridge(packet: com.studiosleepygiraffe.muninnweather.data.WeatherPacket) {
        val payload = JSONObject()
        payload.put("timestamp", packet.timestampMillis / 1000)
        payload.put("temperature", packet.temperature)
        payload.put("temperatureUnit", packet.unit)
        val payloadString = payload.toString()

        Log.i(TAG, "Sending weather broadcast: $payloadString")

        val intent = Intent(ACTION_GADGETBRIDGE_WEATHER).apply {
            setPackage(GADGETBRIDGE_PACKAGE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(EXTRA_WEATHER_JSON, payloadString)
            putExtra(EXTRA_WEATHER_JSON_LEGACY, payloadString)
        }
        applicationContext.sendBroadcast(intent)

        val fallbackIntent = Intent(ACTION_GADGETBRIDGE_WEATHER).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(EXTRA_WEATHER_JSON, payloadString)
            putExtra(EXTRA_WEATHER_JSON_LEGACY, payloadString)
        }
        applicationContext.sendBroadcast(fallbackIntent)
    }

    companion object {
        private const val TAG = "MuninnWeather"
        private const val ENTITY_ID = "sensor.roof_top_weather_station_temperature"
        private const val ACTION_GADGETBRIDGE_WEATHER = "nodomain.freeyourgadget.gadgetbridge.action.WEATHER"
        private const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
        private const val EXTRA_WEATHER_JSON = "WEATHER_JSON"
        private const val EXTRA_WEATHER_JSON_LEGACY = "weather_json"
    }
}

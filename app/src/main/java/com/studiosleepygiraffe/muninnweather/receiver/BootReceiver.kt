package com.studiosleepygiraffe.muninnweather.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import com.studiosleepygiraffe.muninnweather.worker.WorkerScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val storage = WeatherStorage(context)
                if (storage.hasFullConfig()) {
                    WorkerScheduler.schedulePeriodic(context, storage.getPollingIntervalMinutes())
                }
            }
        }
    }
}

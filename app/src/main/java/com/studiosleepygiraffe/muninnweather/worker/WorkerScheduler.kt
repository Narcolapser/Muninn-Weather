package com.studiosleepygiraffe.muninnweather.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val PERIODIC_NAME = "muninn_weather_periodic"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun enqueueOneTime(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "muninn_weather_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

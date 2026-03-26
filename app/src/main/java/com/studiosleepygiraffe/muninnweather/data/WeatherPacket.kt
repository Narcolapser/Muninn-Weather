package com.studiosleepygiraffe.muninnweather.data

data class WeatherPacket(
    val timestampMillis: Long,
    val temperature: Double,
    val unit: String
)

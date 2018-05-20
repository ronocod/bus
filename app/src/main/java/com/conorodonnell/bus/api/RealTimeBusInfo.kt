package com.conorodonnell.bus.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RealTimeBusInfo(
    val arrivaldatetime: String,
    val duetime: String,
    val departuredatetime: String,
    val departureduetime: String,
    val scheduledarrivaldatetime: String,
    val scheduleddeparturedatetime: String,
    val destination: String,
    val destinationlocalized: String,
    val origin: String,
    val originlocalized: String,
    val direction: String,
    val operator: String,
    val additionalinformation: String,
    val lowfloorstatus: String,
    val route: String,
    val sourcetimestamp: String,
    val monitored: String
)

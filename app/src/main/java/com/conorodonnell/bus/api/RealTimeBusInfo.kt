package com.conorodonnell.bus.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RealTimeBusInfo(
    val duetime: String,
    val destination: String,
    val origin: String,
    val operator: String,
    val route: String
)

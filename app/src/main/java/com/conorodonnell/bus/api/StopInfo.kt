package com.conorodonnell.bus.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StopInfo(
    val stopid: String,
    val fullname: String,
    val latitude: String,
    val longitude: String
)

package com.conorodonnell.bus.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StopResult(
    val results: List<StopInfo>
)

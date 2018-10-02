package com.conorodonnell.bus.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RealTimeResult(
  val results: List<RealTimeBusInfo>
)

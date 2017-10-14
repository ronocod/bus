package com.conorodonnell.bus

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface BusService {
    @GET("realtimebusinformation")
    fun fetchRealTimeInfo(@Query("stopid") stopId: String): Call<RealTimeResult>

    @GET("busstopinformation")
    fun fetchStop(@Query("stopid") stopId: String): Call<StopResult>

    @GET("busstopinformation")
    fun fetchAllStops(): Call<StopResult>
}

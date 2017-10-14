package com.conorodonnell.bus

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface BusService {
    @GET("realtimebusinformation")
    fun fetchRealTimeInfo(@Query("stopid") stopId: String): Observable<RealTimeResult>

    @GET("busstopinformation")
    fun fetchStop(@Query("stopid") stopId: String): Observable<StopResult>

    @GET("busstopinformation")
    fun fetchAllStops(): Observable<StopResult>
}

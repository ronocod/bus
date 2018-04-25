package com.conorodonnell.bus.api

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface BusApiClient {
  @GET("realtimebusinformation")
  fun fetchRealTimeInfo(@Query("stopid") stopId: String): Observable<RealTimeResult>

  @GET("busstopinformation?operator=bac")
  fun fetchAllBusStops(): Observable<StopResult>
}

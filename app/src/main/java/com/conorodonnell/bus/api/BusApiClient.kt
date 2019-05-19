package com.conorodonnell.bus.api

import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

interface BusApiClient {
  @GET("realtimebusinformation")
  fun fetchRealTimeInfo(@Query("stopid") stopId: String): Single<RealTimeResult>

  @GET("busstopinformation?operator=bac")
  fun fetchAllBusStops(): Single<StopResult>

  @GET("busstopinformation?operator=luas")
  fun fetchAllLuasStops(): Single<StopResult>

  @GET("busstopinformation?operator=ir")
  fun fetchAllRailStops(): Single<StopResult>
}

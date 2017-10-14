package com.conorodonnell.bus

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


object Core {

    fun service(): BusService {

        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        return Retrofit.Builder()
                .baseUrl("https://data.dublinked.ie/cgi-bin/rtpi/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(BusService::class.java)
    }

    data class Result(val results: List<BusInfo>)
    data class BusInfo(
            val destination: String,
            val scheduledarrivaldatetime: String,
            val route: String
    )

    interface BusService {
        @GET("realtimebusinformation?format=json")
        fun fetchStopInfo(@Query("stopid") stopId: String): Call<Result>
    }
}

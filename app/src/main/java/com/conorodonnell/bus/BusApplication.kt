package com.conorodonnell.bus

import android.app.Application
import android.arch.persistence.room.Room
import com.conorodonnell.bus.api.BusApiClient
import com.conorodonnell.bus.persistence.AppDatabase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

class BusApplication : Application() {

    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "bus")
                .build()
    }
    val apiClient: BusApiClient by lazy {
        val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                }).build()
        Retrofit.Builder()
                .baseUrl("https://data.dublinked.ie/cgi-bin/rtpi/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(BusApiClient::class.java)
    }

}

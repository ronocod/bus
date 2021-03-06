package com.conorodonnell.bus

import android.app.Application
import android.os.AsyncTask
import android.os.Looper
import androidx.room.Room
import com.conorodonnell.bus.api.BusApiClient
import com.conorodonnell.bus.persistence.AppDatabase
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

class BusApplication : Application() {

  val database by lazy {
    Room.databaseBuilder(this, AppDatabase::class.java, "bus")
      .fallbackToDestructiveMigration()
      .build()
  }
  val apiClient: BusApiClient by lazy {
    val client = OkHttpClient.Builder()
      .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS))
      .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
      }).build()
    Retrofit.Builder()
      .baseUrl("https://rtpiapp.rtpi.openskydata.com/RTPIPublicService_V2/service.svc/")
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create())
      .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
      .validateEagerly(true)
      .build()
      .create<BusApiClient>()
  }

  override fun onCreate() {
    RxAndroidPlugins.setMainThreadSchedulerHandler {
      AndroidSchedulers.from(Looper.getMainLooper(), true)
    }
    RxJavaPlugins.setErrorHandler(Throwable::printStackTrace)

    super.onCreate()
    initialiseComponentsInBackground()
  }

  private fun initialiseComponentsInBackground() {
    AsyncTask.execute {
      // Accessing these properties triggers their lazy initialisation.
      // Doing this in a background thread on app launch means less blocking of the main thread when they're
      // accessed for the first time there.
      apiClient
      database
    }
  }
}

package com.conorodonnell.bus

import android.arch.persistence.room.Room
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.conorodonnell.bus.api.Core
import com.conorodonnell.bus.api.RealTimeBusInfo
import com.conorodonnell.bus.persistence.AppDatabase
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_stop.*

class StopActivity : AppCompatActivity() {

    companion object {
        val EXTRA_STOP_ID = "EXTRA_STOP_ID"

        fun createIntent(context: Context, stopId: String): Intent {
            return Intent(context, StopActivity::class.java)
                    .putExtra(EXTRA_STOP_ID, stopId)
        }
    }

    private val apiClient = Core.apiClient()
    private val disposable = CompositeDisposable()

    private var database: AppDatabase = Room.databaseBuilder(this, AppDatabase::class.java, "bus").build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stop)

        loadStopData()

        swipeRefreshLayout.setOnRefreshListener(this::loadStopData)
    }

    private fun getStopIdFromIntent() = intent.getStringExtra(EXTRA_STOP_ID)

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun loadStopData() {
        val stopId = getStopIdFromIntent()
        database.stops().findById(stopId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .safely {
                    subscribe({
                        swipeRefreshLayout.isRefreshing = false
                        title = "${stopId} ${it.name}"
                        updateBusData(stopId)
                    }, {
                        swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(this@StopActivity, "Stop $stopId doesn't exist", Toast.LENGTH_SHORT).show()
                        it.printStackTrace()
                    })
                }
    }

    private fun updateBusData(stopId: String) {
        busInfoText.text = "Loading..."
        apiClient.fetchRealTimeInfo(stopId)
                .map { it.results.joinToString("\n") { it.formatBusInfo() } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .safely {
                    subscribe({
                        busInfoText.text = it
                    }, {
                        it.printStackTrace()
                    })
                }
    }

    private fun RealTimeBusInfo.formatBusInfo() = "$route to $destination | ${formatDueTime()}"

    private fun RealTimeBusInfo.formatDueTime() =
            when (duetime) {
                "Due" -> duetime
                else -> "$duetime mins"
            }

    private inline fun <T> Observable<T>.safely(subscription: Observable<T>.() -> Disposable) =
            disposable.add(subscription())

    private inline fun <T> Single<T>.safely(subscription: Single<T>.() -> Disposable) =
            disposable.add(subscription())

}

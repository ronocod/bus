package com.conorodonnell.bus

import android.arch.persistence.room.Room
import android.content.Context
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.conorodonnell.bus.persistence.AppDatabase
import com.conorodonnell.bus.persistence.Stop
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Float.parseFloat


class MainActivity : AppCompatActivity() {

    private val busService = Core.service()
    private val disposable = CompositeDisposable()

    private val navigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                message.setText(R.string.title_home)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                message.setText(R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                message.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    private var database: AppDatabase = Room.databaseBuilder(this, AppDatabase::class.java, "bus").build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database.stops().count()
                .filter { it == 0 }
                .subscribeOn(Schedulers.io())
                .subscribe {
                    busService.fetchAllBusStops()
                            .subscribeOn(Schedulers.io())
                            .forEach { database.stops().insertAll(it.results.map { it.toEntity() }) }
                }

        navigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener)
        stopField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loadStop()
                true
            } else false
        }

        fetchButton.setOnClickListener {
            loadStop()
        }
    }

    fun hideKeyboard() {
        val v = window.currentFocus
        if (v != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    private fun loadStop() {
        hideKeyboard()
        val stopId = stopField.text.toString()
        fetchButton.isEnabled = false
        busService.fetchRealTimeInfo(stopId)
                .map { it.results.joinToString("\n") { it.formatBusInfo() } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    message.text = it
                    fetchButton.isEnabled = true
                }, Throwable::printStackTrace)

        database.stops().findById(stopId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    title = "$stopId ${it.name}"
                }, Throwable::printStackTrace)
    }


    private fun RealTimeBusInfo.formatBusInfo() = "$route to $destination | ${formatDueTime()}"

    private fun StopInfo.toEntity(): Stop = Stop(stopid, fullname, parseFloat(latitude), parseFloat(longitude))

    private fun RealTimeBusInfo.formatDueTime() =
            when (duetime) {
                "Due" -> duetime
                else -> "$duetime mins"
            }

}

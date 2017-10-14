package com.conorodonnell.bus

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.arch.persistence.room.Room
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker.PERMISSION_GRANTED
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.conorodonnell.bus.api.Core
import com.conorodonnell.bus.api.RealTimeBusInfo
import com.conorodonnell.bus.api.StopInfo
import com.conorodonnell.bus.persistence.AppDatabase
import com.conorodonnell.bus.persistence.Stop
import com.google.android.gms.location.LocationServices
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Float.parseFloat


class MainActivity : AppCompatActivity() {

    private val busService = Core.service()
    private val disposable = CompositeDisposable()

    private val navigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                busInfoText.setText(R.string.title_home)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                busInfoText.setText(R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                busInfoText.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    private var database: AppDatabase = Room.databaseBuilder(this, AppDatabase::class.java, "bus")
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database.stops().count()
                .filter { it == 0 }
                .subscribeOn(Schedulers.io())
                .safely {
                    subscribe {
                        busService.fetchAllBusStops()
                                .subscribeOn(Schedulers.io())
                                .forEach { database.stops().insertAll(it.results.map { it.toEntity() }) }
                    }
                }

        navigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener)
        stopField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loadStop(stopField.text.toString())
                true
            } else false
        }

        fetchButton.setOnClickListener {
            loadStop(stopField.text.toString())
        }
        locationButton.setOnClickListener {
            if (mapView.visibility == View.GONE) {
                busInfoText.visibility = View.GONE
                mapView.visibility = View.VISIBLE
            } else {
                busInfoText.visibility = View.VISIBLE
                mapView.visibility = View.GONE
            }
        }
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty()) {
            return
        }
        if (grantResults.first() == PERMISSION_GRANTED) {
            loadNearest()
        }
    }

    private fun loadNearest() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(ACCESS_FINE_LOCATION), 17)
            }
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        return@addOnSuccessListener
                    }
                    database.stops()
                            .findNearest(location.latitude, location.longitude)
                            .filter { it.isNotEmpty() }
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ list ->
                                loadStop(list.first().id)
                            }, Throwable::printStackTrace)
                }
    }

    fun hideKeyboard() {
        val v = window.currentFocus
        if (v != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun loadStop(stopId: String) {
        fetchButton.isEnabled = false
        database.stops().findById(stopId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .safely {
                    subscribe({
                        title = "$stopId ${it.name}"
                        updateBusData(stopId)
                        hideKeyboard()
                    }, {
                        Toast.makeText(this@MainActivity, "Stop $stopId doesn't exist", LENGTH_SHORT).show()
                        it.printStackTrace()
                        fetchButton.isEnabled = true
                    })
                }
    }

    private fun updateBusData(stopId: String) {
        busInfoText.text = "Loading..."
        busService.fetchRealTimeInfo(stopId)
                .map { it.results.joinToString("\n") { it.formatBusInfo() } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .safely {
                    subscribe({
                        busInfoText.text = it
                        fetchButton.isEnabled = true
                    }, {
                        fetchButton.isEnabled = true
                        it.printStackTrace()
                    })
                }
    }


    private fun RealTimeBusInfo.formatBusInfo() = "$route to $destination | ${formatDueTime()}"

    private fun StopInfo.toEntity(): Stop = Stop(stopid, fullname, parseFloat(latitude), parseFloat(longitude))

    private fun <T> Observable<T>.safely(subscription: Observable<T>.() -> Disposable) =
            disposable.add(subscription())

    private fun <T> Single<T>.safely(subscription: Single<T>.() -> Disposable) =
            disposable.add(subscription())

    private fun <T> Maybe<T>.safely(subscription: Maybe<T>.() -> Disposable) =
            disposable.add(subscription())

    private fun RealTimeBusInfo.formatDueTime() =
            when (duetime) {
                "Due" -> duetime
                else -> "$duetime mins"
            }

}

package com.conorodonnell.bus

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.arch.persistence.room.Room
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker.PERMISSION_GRANTED
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.widget.SearchView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.conorodonnell.bus.api.Core
import com.conorodonnell.bus.api.StopInfo
import com.conorodonnell.bus.persistence.AppDatabase
import com.conorodonnell.bus.persistence.Stop
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
import java.lang.Double.parseDouble

private const val PREFS = "prefs"
private const val REQUESTED_LOCATION_PERMISSION = "requested_location_permission"


class MapActivity : AppCompatActivity() {
    private val busService = Core.service()
    private val disposable = CompositeDisposable()
    private var database: AppDatabase = Room.databaseBuilder(this, AppDatabase::class.java, "bus").build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        database.stops().count()
                .filter { it == 0 }
                .flatMapObservable { busService.fetchAllBusStops() }
                .subscribeOn(Schedulers.io())
                .safely {
                    subscribe {
                        database.stops().insertAll(it.results.map { it.toEntity() })
                    }
                }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this::setupMap)

        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !hasRequestedPermission()) {
            requestPermissions(arrayOf(ACCESS_FINE_LOCATION), 17)
            preferences()
                    .edit()
                    .putBoolean(REQUESTED_LOCATION_PERMISSION, true)
                    .apply()
        }
    }

    private fun preferences() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hasRequestedPermission() = preferences().getBoolean(REQUESTED_LOCATION_PERMISSION, false)

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (menu == null) {
            return super.onCreateOptionsMenu(menu)
        }
        menuInflater.inflate(R.menu.main, menu)

        val searchItem = menu.findItem(R.id.menu_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { loadStop(it) }
                searchItem.collapseActionView()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean = true
        })
        searchView.inputType = InputType.TYPE_CLASS_NUMBER

        return super.onCreateOptionsMenu(menu)
    }

    private fun setupMap(map: GoogleMap) {

        map.setOnInfoWindowClickListener { item ->
            loadStop(item.title)
        }

        val idleListener = {
            addMarkersForStops(loadStopsIn(map.projection.visibleRegion.latLngBounds), map)
        }
        map.setOnCameraIdleListener(idleListener)
        map.setOnMarkerClickListener {
            map.setOnCameraIdleListener {
                map.setOnCameraIdleListener(idleListener)
            }
            return@setOnMarkerClickListener false
        }

        loadDefaultLocation(map)
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            return
        }
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true

        LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        loadDefaultLocation(map)
                        return@addOnSuccessListener
                    }
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                            loadMarkers(map))
                }

    }

    private fun loadDefaultLocation(map: GoogleMap) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(53.36, -6.245), 12f))
        loadMarkers(map)
    }

    private fun loadMarkers(map: GoogleMap): GoogleMap.CancelableCallback {
        return object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                addMarkersForStops(loadStopsIn(map.projection.visibleRegion.latLngBounds), map)
            }

            override fun onCancel() {
                addMarkersForStops(loadStopsIn(map.projection.visibleRegion.latLngBounds), map)
            }
        }
    }

    private fun addMarkersForStops(stops: Single<MutableList<Stop>>, map: GoogleMap) {
        stops.subscribe({ list: MutableList<Stop> ->
            if (list.size > 300) {
                mapView.post {
                    map.clear()
                    shortToast("Too many stops, zoom in")
                }
            } else {
                val markers = list.map { stop ->
                    val latLng = LatLng(stop.latitude, stop.longitude)
                    MarkerOptions()
                            .position(latLng)
                            .title(stop.id)
                            .snippet(stop.name)
                }
                mapView.post {
                    map.clear()
                    markers.forEach { map.addMarker(it) }
                }
            }
        }, Throwable::printStackTrace)
    }

    private fun Activity.shortToast(message: String) {
        Toast.makeText(this, message, LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty()) {
            return
        }
        if (grantResults.first() == PERMISSION_GRANTED) {
            recreate()
        }
    }

    private fun loadStopsIn(area: LatLngBounds): Single<MutableList<Stop>> {
        val ne = area.northeast
        val sw = area.southwest
        return database.stops()
                .findInArea(ne.latitude, sw.latitude, sw.longitude, ne.longitude)
                .subscribeOn(Schedulers.io())
    }

    private fun loadStop(stopId: String) {
        database.stops().findById(stopId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .safely {
                    subscribe({
                        startActivity(StopActivity.createIntent(this@MapActivity, stopId))
                    }, {
                        Toast.makeText(this@MapActivity, "Stop $stopId doesn't exist", LENGTH_SHORT).show()
                        it.printStackTrace()
                    })
                }
    }

    private fun StopInfo.toEntity(): Stop = Stop(stopid, fullname, parseDouble(latitude), parseDouble(longitude))

    private inline fun <T> Observable<T>.safely(subscription: Observable<T>.() -> Disposable) =
            disposable.add(subscription())

    private inline fun <T> Single<T>.safely(subscription: Single<T>.() -> Disposable) =
            disposable.add(subscription())

    private inline fun <T> Maybe<T>.safely(subscription: Maybe<T>.() -> Disposable) =
            disposable.add(subscription())

}

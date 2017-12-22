package com.conorodonnell.bus

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.Snackbar
import android.support.design.widget.Snackbar.LENGTH_INDEFINITE
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker.PERMISSION_GRANTED
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.view.View
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.conorodonnell.bus.api.StopInfo
import com.conorodonnell.bus.persistence.Stop
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
import java.lang.Double.parseDouble

private const val PREFS = "prefs"
private const val REQUESTED_LOCATION_PERMISSION = "requested_location_permission"


class MapActivity : AppCompatActivity() {
    private val disposable = CompositeDisposable()
    private val apiClient by lazy { (application as BusApplication).apiClient }
    private val database by lazy { (application as BusApplication).database }

    private val sheetBehavior: BottomSheetBehavior<LinearLayout> by lazy {
        BottomSheetBehavior.from(bottomSheet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        database.stops().count()
                .filter { it == 0 }
                .flatMapObservable { apiClient.fetchAllBusStops() }
                .subscribeOn(Schedulers.io())
                .disposingIn(disposable) {
                    subscribe {
                        database.stops().insertAll(it.results.map { it.toEntity() })
                    }
                }

        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !hasRequestedPermission()) {
            requestPermissions(arrayOf(ACCESS_FINE_LOCATION), 17)
            preferences()
                    .edit()
                    .putBoolean(REQUESTED_LOCATION_PERMISSION, true)
                    .apply()
        }

        mapView.onCreate(savedInstanceState)
        lifecycle.addObserver(MapViewLifecycleObserver(mapView))
        mapView.getMapAsync(this::setupMap)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
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

        val idleListener = {
            addMarkersForStops(loadStopsIn(map.projection.visibleRegion.latLngBounds), map)
        }
        map.setOnCameraIdleListener(idleListener)
        map.setOnMarkerClickListener {
            map.setOnCameraIdleListener {
                map.setOnCameraIdleListener(idleListener)
            }
            loadStop(it.title)
            return@setOnMarkerClickListener false
        }

        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        sheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    map.setPadding(0, 0, 0, 0)
                } else {
                    map.setPadding(0, 0, 0, (128 * resources.displayMetrics.density).toInt())
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })

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
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(53.36, -6.245), 17f))
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


    private var snackbar: Snackbar? = null

    private fun addMarkersForStops(stops: Single<MutableList<Stop>>, map: GoogleMap) {
        stops.subscribe({ list: MutableList<Stop> ->
            if (list.size > 160) {
                runOnUiThread {
                    if (snackbar == null) {
                        snackbar = Snackbar.make(mapContainer, "Too many stops, zoom in", LENGTH_INDEFINITE)

                    }
                    snackbar?.show()
                }
            } else {
                snackbar?.dismiss()
                val markers = list.map { stop ->
                    MarkerOptions()
                            .position(LatLng(stop.latitude, stop.longitude))
                            .title(stop.id)
                }
                runOnUiThread {
                    map.clear()
                    markers.forEach { map.addMarker(it) }
                }
            }
        }, Throwable::printStackTrace)
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
                .disposingIn(disposable) {
                    subscribe({ stop ->
                        sheetTitle.text = "${stop.id} - ${stop.name}"
                        updateBusData(stopId)
                        mapView.getMapAsync { map ->
                            map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(stop.latitude, stop.longitude)))
                        }
                        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }, {
                        Toast.makeText(this@MapActivity, "Stop $stopId doesn't exist", LENGTH_SHORT).show()
                        it.printStackTrace()
                    })
                }
    }

    private fun updateBusData(stopId: String) {
        sheetContent.text = "Loading..."
        apiClient.fetchRealTimeInfo(stopId)
                .map { it.results.joinToString("\n") { it.formatBusInfo() } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .disposingIn(disposable) {
                    subscribe({
                        sheetContent.text = it
                    }, {
                        it.printStackTrace()
                    })
                }
    }

    private fun StopInfo.toEntity(): Stop = Stop(stopid, fullname, parseDouble(latitude), parseDouble(longitude))

}

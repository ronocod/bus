package com.conorodonnell.bus

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.Snackbar
import android.support.design.widget.Snackbar.LENGTH_INDEFINITE
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker.PERMISSION_GRANTED
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.conorodonnell.bus.api.StopInfo
import com.conorodonnell.bus.persistence.Stop
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
import java.lang.Double.parseDouble
import java.util.*

private const val PREFS = "prefs"
private const val REQUESTED_LOCATION_PERMISSION = "requested_location_permission"

private const val MAX_VISIBLE_STOPS = 160

class MapActivity : AppCompatActivity() {
    private val disposable = CompositeDisposable()
    private val apiClient by lazy { (application as BusApplication).apiClient }
    private val database by lazy { (application as BusApplication).database }
    private val defaultMarkerIcon by lazy { BitmapDescriptorFactory.defaultMarker() }
    private val clickedMarkerIcon by lazy { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) }

    private var lastClickedMarker: Marker? = null

    private val markerStops = HashMap<Marker, String>(MAX_VISIBLE_STOPS)

    private val sheetBehavior: BottomSheetBehavior<ConstraintLayout> by lazy {
        BottomSheetBehavior.from(bottomSheet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mapContainer.postDelayed({
            database.stops().count()
                    .filter { it == 0 }
                    .doOnSuccess { showIndefiniteSnackbar("Downloading stops...") }
                    .flatMapObservable { apiClient.fetchAllBusStops() }
                    .subscribeOn(Schedulers.io())
                    .disposingIn(disposable) {
                        subscribe {
                            database.stops().insertAll(it.results.map { it.toEntity() })
                            runOnUiThread { snackbar?.dismiss() }
                        }
                    }
        }, 500)

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

    private fun showIndefiniteSnackbar(message: String) {
        runOnUiThread {
            snackbar = Snackbar.make(mapContainer, message, LENGTH_INDEFINITE)
            snackbar?.show()
        }
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
        map.setOnMapClickListener {
            lastClickedMarker?.setIcon(defaultMarkerIcon)
            lastClickedMarker = null
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        map.setOnMarkerClickListener { marker ->
            map.setOnCameraIdleListener {
                map.setOnCameraIdleListener(idleListener)
            }
            
            lastClickedMarker?.setIcon(defaultMarkerIcon)
            lastClickedMarker = marker
            val stopId = markerStops[marker]
            loadStop(stopId!!)
            marker.setIcon(clickedMarkerIcon)
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

        loadVisibleMarkers(map)
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            return
        }
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true

        LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        loadVisibleMarkers(map)
                        return@addOnSuccessListener
                    }
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f), loadMarkers(map))
                }
    }

    private fun loadMarkers(map: GoogleMap): GoogleMap.CancelableCallback {
        return object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                loadVisibleMarkers(map)
            }

            override fun onCancel() {
                loadVisibleMarkers(map)
            }
        }
    }

    private fun loadVisibleMarkers(map: GoogleMap) {
        addMarkersForStops(loadStopsIn(map.projection.visibleRegion.latLngBounds), map)
    }

    private var snackbar: Snackbar? = null

    private fun addMarkersForStops(stops: Flowable<MutableList<Stop>>, map: GoogleMap) {
        stops.subscribe({ list: MutableList<Stop> ->
            if (list.isEmpty()) {
                return@subscribe
            }
            if (list.size > MAX_VISIBLE_STOPS) {
                showIndefiniteSnackbar("Too many stops, zoom in")
                return@subscribe
            }
            runOnUiThread {
                markerStops.clear()
                snackbar?.dismiss()
                map.clear()
                lastClickedMarker = null
                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                list.forEach { stop ->
                    val marker = map.addMarker(MarkerOptions()
                            .position(LatLng(stop.latitude, stop.longitude)))
                    markerStops[marker] = stop.id
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

    private fun loadStopsIn(area: LatLngBounds): Flowable<MutableList<Stop>> {
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
        refreshButton.setOnClickListener {
            updateBusData(stopId)
        }
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

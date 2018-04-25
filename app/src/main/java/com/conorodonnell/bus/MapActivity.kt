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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_map.*
import net.sharewire.googlemapsclustering.Cluster
import net.sharewire.googlemapsclustering.ClusterItem
import net.sharewire.googlemapsclustering.ClusterManager
import net.sharewire.googlemapsclustering.DefaultIconGenerator
import java.lang.Double.parseDouble


private const val PREFS = "prefs"
private const val REQUESTED_LOCATION_PERMISSION = "requested_location_permission"

class MapActivity : AppCompatActivity() {
  private val disposable = CompositeDisposable()
  private val apiClient by lazy { (application as BusApplication).apiClient }
  private val database by lazy { (application as BusApplication).database }
  private val defaultMarkerIcon by lazy { BitmapDescriptorFactory.defaultMarker() }
  private val clickedMarkerIcon by lazy { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) }

  private var lastClickedMarker: Marker? = null

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
          .subscribe {
            database.stops().insertAll(it.results.map { it.toEntity() })
            runOnUiThread { snackbar?.dismiss() }
          }.addTo(disposable)
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

  class BusClusterItem(val stop: Stop) : ClusterItem {
    override fun getSnippet(): String? = null

    override fun getLongitude() = stop.longitude

    override fun getLatitude() = stop.latitude

    override fun getTitle(): String? = null
  }

  private fun setupMap(map: GoogleMap) {

    val clusterManager = ClusterManager<BusClusterItem>(this, map)
    map.setOnCameraIdleListener(clusterManager)
    clusterManager.setMinClusterSize(5)

    clusterManager.setIconGenerator(object : DefaultIconGenerator<BusClusterItem>(this) {
      override fun getClusterItemIcon(clusterItem: BusClusterItem): BitmapDescriptor {
        return defaultMarkerIcon
      }
    })
    clusterManager.setCallbacks(object : ClusterManager.Callbacks<BusClusterItem> {
      override fun onClusterItemClick(clusterItem: BusClusterItem): Boolean {
        val stop = clusterItem.stop
        lastClickedMarker?.remove()
        lastClickedMarker = map.addMarker(MarkerOptions()
            .icon(clickedMarkerIcon)
            .zIndex(999f)
            .position(LatLng(stop.latitude, stop.longitude)))
        loadStop(stop.id)
        return false
      }

      override fun onClusterClick(cluster: Cluster<BusClusterItem>): Boolean {
        return false
      }
    })

    database.stops()
        .findAll()
        .map { it.map(::BusClusterItem) }
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribe(clusterManager::setItems)

    map.setOnMapClickListener {
      lastClickedMarker?.remove()
      lastClickedMarker = null
      sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
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

    if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
      return
    }
    map.isMyLocationEnabled = true
    map.uiSettings.isMyLocationButtonEnabled = true

    LocationServices.getFusedLocationProviderClient(this)
        .lastLocation
        .addOnSuccessListener { location ->
          if (location == null) {
            return@addOnSuccessListener
          }
          val latLng = LatLng(location.latitude, location.longitude)
          map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
  }

  private var snackbar: Snackbar? = null

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    if (grantResults.isEmpty()) {
      return
    }
    if (grantResults.first() == PERMISSION_GRANTED) {
      recreate()
    }
  }

  private fun loadStop(stopId: String) {
    database.stops().findById(stopId)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({ stop ->
          sheetTitle.text = "${stop.id} - ${stop.name}"
          updateBusData(stopId)
          mapView.getMapAsync { map ->
            map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(stop.latitude, stop.longitude)))
          }
          sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }, {
          Toast.makeText(this@MapActivity, "Stop $stopId doesn't exist", LENGTH_SHORT).show()
          it.printStackTrace()
        }).addTo(disposable)
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
        .subscribe({
          sheetContent.text = it
        }, Throwable::printStackTrace)
        .addTo(disposable)
  }

  private fun StopInfo.toEntity(): Stop = Stop(stopid, fullname, parseDouble(latitude), parseDouble(longitude))

}

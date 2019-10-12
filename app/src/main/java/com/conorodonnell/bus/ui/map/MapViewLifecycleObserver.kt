package com.conorodonnell.bus.ui.map

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.gms.maps.MapView

class MapViewLifecycleObserver(private val mapView: MapView) : LifecycleObserver {

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  fun onStart() {
    mapView.onStart()
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
  fun onResume() {
    mapView.onResume()
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  fun onPause() {
    mapView.onPause()
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  fun onStop() {
    mapView.onStop()
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  fun onDestroy() {
    mapView.onDestroy()
  }
}

package com.iltan.avcbike.ride

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.iltan.avcbike.speed.RideState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RideStatus(
  val active: Boolean = false,
  val state: RideState = RideState.CRUISING,
  /** Smoothed speed, or null while there is no recent GPS fix. */
  val speedKmh: Float? = null,
  /** Music volume as a fraction of the maximum. */
  val volumeLevel: Float = 0f,
)

/** Shared live state of the ride, published by [RideService] and observed by the UI. */
object RideSession {
  private val _status = MutableStateFlow(RideStatus())
  val status: StateFlow<RideStatus> = _status.asStateFlow()

  internal fun update(transform: (RideStatus) -> RideStatus) = _status.update(transform)

  /** Debug builds only: when set, the ride uses this speed instead of GPS. */
  val simulatedSpeedKmh = MutableStateFlow<Float?>(null)

  fun start(context: Context) {
    // The service can't run without it, and would have to start in the foreground just to stop.
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    try {
      ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
    } catch (e: IllegalStateException) {
      // ForegroundServiceStartNotAllowedException: the app went to the background first.
      Log.w("AVC", "Couldn't start the ride", e)
    }
  }

  fun stop(context: Context) {
    context.startService(Intent(context, RideService::class.java).setAction(RideService.ACTION_STOP))
  }
}

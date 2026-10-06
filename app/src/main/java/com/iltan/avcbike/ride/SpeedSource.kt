package com.iltan.avcbike.ride

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.iltan.avcbike.speed.SpeedSample

/** Where a ride's speed readings come from. Readings arrive on the main thread. */
interface SpeedSource {
  /** Starts readings. [onFailed] is called if this source turns out not to work on the phone. */
  fun start(onSample: (SpeedSample) -> Unit, onFailed: () -> Unit)

  fun stop()

  companion object {
    /** Google's fused location where Play services exist, otherwise the phone's own GPS. */
    fun best(context: Context): SpeedSource = if (hasPlayServices(context)) FusedSpeedSource(context) else GpsSpeedSource(context)
  }
}

/**
 * False on phones without Google Play services: Huawei phones released since 2019 (US sanctions),
 * Honor phones from the Huawei era up to early 2021, and phones running Google-free ROMs.
 */
fun hasPlayServices(context: Context): Boolean =
  GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

/** 1 Hz: fast enough to notice pulling away from a light within a couple of seconds. */
private const val INTERVAL_MS = 1_000L

private fun Location.toSpeedSample(): SpeedSample? {
  if (!hasSpeed()) return null
  return SpeedSample(
    timeMs = elapsedRealtimeNanos / 1_000_000,
    speedKmh = speed * 3.6f,
    accuracyM = if (hasAccuracy()) accuracy else null,
    speedAccuracyKmh = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond * 3.6f else null,
  )
}

class FusedSpeedSource(context: Context) : SpeedSource {
  private val client = LocationServices.getFusedLocationProviderClient(context)
  private var onSample: (SpeedSample) -> Unit = {}
  private val callback =
    object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.locations.forEach { location -> location.toSpeedSample()?.let(onSample) }
      }
    }

  @SuppressLint("MissingPermission") // RideService checks it before starting.
  override fun start(onSample: (SpeedSample) -> Unit, onFailed: () -> Unit) {
    this.onSample = onSample
    client.requestLocationUpdates(locationRequest(), callback, Looper.getMainLooper()).addOnFailureListener { error ->
      Log.w(TAG, "Fused location failed", error)
      onFailed()
    }
  }

  override fun stop() {
    client.removeLocationUpdates(callback)
  }

  companion object {
    fun locationRequest(): LocationRequest =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS).setMinUpdateIntervalMillis(INTERVAL_MS / 2).build()
  }
}

/** The phone's GPS chip directly, for phones without Play services or where fused location fails. */
class GpsSpeedSource(context: Context) : SpeedSource {
  private val manager = context.getSystemService(LocationManager::class.java)
  private var onSample: (SpeedSample) -> Unit = {}
  // The compat listener fills in the callbacks that Android 10 and older require.
  private val listener = LocationListenerCompat { location -> location.toSpeedSample()?.let(onSample) }

  @SuppressLint("MissingPermission") // RideService checks it before starting.
  override fun start(onSample: (SpeedSample) -> Unit, onFailed: () -> Unit) {
    this.onSample = onSample
    if (LocationManager.GPS_PROVIDER !in manager.allProviders) {
      Log.w(TAG, "This phone has no GPS")
      onFailed()
      return
    }
    val request = LocationRequestCompat.Builder(INTERVAL_MS).setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
    LocationManagerCompat.requestLocationUpdates(manager, LocationManager.GPS_PROVIDER, request, listener, Looper.getMainLooper())
  }

  @SuppressLint("MissingPermission") // Removing updates needs no permission; lint can't tell.
  override fun stop() {
    LocationManagerCompat.removeUpdates(manager, listener)
  }
}

private const val TAG = "AVC"

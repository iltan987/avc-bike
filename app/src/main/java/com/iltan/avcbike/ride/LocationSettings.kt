package com.iltan.avcbike.ride

import android.content.Context
import android.location.LocationManager
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest

/**
 * Checks that the phone's location services can deliver the GPS a ride needs. If they're switched
 * off, [onNeedsResolution] gets the system's "turn on location" dialog to show. Phones without
 * Play services have no such dialog: there [onLocationOff] should send the rider to the system's
 * location settings.
 */
fun checkLocationSettings(
  context: Context,
  onReady: () -> Unit,
  onNeedsResolution: (IntentSenderRequest) -> Unit,
  onLocationOff: () -> Unit,
) {
  if (!hasPlayServices(context)) {
    val gpsOn = context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
    if (gpsOn) onReady() else onLocationOff()
    return
  }
  val request = LocationSettingsRequest.Builder().addLocationRequest(FusedSpeedSource.locationRequest()).build()
  LocationServices.getSettingsClient(context)
    .checkLocationSettings(request)
    .addOnSuccessListener { onReady() }
    .addOnFailureListener { error ->
      if (error is ResolvableApiException) onNeedsResolution(IntentSenderRequest.Builder(error.resolution).build())
      // Not fixable from here (e.g. no GPS hardware): start anyway; the ride shows "Finding GPS".
      else onReady()
    }
}

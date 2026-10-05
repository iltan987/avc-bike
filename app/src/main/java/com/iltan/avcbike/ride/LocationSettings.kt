package com.iltan.avcbike.ride

import android.content.Context
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest

/**
 * Checks that the phone's location services can deliver the GPS a ride needs. If they're switched
 * off, [onNeedsResolution] gets the system's "turn on location" dialog to show.
 */
fun checkLocationSettings(context: Context, onReady: () -> Unit, onNeedsResolution: (IntentSenderRequest) -> Unit) {
  val request = LocationSettingsRequest.Builder().addLocationRequest(RideService.locationRequest()).build()
  LocationServices.getSettingsClient(context)
    .checkLocationSettings(request)
    .addOnSuccessListener { onReady() }
    .addOnFailureListener { error ->
      if (error is ResolvableApiException) onNeedsResolution(IntentSenderRequest.Builder(error.resolution).build())
      // Not fixable from here (e.g. no GPS hardware): start anyway; the ride shows "Finding GPS".
      else onReady()
    }
}

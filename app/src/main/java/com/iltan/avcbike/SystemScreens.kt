package com.iltan.avcbike

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/** The app's page in system settings, where permissions and battery use can be changed. */
fun Context.appDetailsIntent(): Intent =
  Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun Context.openAppSettings() {
  startFirstAvailable(appDetailsIntent())
}

/**
 * Opens the first of [intents] the phone has. Some phone makers remove system settings screens,
 * and starting a missing one would crash. Returns false if none of them exist.
 */
fun Context.startFirstAvailable(vararg intents: Intent): Boolean {
  for (intent in intents) {
    try {
      startActivity(intent)
      return true
    } catch (e: ActivityNotFoundException) {
      Log.w("AVC", "No screen for ${intent.action}", e)
    }
  }
  return false
}

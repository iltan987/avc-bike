package com.iltan.avcbike.ride

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import com.iltan.avcbike.R
import com.iltan.avcbike.appDetailsIntent
import com.iltan.avcbike.startFirstAvailable

/**
 * Some phones (Samsung, Xiaomi and others) stop background apps to save battery, which can end a
 * ride while the screen is off. Exempting the app removes that risk; it still only uses battery
 * while a ride is running.
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

/**
 * Where the rider can lift battery limits, best first. Google Play doesn't allow the one-tap
 * system dialog (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) for an app like this one, so the rider sets
 * it by hand: Android 12+ has Battery → Unrestricted on the app's own page; older versions have
 * the full battery optimization list.
 */
fun Context.batteryExemptionIntents(): Array<Intent> =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(appDetailsIntent())
  } else {
    arrayOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetailsIntent())
  }

/** What to tap in the screen [batteryExemptionIntents] opens. */
@StringRes
fun batteryExemptionSteps(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.battery_steps_unrestricted else R.string.battery_steps_dont_optimize

fun Context.openBatteryExemption() {
  startFirstAvailable(*batteryExemptionIntents())
}

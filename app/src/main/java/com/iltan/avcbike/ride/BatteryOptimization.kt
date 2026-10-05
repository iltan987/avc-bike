package com.iltan.avcbike.ride

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Some phones (Samsung, Xiaomi and others) stop background apps to save battery, which can end a
 * ride while the screen is off. Exempting the app removes that risk; it still only uses battery
 * while a ride is running.
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

/**
 * The system's one-tap "let this app run in the background" dialog. Needs
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which fits sideloading; if the app goes to Google Play this
 * may need to become ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS (the full list) instead.
 */
@SuppressLint("BatteryLife")
fun Context.batteryExemptionIntent(): Intent =
  Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())

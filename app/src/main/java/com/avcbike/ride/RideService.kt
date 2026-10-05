package com.avcbike.ride

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.avcbike.MainActivity
import com.avcbike.R
import com.avcbike.audio.SystemMusicVolume
import com.avcbike.audio.VolumeController
import com.avcbike.settings.RideSettings
import com.avcbike.settings.SettingsRepository
import com.avcbike.settings.displaySpeed
import com.avcbike.settings.speedUnitLabel
import com.avcbike.speed.RideState
import com.avcbike.speed.SpeedSample
import com.avcbike.speed.SpeedStateMachine
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that runs for the length of a ride: reads GPS speed, decides when the rider
 * has slowed down, and turns the music volume down and back up.
 */
class RideService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
  private lateinit var volume: VolumeController

  private var settings = RideSettings()
  private val machine = SpeedStateMachine(settings.toSpeedConfig())
  private var appliedState = RideState.CRUISING
  private var lastFixMs = 0L
  private var running = false
  private var lastNotificationText: String? = null

  private val locationCallback =
    object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.locations.forEach(::onLocation)
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!running) startRide()
    // If the system kills the ride, don't restart it without the rider asking.
    return START_NOT_STICKY
  }

  private fun startRide() {
    if (!hasLocationPermission()) {
      Log.w(TAG, "Location permission missing, not starting ride")
      stopSelf()
      return
    }
    createNotificationChannel()
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    running = true

    volume = VolumeController(SystemMusicVolume(getSystemService(AudioManager::class.java)), scope)
    machine.reset()
    appliedState = RideState.CRUISING
    RideSession.update { RideStatus(active = true, volumeLevel = volume.level.value) }

    scope.launch {
      SettingsRepository(this@RideService).settings.collect {
        settings = it
        machine.config = it.toSpeedConfig()
        publish()
      }
    }
    scope.launch { volume.level.collect { level -> RideSession.update { it.copy(volumeLevel = level) } } }
    scope.launch { watchForLostFix() }
    requestLocationUpdates()
    Log.i(TAG, "Ride started")
  }

  @Suppress("MissingPermission") // Checked in startRide().
  private fun requestLocationUpdates() {
    val request =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
        .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
        .build()
    fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
  }

  private fun onLocation(location: Location) {
    if (!location.hasSpeed()) return
    val sample =
      SpeedSample(
        timeMs = location.elapsedRealtimeNanos / 1_000_000,
        speedKmh = location.speed * 3.6f,
        accuracyM = if (location.hasAccuracy()) location.accuracy else null,
      )
    onSample(sample)
  }

  private fun onSample(sample: SpeedSample) {
    val snapshot = machine.onSample(sample)
    lastFixMs = SystemClock.elapsedRealtime()
    applyState(snapshot.state)
    volume.refresh()
    RideSession.update { it.copy(state = snapshot.state, speedKmh = snapshot.smoothedKmh) }
    publish()
  }

  private fun applyState(state: RideState) {
    if (state == appliedState) return
    appliedState = state
    Log.d(TAG, "State -> $state")
    when (state) {
      RideState.QUIET -> volume.quiet(settings.quietVolumePercent, settings.fadeMs)
      RideState.CRUISING -> volume.restore(settings.fadeMs)
    }
  }

  /** Clears the speed readout when GPS goes quiet, so the screen doesn't show a stale number. */
  private suspend fun watchForLostFix() {
    while (scope.isActive) {
      delay(1_000)
      if (SystemClock.elapsedRealtime() - lastFixMs > LOST_FIX_MS && RideSession.status.value.speedKmh != null) {
        RideSession.update { it.copy(speedKmh = null) }
        publish()
      }
    }
  }

  private fun publish() {
    if (!running) return
    val text = notificationText()
    if (text == lastNotificationText) return
    lastNotificationText = text
    if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
      @Suppress("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS.
      NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }
  }

  private fun notificationText(): String {
    val status = RideSession.status.value
    val speed =
      status.speedKmh?.let { getString(R.string.speed_with_unit, displaySpeed(it, settings.useMph), getString(speedUnitLabel(settings.useMph))) }
        ?: getString(R.string.waiting_for_gps)
    val state = getString(if (status.state == RideState.QUIET) R.string.state_quiet else R.string.state_cruising)
    return getString(R.string.notification_ride_title, speed, state)
  }

  private fun buildNotification(): Notification {
    val openApp =
      PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val stop =
      PendingIntent.getService(this, 1, Intent(this, RideService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
    val quiet = RideSession.status.value.state == RideState.QUIET
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_speed)
      .setContentTitle(lastNotificationText ?: getString(R.string.notification_ride_starting))
      .setContentText(getString(if (quiet) R.string.notification_quiet_text else R.string.notification_cruising_text))
      .setContentIntent(openApp)
      .addAction(R.drawable.ic_stop, getString(R.string.stop_ride), stop)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  private fun createNotificationChannel() {
    val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_ride), NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

  override fun onDestroy() {
    if (running) {
      fusedLocation.removeLocationUpdates(locationCallback)
      // No fade: the scope is cancelled right after, so put the volume back in one step.
      volume.restore(fadeMs = 0)
      running = false
      Log.i(TAG, "Ride stopped")
    }
    RideSession.update { RideStatus() }
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_STOP = "com.avcbike.action.STOP_RIDE"
    private const val TAG = "AVC"
    private const val CHANNEL_ID = "ride"
    private const val NOTIFICATION_ID = 1
    private const val LOCATION_INTERVAL_MS = 1_000L
    private const val LOST_FIX_MS = 5_000L
  }
}

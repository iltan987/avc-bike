package com.iltan.avcbike.ride

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import com.iltan.avcbike.BuildConfig
import com.iltan.avcbike.MainActivity
import com.iltan.avcbike.R
import com.iltan.avcbike.audio.PrefsQuietMemory
import com.iltan.avcbike.audio.SystemMusicVolume
import com.iltan.avcbike.audio.VolumeController
import com.iltan.avcbike.settings.AppLanguage
import com.iltan.avcbike.settings.RideSettings
import com.iltan.avcbike.settings.SettingsRepository
import com.iltan.avcbike.settings.displaySpeed
import com.iltan.avcbike.settings.speedUnitLabel
import com.iltan.avcbike.speed.RideState
import com.iltan.avcbike.speed.SpeedSample
import com.iltan.avcbike.speed.SpeedStateMachine
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
  private var running = false
  private var lastNotificationText: String? = null
  private var lastNotifiedState: RideState? = null
  private var lastNotifiedMs = 0L
  private var lastMovingMs = 0L
  private var lostFixJob: Job? = null

  private val locationCallback =
    object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.locations.forEach(::onLocation)
      }
    }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(AppLanguage.wrap(base))
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
    // startForeground() comes first: a service started with startForegroundService() that stops
    // without calling it crashes the app, even when stopping is the right thing to do.
    createNotificationChannel()
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
    try {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    } catch (e: RuntimeException) {
      // Android 12+ refuses from the background (ForegroundServiceStartNotAllowedException), and
      // 14+ without location permission (SecurityException).
      Log.w(TAG, "Couldn't start the ride in the foreground", e)
      stopSelf()
      return
    }
    if (!hasLocationPermission()) {
      Log.w(TAG, "Location permission missing, not starting ride")
      stopSelf()
      return
    }
    running = true

    volume = VolumeController(SystemMusicVolume(getSystemService(AudioManager::class.java)), scope, PrefsQuietMemory(this))
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
    lastMovingMs = SystemClock.elapsedRealtime()
    scope.launch { watchForIdleRide() }
    if (BuildConfig.DEBUG) scope.launch { runSimulator() }
    requestLocationUpdates()
    Log.i(TAG, "Ride started")
  }

  @Suppress("MissingPermission") // Checked in startRide().
  private fun requestLocationUpdates() {
    fusedLocation.requestLocationUpdates(locationRequest(), locationCallback, Looper.getMainLooper())
  }

  private fun onLocation(location: Location) {
    if (!location.hasSpeed() || RideSession.simulatedSpeedKmh.value != null) return
    val sample =
      SpeedSample(
        timeMs = location.elapsedRealtimeNanos / 1_000_000,
        speedKmh = location.speed * 3.6f,
        accuracyM = if (location.hasAccuracy()) location.accuracy else null,
        speedAccuracyKmh = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond * 3.6f else null,
      )
    onSample(sample)
  }

  private fun onSample(sample: SpeedSample) {
    val snapshot = machine.onSample(sample)
    val now = SystemClock.elapsedRealtime()
    if ((snapshot.smoothedKmh ?: 0f) >= MOVING_KMH) lastMovingMs = now
    scheduleLostFixCheck()
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

  /** Feeds the simulator's speed in place of GPS, twice a second, while it is switched on. */
  private suspend fun runSimulator() {
    RideSession.simulatedSpeedKmh.collectLatest { speed ->
      while (speed != null) {
        onSample(SpeedSample(timeMs = SystemClock.elapsedRealtime(), speedKmh = speed, accuracyM = 5f))
        delay(500)
      }
    }
  }

  /**
   * Clears the speed readout if no fix arrives for a while, so the screen doesn't show a stale
   * number. One delayed check, re-armed by each fix, instead of a timer ticking all ride long.
   */
  private fun scheduleLostFixCheck() {
    lostFixJob?.cancel()
    lostFixJob =
      scope.launch {
        delay(LOST_FIX_MS)
        RideSession.update { it.copy(speedKmh = null) }
        publish(force = true)
      }
  }

  /** Ends a forgotten ride so GPS doesn't run all day: no movement for [AUTO_STOP_MS]. */
  private suspend fun watchForIdleRide() {
    while (scope.isActive) {
      delay(IDLE_CHECK_MS)
      if (SystemClock.elapsedRealtime() - lastMovingMs >= AUTO_STOP_MS) {
        Log.i(TAG, "No movement for ${AUTO_STOP_MS / 60_000} min, stopping ride")
        notifyAutoStopped()
        stopSelf()
        return
      }
    }
  }

  private fun notifyAutoStopped() {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
    val openApp =
      PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.notification_auto_stopped_title))
        .setContentText(autoStopText())
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    @Suppress("MissingPermission") // Checked with areNotificationsEnabled() above.
    NotificationManagerCompat.from(this).notify(AUTO_STOP_NOTIFICATION_ID, notification)
  }

  private fun autoStopText(): String {
    val minutes = (AUTO_STOP_MS / 60_000).toInt()
    return resources.getQuantityString(R.plurals.notification_auto_stopped_text, minutes, minutes)
  }

  /**
   * Updates the ride notification. State changes show right away; speed-only changes at most every
   * [NOTIFICATION_MIN_INTERVAL_MS], since redrawing a notification every second costs battery.
   */
  private fun publish(force: Boolean = false) {
    if (!running) return
    val text = notificationText()
    if (text == lastNotificationText) return
    val state = RideSession.status.value.state
    val now = SystemClock.elapsedRealtime()
    if (!force && state == lastNotifiedState && now - lastNotifiedMs < NOTIFICATION_MIN_INTERVAL_MS) return
    lastNotificationText = text
    lastNotifiedState = state
    lastNotifiedMs = now
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
      .setSmallIcon(R.drawable.ic_notification)
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
      // No fade: the service is going away, so put the volume back in one step.
      volume.restore(fadeMs = 0)
      running = false
      Log.i(TAG, "Ride stopped")
    }
    RideSession.update { RideStatus() }
    RideSession.simulatedSpeedKmh.value = null
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_STOP = "com.iltan.avcbike.action.STOP_RIDE"

    /** 1 Hz GPS: fast enough to notice pulling away from a light within a couple of seconds. */
    fun locationRequest(): LocationRequest =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS).setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2).build()

    private const val TAG = "AVC"
    private const val CHANNEL_ID = "ride"
    private const val NOTIFICATION_ID = 1
    private const val LOCATION_INTERVAL_MS = 1_000L
    private const val LOST_FIX_MS = 5_000L
    private const val NOTIFICATION_MIN_INTERVAL_MS = 5_000L
    private const val AUTO_STOP_NOTIFICATION_ID = 2
    private const val MOVING_KMH = 8f
    private const val IDLE_CHECK_MS = 60_000L
    private const val AUTO_STOP_MS = 30 * 60_000L
  }
}

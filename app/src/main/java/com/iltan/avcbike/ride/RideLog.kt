package com.iltan.avcbike.ride

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import com.iltan.avcbike.BuildConfig
import com.iltan.avcbike.settings.RideSettings
import com.iltan.avcbike.speed.RideState
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Debug builds only: writes each ride to a CSV file, to check how GPS behaves on real rides. One
 * row per location reading (with its position, so a ride can be put on a map), plus rows for time
 * to first fix, state changes, volume steps, lost fixes and stopping. Release builds never create
 * one. To copy a ride to the computer:
 *
 * ```
 * adb shell run-as com.iltan.avcbike ls files/ride-logs
 * adb exec-out run-as com.iltan.avcbike cat files/ride-logs/<name>.csv > <name>.csv
 * ```
 */
class RideLog(context: Context) {
  private val startMs = SystemClock.elapsedRealtime()
  private val locationManager = context.getSystemService(LocationManager::class.java)
  private val mainExecutor = ContextCompat.getMainExecutor(context)
  // Writes happen in order on one background thread, so the ride never waits for the disk.
  private val io = Executors.newSingleThreadExecutor()
  private lateinit var writer: Writer
  private var gnss = GnssSummary()

  private val gnssCallback =
    object : GnssStatusCompat.Callback() {
      override fun onFirstFix(ttffMillis: Int) = row("first-fix", note = "$ttffMillis ms")

      override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
        gnss = GnssSummary.of(status)
      }
    }

  init {
    val dir = File(context.filesDir, "ride-logs")
    val name = "ride-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".csv"
    io.execute {
      dir.mkdirs()
      writer = File(dir, name).bufferedWriter()
      writer.write(HEADER + "\n")
    }
    row(
      "start",
      note =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · app ${BuildConfig.VERSION_NAME} · " +
          "GNSS ${LocationManagerCompat.getGnssHardwareModelName(locationManager) ?: "unknown"}",
    )
  }

  @SuppressLint("MissingPermission") // RideService checks location permission before a ride starts.
  fun watchSatellites() {
    LocationManagerCompat.registerGnssStatusCallback(locationManager, mainExecutor, gnssCallback)
  }

  fun settings(s: RideSettings) =
    row(
      "settings",
      note =
        "quiet below ${s.quietBelowKmh} · back above ${s.resumeAboveKmh} km/h · wait ${s.quietDelaySec}/${s.resumeDelaySec} s · " +
          "fade ${s.fadeMs} ms · quiet volume ${s.quietVolumePercent}%",
    )

  /** [used] is null for a reading without a speed, false if the state machine ignored it. */
  fun reading(location: Location, used: Boolean?, smoothedKmh: Float?, state: RideState, volume: Float) {
    val fixMs = location.elapsedRealtimeNanos / 1_000_000
    row(
      "reading",
      timeMs = fixMs,
      fields =
        listOf(
          location.provider.orEmpty(),
          num(location.latitude, 6),
          num(location.longitude, 6),
          if (location.hasAccuracy()) num(location.accuracy, 1) else "",
          if (location.hasSpeed()) num(location.speed * 3.6f, 1) else "",
          if (location.hasSpeedAccuracy()) num(location.speedAccuracyMetersPerSecond * 3.6f, 1) else "",
          (SystemClock.elapsedRealtime() - fixMs).toString(),
          used?.toString().orEmpty(),
          smoothedKmh?.let { num(it, 1) }.orEmpty(),
          state.name,
          percent(volume),
          gnss.used.toString(),
          gnss.seen.toString(),
          gnss.lowBandUsed.toString(),
          gnss.meanCn0Used?.let { num(it, 1) }.orEmpty(),
        ),
    )
  }

  fun state(state: RideState, smoothedKmh: Float?) = row("state", note = "${state.name} at ${smoothedKmh?.let { num(it, 1) } ?: "?"} km/h")

  fun volume(level: Float) = row("volume", note = "${percent(level)}%")

  fun event(name: String, note: String = "") = row(name, note = note)

  fun close() {
    LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssCallback)
    row("end")
    io.execute { writer.close() }
    io.shutdown()
  }

  /** Every row has all columns, so the file opens cleanly in a spreadsheet or pandas. */
  private fun row(event: String, timeMs: Long = SystemClock.elapsedRealtime(), fields: List<String> = List(READING_COLUMNS) { "" }, note: String = "") {
    // Notes never contain commas or quotes, so no CSV escaping is needed.
    val line = (listOf(num((timeMs - startMs) / 1000f, 3), event) + fields + note.replace(',', ';')).joinToString(",")
    io.execute {
      writer.write(line + "\n")
      // Flushed every row, so a ride killed by the phone still leaves a complete log.
      writer.flush()
    }
  }

  /** Satellites from the latest GNSS status; fused readings don't carry these themselves. */
  private data class GnssSummary(val used: Int = 0, val seen: Int = 0, val lowBandUsed: Int = 0, val meanCn0Used: Float? = null) {
    companion object {
      fun of(status: GnssStatusCompat): GnssSummary {
        var used = 0
        var lowBand = 0
        var cn0 = 0f
        for (i in 0 until status.satelliteCount) {
          if (!status.usedInFix(i)) continue
          used++
          cn0 += status.getCn0DbHz(i)
          // L5 / E5a / B2a (1176 MHz) rather than L1 (1575 MHz): the second band of dual-frequency GNSS.
          if (status.hasCarrierFrequencyHz(i) && status.getCarrierFrequencyHz(i) < 1.3e9f) lowBand++
        }
        return GnssSummary(used, status.satelliteCount, lowBand, if (used > 0) cn0 / used else null)
      }
    }
  }

  private companion object {
    const val HEADER =
      "t_s,event,provider,lat,lon,acc_m,speed_kmh,speed_acc_kmh,fix_age_ms,used,smoothed_kmh,state,volume_pct," +
        "sats_used,sats_seen,l5_used,cn0_used,note"
    const val READING_COLUMNS = 15

    // Locale.ROOT: a Turkish phone would otherwise write "1,5" and break the CSV.
    fun num(value: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", value)

    fun num(value: Float, decimals: Int) = num(value.toDouble(), decimals)

    fun percent(level: Float) = (level * 100).toInt().toString()
  }
}

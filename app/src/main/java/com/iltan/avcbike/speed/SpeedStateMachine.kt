package com.iltan.avcbike.speed

/** Whether the music should be at the rider's normal level or turned down. */
enum class RideState {
  CRUISING,
  QUIET,
}

data class SpeedConfig(
  val quietBelowKmh: Float,
  val resumeAboveKmh: Float,
  val quietDelayMs: Long,
  val resumeDelayMs: Long,
  /** Fixes with a worse horizontal accuracy than this are ignored. */
  val maxAccuracyM: Float = 30f,
  /**
   * Readings whose speed is less certain than ± this are ignored: multipath near buildings can
   * report a stopped bike as moving. Lenient (3 m/s) so phones that report pessimistic values
   * still work.
   */
  val maxSpeedAccuracyKmh: Float = 10.8f,
  /** Number of recent readings the median is taken over. */
  val windowSize: Int = 3,
  /** A gap without readings longer than this starts smoothing and delays from scratch. */
  val staleAfterMs: Long = 5_000,
)

/**
 * One speed reading. [accuracyM] is the horizontal accuracy of the fix and [speedAccuracyKmh] the
 * uncertainty of the speed itself; either is null if the phone doesn't report it.
 */
data class SpeedSample(val timeMs: Long, val speedKmh: Float, val accuracyM: Float? = null, val speedAccuracyKmh: Float? = null)

data class SpeedSnapshot(val state: RideState, val smoothedKmh: Float?)

/**
 * Turns noisy GPS speed readings into a stable [RideState].
 *
 * Readings are smoothed with a short median, then two thresholds (hysteresis) and a minimum
 * dwell time keep the state from flickering at traffic lights or around a single threshold.
 * When readings stop arriving (tunnel, lost GPS) the current state is kept.
 */
class SpeedStateMachine(config: SpeedConfig) {
  var config: SpeedConfig = config
    set(value) {
      field = value
      pendingSinceMs = null
    }

  var state: RideState = RideState.CRUISING
    private set

  private val window = ArrayDeque<Float>()
  private var lastSampleMs: Long? = null
  private var pendingSinceMs: Long? = null

  val smoothedKmh: Float?
    get() = if (window.isEmpty()) null else window.sorted()[window.size / 2]

  fun onSample(sample: SpeedSample): SpeedSnapshot {
    if (!isUsable(sample)) return snapshot()

    val last = lastSampleMs
    if (last != null && sample.timeMs - last > config.staleAfterMs) {
      window.clear()
      pendingSinceMs = null
    }
    lastSampleMs = sample.timeMs

    window.addLast(sample.speedKmh)
    while (window.size > config.windowSize) window.removeFirst()

    val speed = smoothedKmh ?: return snapshot()
    val wantsChange =
      when (state) {
        RideState.CRUISING -> speed < config.quietBelowKmh
        RideState.QUIET -> speed > config.resumeAboveKmh
      }
    if (!wantsChange) {
      pendingSinceMs = null
      return snapshot()
    }

    val since = pendingSinceMs ?: sample.timeMs.also { pendingSinceMs = it }
    val delay = if (state == RideState.CRUISING) config.quietDelayMs else config.resumeDelayMs
    if (sample.timeMs - since >= delay) {
      state = if (state == RideState.CRUISING) RideState.QUIET else RideState.CRUISING
      pendingSinceMs = null
    }
    return snapshot()
  }

  fun reset() {
    state = RideState.CRUISING
    window.clear()
    lastSampleMs = null
    pendingSinceMs = null
  }

  private fun isUsable(sample: SpeedSample): Boolean {
    if (sample.speedKmh.isNaN() || sample.speedKmh < 0f) return false
    if (sample.accuracyM != null && sample.accuracyM > config.maxAccuracyM) return false
    if (sample.speedAccuracyKmh != null && sample.speedAccuracyKmh > config.maxSpeedAccuracyKmh) return false
    return true
  }

  private fun snapshot() = SpeedSnapshot(state, smoothedKmh)
}

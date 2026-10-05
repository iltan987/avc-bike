package com.iltan.avcbike.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedStateMachineTest {
  private val config = SpeedConfig(quietBelowKmh = 15f, resumeAboveKmh = 20f, quietDelayMs = 2_000, resumeDelayMs = 1_000)

  /** Feeds one reading per second starting at [startMs] and returns the final state. */
  private fun SpeedStateMachine.feed(vararg speeds: Float, startMs: Long = 0, stepMs: Long = 1_000): RideState {
    speeds.forEachIndexed { i, speed -> onSample(SpeedSample(startMs + i * stepMs, speed)) }
    return state
  }

  @Test
  fun startsCruising() {
    assertEquals(RideState.CRUISING, SpeedStateMachine(config).state)
  }

  @Test
  fun slowingDown_goesQuietOnlyAfterDelay() {
    val machine = SpeedStateMachine(config)
    machine.feed(50f, 50f, 50f)
    // Median needs two slow readings, then the 2 s delay must pass.
    assertEquals(RideState.CRUISING, machine.feed(5f, 5f, startMs = 3_000))
    assertEquals(RideState.CRUISING, machine.feed(5f, startMs = 5_000))
    assertEquals(RideState.QUIET, machine.feed(5f, startMs = 6_000))
  }

  @Test
  fun pullingAway_resumesAfterDelay() {
    val machine = SpeedStateMachine(config)
    machine.feed(0f, 0f, 0f, 0f, 0f)
    assertEquals(RideState.QUIET, machine.state)
    assertEquals(RideState.CRUISING, machine.feed(30f, 30f, 30f, startMs = 5_000))
  }

  @Test
  fun speedBetweenThresholds_keepsCurrentState() {
    val machine = SpeedStateMachine(config)
    machine.feed(0f, 0f, 0f, 0f, 0f)
    assertEquals(RideState.QUIET, machine.feed(17f, 18f, 17f, 18f, 17f, startMs = 5_000))

    val cruising = SpeedStateMachine(config)
    assertEquals(RideState.CRUISING, cruising.feed(17f, 18f, 17f, 18f, 17f))
  }

  @Test
  fun singleNoisySpike_isIgnored() {
    val machine = SpeedStateMachine(config)
    machine.feed(0f, 0f, 0f, 0f, 0f)
    // One bogus 60 km/h reading while standing still must not resume full volume.
    assertEquals(RideState.QUIET, machine.feed(0f, 60f, 0f, 0f, startMs = 5_000))
  }

  @Test
  fun briefDipBelowThreshold_doesNotGoQuiet() {
    val machine = SpeedStateMachine(config)
    machine.feed(40f, 40f, 40f)
    assertEquals(RideState.CRUISING, machine.feed(10f, 10f, 40f, 40f, 10f, 10f, 40f, startMs = 3_000))
  }

  @Test
  fun inaccurateFixes_areIgnored() {
    val machine = SpeedStateMachine(config)
    repeat(10) { machine.onSample(SpeedSample(it * 1_000L, 0f, accuracyM = 200f)) }
    assertEquals(RideState.CRUISING, machine.state)
    assertNull(machine.smoothedKmh)
  }

  @Test
  fun gpsGap_keepsStateAndRestartsDelay() {
    val machine = SpeedStateMachine(config)
    machine.feed(0f, 0f, 0f, 0f, 0f)
    assertEquals(RideState.QUIET, machine.state)
    // Nothing for 20 s (tunnel), then a single fast reading: the resume delay starts over.
    assertEquals(RideState.QUIET, machine.feed(40f, startMs = 25_000))
    assertEquals(RideState.CRUISING, machine.feed(40f, startMs = 26_000))
  }

  @Test
  fun zeroDelay_switchesImmediately() {
    val machine = SpeedStateMachine(config.copy(quietDelayMs = 0, windowSize = 1))
    assertEquals(RideState.QUIET, machine.feed(5f))
  }

  @Test
  fun reset_returnsToCruising() {
    val machine = SpeedStateMachine(config)
    machine.feed(0f, 0f, 0f, 0f, 0f)
    machine.reset()
    assertEquals(RideState.CRUISING, machine.state)
    assertNull(machine.smoothedKmh)
  }
}

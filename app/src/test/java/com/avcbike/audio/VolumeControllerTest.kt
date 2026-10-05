package com.avcbike.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeControllerTest {
  private class FakeVolume(override val max: Int = 15, override var current: Int = 10) : MusicVolume

  // Fades run in the test scope itself; advanceUntilIdle() doesn't run backgroundScope work.
  private fun TestScope.controller(volume: MusicVolume) = VolumeController(volume, this)

  @Test
  fun quiet_fadesToPercentOfNormal() = runTest {
    val volume = FakeVolume(current = 10)
    val controller = controller(volume)
    controller.quiet(percent = 40, fadeMs = 1000)
    advanceUntilIdle()
    assertEquals(4, volume.current)
    assertTrue(controller.isQuiet)
  }

  @Test
  fun restore_returnsToNormal() = runTest {
    val volume = FakeVolume(current = 12)
    val controller = controller(volume)
    controller.quiet(percent = 50, fadeMs = 600)
    advanceUntilIdle()
    controller.restore(fadeMs = 600)
    advanceUntilIdle()
    assertEquals(12, volume.current)
    assertFalse(controller.isQuiet)
  }

  @Test
  fun restoreDuringFadeDown_goesBackToNormal() = runTest {
    val volume = FakeVolume(current = 10)
    val controller = controller(volume)
    controller.quiet(percent = 0, fadeMs = 10_000)
    testScheduler.advanceTimeBy(3_500)
    controller.restore(fadeMs = 0)
    advanceUntilIdle()
    assertEquals(10, volume.current)
  }

  @Test
  fun userAdjustmentWhileQuiet_isNotOverridden() = runTest {
    val volume = FakeVolume(current = 10)
    val controller = controller(volume)
    controller.quiet(percent = 40, fadeMs = 0)
    advanceUntilIdle()
    volume.current = 7 // rider presses volume up at a red light
    controller.restore(fadeMs = 0)
    advanceUntilIdle()
    assertEquals(7, volume.current)
  }

  @Test
  fun quietTwice_keepsOriginalNormal() = runTest {
    val volume = FakeVolume(current = 10)
    val controller = controller(volume)
    controller.quiet(percent = 40, fadeMs = 0)
    advanceUntilIdle()
    controller.quiet(percent = 40, fadeMs = 0)
    advanceUntilIdle()
    assertEquals(4, volume.current)
    controller.restore(fadeMs = 0)
    advanceUntilIdle()
    assertEquals(10, volume.current)
  }

  @Test
  fun level_reportsFractionOfMax() = runTest {
    val volume = FakeVolume(max = 10, current = 10)
    val controller = controller(volume)
    controller.quiet(percent = 30, fadeMs = 0)
    advanceUntilIdle()
    assertEquals(0.3f, controller.level.value, 0.001f)
  }

  @Test
  fun zeroFade_restoresWithoutRunningCoroutines() {
    // No scheduler advancing here: a 0 ms restore must apply immediately (used on shutdown).
    val volume = FakeVolume(current = 10)
    val controller = VolumeController(volume, CoroutineScope(Job().apply { cancel() }))
    controller.quiet(percent = 40, fadeMs = 0)
    assertEquals(4, volume.current)
    controller.restore(fadeMs = 0)
    assertEquals(10, volume.current)
  }

  @Test
  fun quietLevel_neverMutesAudibleMusic() {
    assertEquals(1, VolumeController.quietLevel(normal = 2, percent = 10))
    assertEquals(0, VolumeController.quietLevel(normal = 0, percent = 40))
    assertEquals(6, VolumeController.quietLevel(normal = 15, percent = 40))
  }
}

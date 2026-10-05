package com.iltan.avcbike.audio

import android.media.AudioManager
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The device's music volume, as integer steps from 0 to [max]. */
interface MusicVolume {
  val max: Int
  var current: Int
}

class SystemMusicVolume(private val audioManager: AudioManager) : MusicVolume {
  override val max: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  override var current: Int
    get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    // Flags 0: no system volume panel popping up over the rider's screen.
    set(value) = audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
}

/**
 * Fades the music down when the rider slows and back up when they pull away.
 *
 * The "normal" level is whatever the rider had when we turned it down, so volume changes made
 * with the phone or intercom buttons while cruising are respected. If the rider adjusts the volume
 * while it is turned down, we leave their choice alone instead of restoring over it.
 */
class VolumeController(private val volume: MusicVolume, private val scope: CoroutineScope) {
  private var savedNormal: Int? = null
  private var lastSet: Int? = null
  private var fadeJob: Job? = null

  private val _level = MutableStateFlow(fraction(volume.current))
  /** Current music volume as a fraction of the maximum, for the UI. */
  val level: StateFlow<Float> = _level.asStateFlow()

  val isQuiet: Boolean
    get() = savedNormal != null

  fun quiet(percent: Int, fadeMs: Int) {
    if (savedNormal != null) return
    val normal = volume.current
    savedNormal = normal
    lastSet = normal
    fadeTo(quietLevel(normal, percent), fadeMs)
  }

  fun restore(fadeMs: Int) {
    val normal = savedNormal ?: return
    savedNormal = null
    val userAdjusted = lastSet != null && volume.current != lastSet
    if (userAdjusted) {
      fadeJob?.cancel()
      return
    }
    fadeTo(normal, fadeMs)
  }

  /** Re-reads the system volume so [level] follows changes made outside the app. */
  fun refresh() {
    if (fadeJob?.isActive != true) _level.value = fraction(volume.current)
  }

  private fun fadeTo(target: Int, fadeMs: Int) {
    fadeJob?.cancel()
    if (fadeMs <= 0) {
      // Synchronous, so it also works while the owner is shutting down and its scope is gone.
      if (volume.current != target) set(target)
      return
    }
    fadeJob =
      scope.launch {
        val start = volume.current
        val steps = abs(target - start)
        if (steps == 0) return@launch
        val stepDelay = fadeMs.toLong() / steps
        val direction = if (target > start) 1 else -1
        for (i in 1..steps) {
          if (stepDelay > 0) delay(stepDelay)
          set(start + i * direction)
        }
      }
  }

  private fun set(index: Int) {
    volume.current = index
    lastSet = index
    _level.value = fraction(index)
  }

  private fun fraction(index: Int) = if (volume.max == 0) 0f else index.toFloat() / volume.max

  companion object {
    /** The quiet step for a [normal] level, never fully muting music that was audible. */
    fun quietLevel(normal: Int, percent: Int): Int {
      if (normal == 0) return 0
      return (normal * percent / 100f).roundToInt().coerceIn(1, normal)
    }
  }
}

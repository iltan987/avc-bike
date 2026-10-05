package com.avcbike.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avcbike.speed.SpeedConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RideSettings(
  val quietBelowKmh: Float = 15f,
  val resumeAboveKmh: Float = 20f,
  val quietDelaySec: Float = 2f,
  val resumeDelaySec: Float = 1f,
  /** Quiet volume as a percentage of the rider's normal volume. */
  val quietVolumePercent: Int = 40,
  val fadeMs: Int = 1500,
  val useMph: Boolean = false,
  val keepScreenOn: Boolean = false,
  val onboardingDone: Boolean = false,
) {
  fun toSpeedConfig() =
    SpeedConfig(
      quietBelowKmh = quietBelowKmh,
      resumeAboveKmh = resumeAboveKmh,
      quietDelayMs = (quietDelaySec * 1000).toLong(),
      resumeDelayMs = (resumeDelaySec * 1000).toLong(),
    )
}

enum class Preset(val quietBelowKmh: Float, val resumeAboveKmh: Float) {
  CITY(15f, 20f),
  TOURING(25f, 30f);

  companion object {
    fun of(settings: RideSettings): Preset? =
      entries.find { it.quietBelowKmh == settings.quietBelowKmh && it.resumeAboveKmh == settings.resumeAboveKmh }
  }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
  private val dataStore = context.applicationContext.dataStore

  val settings: Flow<RideSettings> = dataStore.data.map { it.toSettings() }

  suspend fun update(transform: (RideSettings) -> RideSettings) {
    dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
  }

  private fun Preferences.toSettings(): RideSettings {
    val defaults = RideSettings()
    return RideSettings(
      quietBelowKmh = this[QUIET_BELOW_KMH] ?: defaults.quietBelowKmh,
      resumeAboveKmh = this[RESUME_ABOVE_KMH] ?: defaults.resumeAboveKmh,
      quietDelaySec = this[QUIET_DELAY_SEC] ?: defaults.quietDelaySec,
      resumeDelaySec = this[RESUME_DELAY_SEC] ?: defaults.resumeDelaySec,
      quietVolumePercent = this[QUIET_VOLUME_PERCENT] ?: defaults.quietVolumePercent,
      fadeMs = this[FADE_MS] ?: defaults.fadeMs,
      useMph = this[USE_MPH] ?: defaults.useMph,
      keepScreenOn = this[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
      onboardingDone = this[ONBOARDING_DONE] ?: defaults.onboardingDone,
    )
  }

  private fun MutablePreferences.write(settings: RideSettings) {
    this[QUIET_BELOW_KMH] = settings.quietBelowKmh
    this[RESUME_ABOVE_KMH] = settings.resumeAboveKmh
    this[QUIET_DELAY_SEC] = settings.quietDelaySec
    this[RESUME_DELAY_SEC] = settings.resumeDelaySec
    this[QUIET_VOLUME_PERCENT] = settings.quietVolumePercent
    this[FADE_MS] = settings.fadeMs
    this[USE_MPH] = settings.useMph
    this[KEEP_SCREEN_ON] = settings.keepScreenOn
    this[ONBOARDING_DONE] = settings.onboardingDone
  }

  private companion object {
    val QUIET_BELOW_KMH = floatPreferencesKey("quiet_below_kmh")
    val RESUME_ABOVE_KMH = floatPreferencesKey("resume_above_kmh")
    val QUIET_DELAY_SEC = floatPreferencesKey("quiet_delay_sec")
    val RESUME_DELAY_SEC = floatPreferencesKey("resume_delay_sec")
    val QUIET_VOLUME_PERCENT = intPreferencesKey("quiet_volume_percent")
    val FADE_MS = intPreferencesKey("fade_ms")
    val USE_MPH = booleanPreferencesKey("use_mph")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
  }
}

package com.iltan.avcbike.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iltan.avcbike.audio.MusicVolume
import com.iltan.avcbike.audio.VolumeController
import com.iltan.avcbike.ride.RideSession
import com.iltan.avcbike.settings.Preset
import com.iltan.avcbike.settings.RideSettings
import com.iltan.avcbike.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: RideSettings = RideSettings(), val rideActive: Boolean = false, val previewing: Boolean = false)

class SettingsViewModel(private val repository: SettingsRepository, musicVolume: MusicVolume) : ViewModel() {
  private val volume = VolumeController(musicVolume, viewModelScope)
  private val previewing = MutableStateFlow(false)

  val uiState: StateFlow<SettingsUiState> =
    combine(repository.settings, RideSession.status, previewing) { settings, status, preview -> SettingsUiState(settings, status.active, preview) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

  fun update(transform: (RideSettings) -> RideSettings) {
    viewModelScope.launch { repository.update(transform) }
  }

  fun applyPreset(preset: Preset) = update { it.copy(quietBelowKmh = preset.quietBelowKmh, resumeAboveKmh = preset.resumeAboveKmh) }

  /** Lets the rider hear the quiet level: dips the music for a moment, then brings it back. */
  fun previewQuietVolume() {
    if (previewing.value || uiState.value.rideActive) return
    val settings = uiState.value.settings
    previewing.value = true
    viewModelScope.launch {
      volume.quiet(settings.quietVolumePercent, settings.fadeMs)
      delay(settings.fadeMs + PREVIEW_HOLD_MS)
      volume.restore(settings.fadeMs)
      delay(settings.fadeMs.toLong())
      previewing.value = false
    }
  }

  override fun onCleared() {
    // Leaving the screen mid-preview must not strand the music at the quiet level.
    volume.restore(fadeMs = 0)
  }

  private companion object {
    const val PREVIEW_HOLD_MS = 2_000L
  }
}

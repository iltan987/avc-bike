package com.iltan.avcbike.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iltan.avcbike.ride.RideSession
import com.iltan.avcbike.ride.RideStatus
import com.iltan.avcbike.settings.RideSettings
import com.iltan.avcbike.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RideUiState(val status: RideStatus = RideStatus(), val settings: RideSettings = RideSettings())

class RideViewModel(settingsRepository: SettingsRepository) : ViewModel() {
  val uiState: StateFlow<RideUiState> =
    combine(RideSession.status, settingsRepository.settings, ::RideUiState)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RideUiState())
}

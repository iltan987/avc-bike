package com.avcbike

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Ride : NavKey

@Serializable data object Settings : NavKey

@Serializable data object Onboarding : NavKey

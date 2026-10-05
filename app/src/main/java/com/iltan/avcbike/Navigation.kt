package com.iltan.avcbike

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.iltan.avcbike.settings.SettingsRepository
import com.iltan.avcbike.ui.onboarding.OnboardingScreen
import com.iltan.avcbike.ui.ride.RideScreen
import com.iltan.avcbike.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun MainNavigation() {
  val context = LocalContext.current.applicationContext
  val repository = remember { SettingsRepository(context) }
  // null until the stored settings are read, so returning riders don't see onboarding flash by.
  val onboardingDone by remember { repository.settings.map { it.onboardingDone } }.collectAsStateWithLifecycle(initialValue = null)
  when (onboardingDone) {
    null -> Unit
    else -> MainNavigation(startAtOnboarding = onboardingDone == false, repository = repository)
  }
}

@Composable
private fun MainNavigation(startAtOnboarding: Boolean, repository: SettingsRepository) {
  val backStack = rememberNavBackStack(if (startAtOnboarding) Onboarding else Ride)
  val scope = rememberCoroutineScope()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Onboarding> {
          OnboardingScreen(
            onFinished = {
              scope.launch { repository.update { it.copy(onboardingDone = true) } }
              backStack.clear()
              backStack.add(Ride)
            },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<Ride> { RideScreen(onOpenSettings = { backStack.add(Settings) }, modifier = Modifier.safeDrawingPadding()) }
        entry<Settings> { SettingsScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding()) }
      },
  )
}

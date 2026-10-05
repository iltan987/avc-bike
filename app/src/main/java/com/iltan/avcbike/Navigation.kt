package com.iltan.avcbike

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.iltan.avcbike.settings.SettingsRepository
import com.iltan.avcbike.ui.onboarding.OnboardingScreen
import com.iltan.avcbike.ui.ride.RideScreen
import com.iltan.avcbike.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(onboardingDone: Boolean, repository: SettingsRepository) {
  // Only the first value matters: the back stack is created once.
  val backStack = rememberNavBackStack(if (onboardingDone) Ride else Onboarding)
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

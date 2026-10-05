package com.avcbike

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.avcbike.ui.ride.RideScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Ride)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider { entry<Ride> { RideScreen(onOpenSettings = {}, modifier = Modifier.safeDrawingPadding()) } },
  )
}

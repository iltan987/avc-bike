package com.iltan.avcbike.ui.ride

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iltan.avcbike.ride.RideStatus
import com.iltan.avcbike.speed.RideState
import com.iltan.avcbike.theme.AVCBikeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI tests for [RideScreen]. */
class RideScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun idle_showsStartButton() {
    var started = false
    composeTestRule.setContent { AVCBikeTheme { RideScreen(RideUiState(), false, { started = true }, {}, {}, {}) } }
    composeTestRule.onNodeWithText("READY TO RIDE").assertIsDisplayed()
    composeTestRule.onNodeWithText("START RIDE").performClick()
    assertTrue(started)
  }

  @Test
  fun quiet_showsSpeedAndQuietStatus() {
    val state = RideUiState(status = RideStatus(active = true, state = RideState.QUIET, speedKmh = 3f, volumeLevel = 0.3f))
    composeTestRule.setContent { AVCBikeTheme { RideScreen(state, false, {}, {}, {}, {}) } }
    composeTestRule.onNodeWithText("3").assertIsDisplayed()
    composeTestRule.onNodeWithText("QUIET · 40% VOLUME").assertIsDisplayed()
    composeTestRule.onNodeWithText("STOP RIDE").assertIsDisplayed()
  }
}

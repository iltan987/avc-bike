package com.avcbike.ui.ride

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avcbike.ride.RideSession
import kotlin.math.roundToInt

/**
 * Debug builds only: replaces GPS with a slider so slowing down and pulling away can be tried at a
 * desk. Not translated on purpose; friends never see it.
 */
@Composable
fun SpeedSimulator(modifier: Modifier = Modifier) {
  val simulated by RideSession.simulatedSpeedKmh.collectAsStateWithLifecycle()
  var lastSpeed by rememberSaveable { mutableFloatStateOf(40f) }
  val speed = remember(simulated) { simulated ?: lastSpeed }

  Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "DEBUG · simulate ${speed.roundToInt()} km/h",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        Switch(checked = simulated != null, onCheckedChange = { on -> RideSession.simulatedSpeedKmh.value = if (on) lastSpeed else null })
      }
      Slider(
        value = speed,
        onValueChange = {
          lastSpeed = it
          if (simulated != null) RideSession.simulatedSpeedKmh.value = it
        },
        valueRange = 0f..80f,
        enabled = simulated != null,
      )
    }
  }
}

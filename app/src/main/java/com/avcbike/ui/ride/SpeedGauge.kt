package com.avcbike.ui.ride

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.avcbike.R
import com.avcbike.theme.AVCBikeTheme
import com.avcbike.theme.SpeedNumerals
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val TICK_COUNT = 28

/**
 * Dashboard gauge: the speed in the middle, and an arc around it showing the current music
 * volume. The arc animates as the volume fades, in the [accent] color of the ride state.
 */
@Composable
fun SpeedGauge(
  speed: Int?,
  unitLabel: String,
  volumeLevel: Float,
  accent: Color,
  active: Boolean,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val level by animateFloatAsState(if (active) volumeLevel.coerceIn(0f, 1f) else 0f, tween(600), label = "volume")
  val arcColor by animateColorAsState(if (active) accent else colors.outline, tween(500), label = "accent")
  val track = colors.surfaceContainerHigh
  val tickIdle = colors.outlineVariant
  val volumePercent = (volumeLevel * 100).roundToInt()
  val description = stringResource(R.string.gauge_description, speed?.toString() ?: "–", unitLabel, volumePercent)

  Box(modifier.aspectRatio(1f).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = 18.dp.toPx()
      val inset = stroke * 1.5f
      val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
      val topLeft = Offset(inset, inset)

      drawArc(track, START_ANGLE, SWEEP_ANGLE, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      if (level > 0.005f) {
        // Soft glow under the arc, then the arc itself.
        drawArc(arcColor.copy(alpha = 0.10f), START_ANGLE, SWEEP_ANGLE * level, false, topLeft, arcSize, style = Stroke(stroke * 1.7f, cap = StrokeCap.Round))
        drawArc(arcColor, START_ANGLE, SWEEP_ANGLE * level, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      }
      drawTicks(radius = arcSize.width / 2 - stroke * 1.6f, level = level, lit = arcColor, idle = tickIdle)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = speed?.toString() ?: "–",
        // No reading yet: a thin dash instead of heavy numerals.
        style = if (speed == null) SpeedNumerals.copy(fontWeight = FontWeight.Thin) else SpeedNumerals,
        color = if (active && speed != null) colors.onSurface else colors.onSurfaceVariant,
      )
      Text(text = unitLabel.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
      Spacer(Modifier.height(20.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null, tint = arcColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (active) stringResource(R.string.volume_percent, volumePercent) else stringResource(R.string.volume_idle),
          style = MaterialTheme.typography.labelLarge,
          color = arcColor,
        )
      }
    }
  }
}

private fun DrawScope.drawTicks(radius: Float, level: Float, lit: Color, idle: Color) {
  val major = 5.dp.toPx()
  val width = 2.dp.toPx()
  for (i in 0 until TICK_COUNT) {
    val fraction = i / (TICK_COUNT - 1f)
    val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
    val length = if (i % 3 == 0) major * 2 else major
    val outer = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    val inner = Offset(center.x + (radius - length) * cos(angle).toFloat(), center.y + (radius - length) * sin(angle).toFloat())
    val color = if (fraction <= level && level > 0f) lit.copy(alpha = 0.7f) else idle
    drawLine(color, inner, outer, strokeWidth = width, cap = StrokeCap.Round)
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360)
@Composable
private fun SpeedGaugeCruisingPreview() {
  AVCBikeTheme(darkTheme = true) { SpeedGauge(speed = 42, unitLabel = "km/h", volumeLevel = 0.85f, accent = MaterialTheme.colorScheme.primary, active = true) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360)
@Composable
private fun SpeedGaugeQuietPreview() {
  AVCBikeTheme(darkTheme = true) { SpeedGauge(speed = 3, unitLabel = "km/h", volumeLevel = 0.33f, accent = MaterialTheme.colorScheme.secondary, active = true) }
}

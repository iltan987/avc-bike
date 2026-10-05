package com.iltan.avcbike.ui.ride

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iltan.avcbike.R
import com.iltan.avcbike.settings.displaySpeed
import com.iltan.avcbike.theme.AVCBikeTheme
import com.iltan.avcbike.theme.SpeedNumerals
import com.iltan.avcbike.ui.uppercaseLocalized
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val TICK_COUNT = 28
private const val VOLUME_BARS = 10

/** Full-scale speed of the arc. City riding sits in the lower half, where the quiet zone is. */
const val GAUGE_MAX_KMH = 120f

/**
 * Dashboard gauge. The arc is a speedometer: it follows [speedKmh] together with the number, and
 * the quiet zone (below [quietBelowKmh]) is shaded on the track so the rider can see where the
 * music turns down. Music volume has its own small bar meter under the number.
 *
 * [speedKmh] and [accent] are drawn as given; callers animate them so the number, the arc and
 * anything else tinted by the ride state stay in step.
 */
@Composable
fun SpeedGauge(
  speedKmh: Float?,
  useMph: Boolean,
  unitLabel: String,
  quietBelowKmh: Float,
  volumeLevel: Float,
  accent: Color,
  active: Boolean,
  modifier: Modifier = Modifier,
  volumeFadeMs: Int = 600,
) {
  val colors = MaterialTheme.colorScheme
  val level by animateFloatAsState(if (active) volumeLevel.coerceIn(0f, 1f) else 0f, tween(volumeFadeMs), label = "volume")
  val speedFraction = ((speedKmh ?: 0f) / GAUGE_MAX_KMH).coerceIn(0f, 1f)
  val quietFraction = (quietBelowKmh / GAUGE_MAX_KMH).coerceIn(0f, 1f)
  val speed = speedKmh?.let { displaySpeed(it, useMph) }
  val track = colors.surfaceContainerHigh
  val quietZone = colors.secondary.copy(alpha = 0.7f)
  val idle = colors.outlineVariant
  val volumePercent = (volumeLevel * 100).roundToInt()
  val description = stringResource(R.string.gauge_description, speed?.toString() ?: "–", unitLabel, volumePercent)

  BoxWithConstraints(modifier.aspectRatio(1f).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
    // Numerals scale with the gauge so they clear the tick marks at any size. Sized from the
    // gauge, not the font setting: at large font scales sp numerals would spill over the ticks.
    val numeralSize = with(LocalDensity.current) { (maxWidth * 0.28f).toSp() }
    val numerals = SpeedNumerals.copy(fontSize = numeralSize, lineHeight = numeralSize)
    Canvas(Modifier.fillMaxSize()) {
      val stroke = 18.dp.toPx()
      val inset = stroke * 1.5f
      val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
      val topLeft = Offset(inset, inset)

      drawArc(track, START_ANGLE, SWEEP_ANGLE, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      // Quiet zone: a thin band just inside the track, visible even when the speed arc covers it.
      val band = 3.dp.toPx()
      val bandInset = inset + stroke / 2 + band * 1.8f
      drawArc(
        quietZone,
        START_ANGLE,
        SWEEP_ANGLE * quietFraction,
        false,
        Offset(bandInset, bandInset),
        Size(size.width - bandInset * 2, size.height - bandInset * 2),
        style = Stroke(band, cap = StrokeCap.Round),
      )
      // Nothing below what the number shows as 0, so a stopped bike doesn't leave a dot at the start.
      if (active && (speedKmh ?: 0f) >= 0.5f) {
        // Soft glow under the arc, then the arc itself.
        drawArc(accent.copy(alpha = 0.10f), START_ANGLE, SWEEP_ANGLE * speedFraction, false, topLeft, arcSize, style = Stroke(stroke * 1.7f, cap = StrokeCap.Round))
        drawArc(accent, START_ANGLE, SWEEP_ANGLE * speedFraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      }
      drawTicks(radius = arcSize.width / 2 - stroke * 1.9f, lit = if (active) speedFraction else 0f, litColor = accent, idle = idle)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = speed?.toString() ?: "–",
        // No reading yet: a thin dash instead of heavy numerals.
        style = if (speed == null) numerals.copy(fontWeight = FontWeight.Thin) else numerals,
        color = if (active && speed != null) colors.onSurface else colors.onSurfaceVariant,
      )
      Text(text = unitLabel.uppercaseLocalized(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val volumeColor = if (active) accent else colors.onSurfaceVariant
        Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null, tint = volumeColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        VolumeBars(level = level, lit = volumeColor, idle = idle)
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (active) stringResource(R.string.volume_percent_short, (level * 100).roundToInt()) else "–",
          style = MaterialTheme.typography.labelMedium,
          color = volumeColor,
        )
      }
    }
  }
}

/** A small rising bar meter, like a phone's volume indicator. */
@Composable
private fun VolumeBars(level: Float, lit: Color, idle: Color) {
  Canvas(Modifier.width(40.dp).height(14.dp)) {
    val gap = 2.dp.toPx()
    val barWidth = (size.width - gap * (VOLUME_BARS - 1)) / VOLUME_BARS
    val litBars = level * VOLUME_BARS
    for (i in 0 until VOLUME_BARS) {
      val height = size.height * (0.3f + 0.7f * (i + 1) / VOLUME_BARS)
      // The bar being crossed is partly lit, so fades look continuous rather than stepping.
      val fill = (litBars - i).coerceIn(0f, 1f)
      val color = if (fill > 0f) lerp(idle, lit, fill) else idle
      drawRoundRect(
        color = color,
        topLeft = Offset(i * (barWidth + gap), size.height - height),
        size = Size(barWidth, height),
        cornerRadius = CornerRadius(barWidth / 2),
      )
    }
  }
}

private fun DrawScope.drawTicks(radius: Float, lit: Float, litColor: Color, idle: Color) {
  val major = 5.dp.toPx()
  val width = 2.dp.toPx()
  for (i in 0 until TICK_COUNT) {
    val fraction = i / (TICK_COUNT - 1f)
    val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
    val length = if (i % 3 == 0) major * 2 else major
    val outer = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    val inner = Offset(center.x + (radius - length) * cos(angle).toFloat(), center.y + (radius - length) * sin(angle).toFloat())
    val color = if (lit > 0f && fraction <= lit) litColor.copy(alpha = 0.7f) else idle
    drawLine(color, inner, outer, strokeWidth = width, cap = StrokeCap.Round)
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360)
@Composable
private fun SpeedGaugeCruisingPreview() {
  AVCBikeTheme(darkTheme = true) {
    SpeedGauge(speedKmh = 42f, useMph = false, unitLabel = "km/h", quietBelowKmh = 15f, volumeLevel = 0.8f, accent = MaterialTheme.colorScheme.primary, active = true)
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360)
@Composable
private fun SpeedGaugeQuietPreview() {
  AVCBikeTheme(darkTheme = true) {
    SpeedGauge(speedKmh = 3f, useMph = false, unitLabel = "km/h", quietBelowKmh = 15f, volumeLevel = 0.32f, accent = MaterialTheme.colorScheme.secondary, active = true)
  }
}

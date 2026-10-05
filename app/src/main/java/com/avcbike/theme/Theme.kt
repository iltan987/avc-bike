package com.avcbike.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.avcbike.speed.RideState

private val DarkColorScheme =
  darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF221100),
    primaryContainer = EmberDeep,
    onPrimaryContainer = EmberPale,
    secondary = Teal,
    onSecondary = TealNight,
    secondaryContainer = TealDeep,
    onSecondaryContainer = TealPale,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = Carbon3,
    onSurfaceVariant = Fog,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Carbon,
    surfaceContainer = Carbon2,
    surfaceContainerHigh = Carbon3,
    surfaceContainerHighest = Carbon4,
    outline = Steel,
    outlineVariant = Carbon4,
    error = Signal,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmberDark,
    onPrimary = Color.White,
    primaryContainer = EmberMist,
    onPrimaryContainer = EmberNight,
    secondary = TealDark,
    onSecondary = Color.White,
    secondaryContainer = TealMist,
    onSecondaryContainer = TealNight,
    background = Paper,
    onBackground = Graphite,
    surface = Paper,
    onSurface = Graphite,
    surfaceVariant = Paper3,
    onSurfaceVariant = Slate,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Paper2,
    surfaceContainerHigh = Paper3,
    surfaceContainerHighest = Pebble.copy(alpha = 0.5f),
    outline = Pebble,
    outlineVariant = Paper3,
    error = SignalDark,
  )

/**
 * App theme. A fixed brand palette (no wallpaper-based dynamic color) so the dashboard looks the
 * same on every friend's phone; light or dark follows the system setting.
 */
@Composable
fun AVCBikeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

/** Accent for a ride state: ember while cruising, teal while the music is turned down. */
fun ColorScheme.stateColor(state: RideState): Color = if (state == RideState.QUIET) secondary else primary

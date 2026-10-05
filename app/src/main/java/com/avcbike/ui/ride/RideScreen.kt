package com.avcbike.ui.ride

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avcbike.BuildConfig
import com.avcbike.R
import com.avcbike.ride.RideSession
import com.avcbike.ride.RideStatus
import com.avcbike.settings.RideSettings
import com.avcbike.settings.SettingsRepository
import com.avcbike.settings.displaySpeed
import com.avcbike.settings.speedUnitLabel
import com.avcbike.speed.RideState
import com.avcbike.theme.AVCBikeTheme
import com.avcbike.theme.stateColor

@Composable
fun RideScreen(
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: RideViewModel = rideViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var locationDenied by remember { mutableStateOf(false) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
        locationDenied = false
        RideSession.start(context)
      } else {
        locationDenied = true
      }
    }

  KeepScreenOn(state.status.active && state.settings.keepScreenOn)

  RideScreen(
    state = state,
    locationDenied = locationDenied,
    onStart = {
      if (hasFineLocation(context)) RideSession.start(context) else permissionLauncher.launch(ridePermissions())
    },
    onStop = { RideSession.stop(context) },
    onOpenSettings = onOpenSettings,
    onOpenAppSettings = { openAppSettings(context) },
    modifier = modifier,
  )
}

@Composable
internal fun RideScreen(
  state: RideUiState,
  locationDenied: Boolean,
  onStart: () -> Unit,
  onStop: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAppSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = state.status
  val settings = state.settings
  val colors = MaterialTheme.colorScheme
  val accent by animateColorAsState(colors.stateColor(status.state), tween(500), label = "accent")
  val unit = stringResource(speedUnitLabel(settings.useMph))

  Column(modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Wordmark()
      Spacer(Modifier.weight(1f))
      IconButton(onClick = onOpenSettings) {
        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title), tint = colors.onSurfaceVariant)
      }
    }

    Spacer(Modifier.weight(1f))
    SpeedGauge(
      speed = status.speedKmh?.let { displaySpeed(it, settings.useMph) },
      unitLabel = unit,
      volumeLevel = status.volumeLevel,
      accent = accent,
      active = status.active,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    StatusChip(status = status, quietPercent = settings.quietVolumePercent, accent = if (status.active) accent else colors.outline)
    Spacer(Modifier.height(14.dp))
    Text(
      text =
        stringResource(
          R.string.thresholds_summary,
          displaySpeed(settings.quietBelowKmh, settings.useMph),
          displaySpeed(settings.resumeAboveKmh, settings.useMph),
          unit,
        ),
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f))

    if (BuildConfig.DEBUG && status.active) SpeedSimulator(Modifier.padding(bottom = 16.dp))
    AnimatedVisibility(locationDenied && !status.active) { LocationDeniedNotice(onOpenAppSettings) }
    RideButton(active = status.active, onStart = onStart, onStop = onStop)
    Spacer(Modifier.height(20.dp))
  }
}

@Composable
private fun Wordmark() {
  val colors = MaterialTheme.colorScheme
  Text(
    text =
      buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Black, color = colors.primary)) { append("AVC") }
        withStyle(SpanStyle(fontWeight = FontWeight.Light, color = colors.onSurface)) { append(" BIKE") }
      },
    style = MaterialTheme.typography.titleLarge,
  )
}

@Composable
private fun StatusChip(status: RideStatus, quietPercent: Int, accent: Color) {
  val label =
    when {
      !status.active -> stringResource(R.string.status_ready)
      status.speedKmh == null -> stringResource(R.string.status_waiting_for_gps)
      status.state == RideState.QUIET -> stringResource(R.string.status_quiet, quietPercent)
      else -> stringResource(R.string.status_cruising)
    }
  Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).background(accent, CircleShape))
      Spacer(Modifier.width(10.dp))
      Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
    }
  }
}

@Composable
private fun RideButton(active: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
  val haptics = LocalHapticFeedback.current
  val colors = MaterialTheme.colorScheme
  Button(
    onClick = {
      haptics.performHapticFeedback(HapticFeedbackType.Confirm)
      if (active) onStop() else onStart()
    },
    modifier = Modifier.fillMaxWidth().height(76.dp),
    shape = RoundedCornerShape(22.dp),
    colors =
      if (active) ButtonDefaults.buttonColors(containerColor = colors.surfaceContainerHigh, contentColor = colors.error)
      else ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
  ) {
    Icon(painterResource(if (active) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(28.dp))
    Spacer(Modifier.width(10.dp))
    Text(
      text = stringResource(if (active) R.string.stop_ride else R.string.start_ride).uppercase(),
      style = MaterialTheme.typography.titleMedium.copy(letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing),
    )
  }
}

@Composable
private fun LocationDeniedNotice(onOpenAppSettings: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text(stringResource(R.string.location_denied), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.open_settings)) }
    }
  }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
  val view = LocalView.current
  DisposableEffect(view, enabled) {
    view.keepScreenOn = enabled
    onDispose { view.keepScreenOn = false }
  }
}

@Composable
private fun rideViewModel(): RideViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { RideViewModel(SettingsRepository(context)) }
}

internal fun hasFineLocation(context: Context) =
  ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun ridePermissions(): Array<String> = buildList {
  add(Manifest.permission.ACCESS_FINE_LOCATION)
  add(Manifest.permission.ACCESS_COARSE_LOCATION)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

internal fun openAppSettings(context: Context) {
  context.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  )
}

private val previewCruising =
  RideUiState(status = RideStatus(active = true, state = RideState.CRUISING, speedKmh = 42f, volumeLevel = 0.8f), settings = RideSettings())

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 380, heightDp = 800)
@Composable
private fun RideScreenCruisingPreview() {
  AVCBikeTheme(darkTheme = true) { RideScreen(previewCruising, false, {}, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 380, heightDp = 800)
@Composable
private fun RideScreenQuietPreview() {
  val state = previewCruising.copy(status = previewCruising.status.copy(state = RideState.QUIET, speedKmh = 2f, volumeLevel = 0.33f))
  AVCBikeTheme(darkTheme = true) { RideScreen(state, false, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun RideScreenIdleLightPreview() {
  AVCBikeTheme(darkTheme = false) { RideScreen(RideUiState(), true, {}, {}, {}, {}) }
}

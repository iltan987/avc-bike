package com.iltan.avcbike.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iltan.avcbike.R
import com.iltan.avcbike.ride.RideStatus
import com.iltan.avcbike.ride.batteryExemptionIntent
import com.iltan.avcbike.ride.isIgnoringBatteryOptimizations
import com.iltan.avcbike.speed.RideState
import com.iltan.avcbike.theme.AVCBikeTheme
import com.iltan.avcbike.ui.ride.SpeedGauge
import com.iltan.avcbike.ui.ride.StatusChip
import com.iltan.avcbike.ui.ride.openAppSettings
import com.iltan.avcbike.ui.uppercaseLocalized
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Page {
  WELCOME,
  LOCATION,
  NOTIFICATIONS,
  BATTERY,
}

private fun Context.onboardingPages() = buildList {
  add(Page.WELCOME)
  add(Page.LOCATION)
  // Before Android 13 notifications don't need a runtime permission.
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Page.NOTIFICATIONS)
  if (!isIgnoringBatteryOptimizations()) add(Page.BATTERY)
}

private data class Permissions(val location: Boolean, val notifications: Boolean, val battery: Boolean)

private fun Context.currentPermissions() =
  Permissions(
    location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
    battery = isIgnoringBatteryOptimizations(),
  )

private fun Context.granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  // Fixed for this visit, so pages don't disappear under the rider as permissions are granted.
  val pages = remember { context.onboardingPages() }
  var permissions by remember { mutableStateOf(context.currentPermissions()) }
  var locationAsked by remember { mutableStateOf(false) }
  // The rider may grant permissions from system settings and come back.
  LifecycleResumeEffect(Unit) {
    permissions = context.currentPermissions()
    onPauseOrDispose {}
  }

  val pagerState = rememberPagerState { pages.size }
  val scope = rememberCoroutineScope()
  fun next() {
    if (pagerState.currentPage == pages.lastIndex) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
  }

  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      locationAsked = true
      permissions = context.currentPermissions()
      if (permissions.location) next()
    }
  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      permissions = context.currentPermissions()
      next()
    }
  val batteryLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      permissions = context.currentPermissions()
      if (permissions.battery) next()
    }

  val page = pages[pagerState.currentPage]
  Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
      when (pages[index]) {
        Page.WELCOME -> WelcomePage()
        Page.LOCATION ->
          PermissionPage(
            icon = R.drawable.ic_location_on,
            title = stringResource(R.string.onboarding_location_title),
            body = stringResource(R.string.onboarding_location_body),
            status =
              when {
                permissions.location -> stringResource(R.string.onboarding_location_granted)
                locationAsked -> stringResource(R.string.onboarding_location_denied)
                else -> null
              },
          )
        Page.NOTIFICATIONS ->
          PermissionPage(
            icon = R.drawable.ic_notifications,
            title = stringResource(R.string.onboarding_notifications_title),
            body = stringResource(R.string.onboarding_notifications_body),
            status = if (permissions.notifications) stringResource(R.string.onboarding_notifications_granted) else null,
          )
        Page.BATTERY ->
          PermissionPage(
            icon = R.drawable.ic_battery_android_full,
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
            status = if (permissions.battery) stringResource(R.string.onboarding_battery_granted) else null,
          )
      }
    }

    PageDots(count = pages.size, current = pagerState.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.height(24.dp))

    val isLast = pagerState.currentPage == pages.lastIndex
    val (label, action) =
      when {
        page == Page.LOCATION && !permissions.location && locationAsked ->
          stringResource(R.string.open_settings) to { openAppSettings(context) }
        page == Page.LOCATION && !permissions.location ->
          stringResource(R.string.onboarding_allow_location) to
            { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        page == Page.NOTIFICATIONS && !permissions.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
          stringResource(R.string.onboarding_allow_notifications) to { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        page == Page.BATTERY && !permissions.battery ->
          stringResource(R.string.onboarding_allow_battery) to { batteryLauncher.launch(context.batteryExemptionIntent()) }
        isLast -> stringResource(R.string.onboarding_lets_ride) to ::next
        else -> stringResource(R.string.onboarding_next) to ::next
      }
    Button(
      onClick = action,
      modifier = Modifier.fillMaxWidth().height(64.dp),
      shape = RoundedCornerShape(20.dp),
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
      Text(
        label.uppercaseLocalized(),
        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    // A way past each permission page without granting it; the ride screen asks again on Start.
    val showSkip =
      when (page) {
        Page.WELCOME -> false
        Page.LOCATION -> !permissions.location
        Page.NOTIFICATIONS -> !permissions.notifications
        Page.BATTERY -> !permissions.battery
      }
    TextButton(onClick = ::next, enabled = showSkip, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
      Text(if (showSkip) stringResource(R.string.onboarding_not_now) else "")
    }
  }
}

@Composable
private fun WelcomePage() {
  // A looping mini ride: cruise, brake smoothly to a stop (the music dips below 15 km/h), wait at
  // the light, then pull away (the music comes back above 20 km/h), the same rules a real ride uses.
  val speed = remember { Animatable(DEMO_CRUISE_KMH) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1_400)
      speed.animateTo(0f, tween(2_600, easing = LinearOutSlowInEasing))
      delay(1_600)
      speed.animateTo(DEMO_CRUISE_KMH, tween(2_800, easing = FastOutSlowInEasing))
    }
  }
  var quiet by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    snapshotFlow { speed.value }
      .collect { kmh ->
        if (!quiet && kmh < DEMO_QUIET_BELOW_KMH) quiet = true
        if (quiet && kmh > DEMO_RESUME_ABOVE_KMH) quiet = false
      }
  }
  val accent by animateColorAsState(if (quiet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, tween(700), label = "demo")
  val status = RideStatus(active = true, state = if (quiet) RideState.QUIET else RideState.CRUISING, speedKmh = speed.value, volumeLevel = 0f)

  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    SpeedGauge(
      speed = speed.value.roundToInt(),
      unitLabel = stringResource(R.string.unit_kmh),
      volumeLevel = if (quiet) DEMO_QUIET_VOLUME else DEMO_CRUISE_VOLUME,
      accent = accent,
      active = true,
      modifier = Modifier.fillMaxWidth(0.78f),
    )
    Spacer(Modifier.height(16.dp))
    StatusChip(status = status, accent = accent)
    Spacer(Modifier.height(28.dp))
    PageText(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body))
  }
}

private const val DEMO_CRUISE_KMH = 52f
private const val DEMO_QUIET_BELOW_KMH = 15f
private const val DEMO_RESUME_ABOVE_KMH = 20f
private const val DEMO_CRUISE_VOLUME = 0.8f
private const val DEMO_QUIET_VOLUME = 0.32f

@Composable
private fun PermissionPage(@DrawableRes icon: Int, title: String, body: String, status: String?) {
  val colors = MaterialTheme.colorScheme
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Box(Modifier.size(132.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
      Box(Modifier.size(88.dp).background(colors.primary.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(44.dp))
      }
    }
    Spacer(Modifier.height(40.dp))
    PageText(title, body)
    if (status != null) {
      Spacer(Modifier.height(20.dp))
      Text(status, style = MaterialTheme.typography.labelLarge, color = colors.primary, textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun PageText(title: String, body: String) {
  Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
  Spacer(Modifier.height(14.dp))
  Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    repeat(count) { index ->
      val selected = index == current
      val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
      Box(
        Modifier.height(8.dp)
          .width(width)
          .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingPreview() {
  AVCBikeTheme(darkTheme = true) { OnboardingScreen(onFinished = {}) }
}

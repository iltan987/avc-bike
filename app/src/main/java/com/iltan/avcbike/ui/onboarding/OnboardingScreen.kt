package com.iltan.avcbike.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iltan.avcbike.R
import com.iltan.avcbike.theme.AVCBikeTheme
import com.iltan.avcbike.ui.ride.SpeedGauge
import com.iltan.avcbike.ui.ride.openAppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Page {
  WELCOME,
  LOCATION,
  NOTIFICATIONS,
}

private val pages = buildList {
  add(Page.WELCOME)
  add(Page.LOCATION)
  // Before Android 13 notifications don't need a runtime permission.
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Page.NOTIFICATIONS)
}

private data class Permissions(val location: Boolean, val notifications: Boolean)

private fun Context.currentPermissions() =
  Permissions(
    location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
  )

private fun Context.granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
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
        isLast -> stringResource(R.string.onboarding_lets_ride) to ::next
        else -> stringResource(R.string.onboarding_next) to ::next
      }
    Button(
      onClick = action,
      modifier = Modifier.fillMaxWidth().height(64.dp),
      shape = RoundedCornerShape(20.dp),
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
      Text(label.uppercase(), style = MaterialTheme.typography.titleMedium.copy(letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing))
    }
    // A way past each permission page without granting it; the ride screen asks again on Start.
    val showSkip = (page == Page.LOCATION && !permissions.location) || (page == Page.NOTIFICATIONS && !permissions.notifications)
    TextButton(onClick = ::next, enabled = showSkip, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
      Text(if (showSkip) stringResource(R.string.onboarding_not_now) else "")
    }
  }
}

@Composable
private fun WelcomePage() {
  // A looping demo of the idea: the bike slows to a stop, the music dips, then it pulls away.
  var demoStep by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(2_200)
      demoStep = (demoStep + 1) % 2
    }
  }
  val quiet = demoStep == 1
  val accent by animateColorAsState(if (quiet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, tween(600), label = "demo")

  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    SpeedGauge(
      speed = if (quiet) 0 else 48,
      unitLabel = stringResource(R.string.unit_kmh),
      volumeLevel = if (quiet) 0.32f else 0.8f,
      accent = accent,
      active = true,
      modifier = Modifier.fillMaxWidth(0.82f),
    )
    Spacer(Modifier.height(32.dp))
    PageText(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body))
  }
}

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

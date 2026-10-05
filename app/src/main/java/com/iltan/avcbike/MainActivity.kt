package com.iltan.avcbike

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iltan.avcbike.settings.SettingsRepository
import com.iltan.avcbike.settings.ThemeMode
import com.iltan.avcbike.theme.AVCBikeTheme

class MainActivity : ComponentActivity() {
  private var settingsLoaded = false

  override fun onCreate(savedInstanceState: Bundle?) {
    val splash = installSplashScreen()
    super.onCreate(savedInstanceState)
    // Hold the splash until the stored theme and onboarding state are known, so the first frame is
    // already in the rider's chosen theme.
    splash.setKeepOnScreenCondition { !settingsLoaded }

    val repository = SettingsRepository(this)
    setContent {
      val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
      val loaded = settings ?: return@setContent
      SideEffect { settingsLoaded = true }

      val darkTheme =
        when (loaded.themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }
      DisposableEffect(darkTheme) {
        // Status and navigation bar icons follow the app's theme, not just the system's.
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
      }
      AVCBikeTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(onboardingDone = loaded.onboardingDone, repository = repository)
        }
      }
    }
  }
}

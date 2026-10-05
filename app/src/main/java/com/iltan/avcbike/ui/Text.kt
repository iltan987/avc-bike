package com.iltan.avcbike.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat

/** Uppercase in the app's language, so Turkish "i" becomes "İ" rather than "I". */
@Composable
@ReadOnlyComposable
fun String.uppercaseLocalized(): String {
  val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
  return if (locale != null) uppercase(locale) else uppercase()
}

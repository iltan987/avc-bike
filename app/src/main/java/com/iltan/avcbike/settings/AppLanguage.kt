package com.iltan.avcbike.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language. SYSTEM follows the phone, falling back to English for languages the app
 * doesn't have. The choice is stored by AndroidX (and by the system on Android 13+), not in
 * [SettingsRepository], so it also shows up in the phone's per-app language settings.
 */
enum class AppLanguage(val tag: String?) {
  SYSTEM(null),
  ENGLISH("en"),
  TURKISH("tr");

  fun apply() {
    AppCompatDelegate.setApplicationLocales(if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
  }

  companion object {
    fun current(): AppLanguage {
      val locales = AppCompatDelegate.getApplicationLocales()
      if (locales.isEmpty) return SYSTEM
      return entries.find { it.tag == locales[0]?.language } ?: SYSTEM
    }

    /**
     * Before Android 13 the per-app language only reaches AppCompat activities; services such as
     * the ride notification need their context wrapped by hand.
     */
    fun wrap(base: Context): Context {
      val locales = AppCompatDelegate.getApplicationLocales()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || locales.isEmpty) return base
      val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(locales.toLanguageTags())) }
      return base.createConfigurationContext(config)
    }
  }
}

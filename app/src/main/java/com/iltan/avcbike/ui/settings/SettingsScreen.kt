package com.iltan.avcbike.ui.settings

import android.media.AudioManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iltan.avcbike.R
import com.iltan.avcbike.audio.SystemMusicVolume
import com.iltan.avcbike.ride.batteryExemptionIntent
import com.iltan.avcbike.ride.isIgnoringBatteryOptimizations
import com.iltan.avcbike.settings.AppLanguage
import com.iltan.avcbike.settings.Preset
import com.iltan.avcbike.settings.RideSettings
import com.iltan.avcbike.settings.SettingsRepository
import com.iltan.avcbike.settings.ThemeMode
import com.iltan.avcbike.settings.displaySpeed
import com.iltan.avcbike.settings.speedUnitLabel
import com.iltan.avcbike.theme.AVCBikeTheme
import com.iltan.avcbike.ui.uppercaseLocalized
import kotlin.math.roundToInt

private const val MIN_KMH = 5f
private const val MAX_KMH = 60f
private const val MIN_GAP_KMH = 3f

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = settingsViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var batteryUnrestricted by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
  // Re-check when coming back from the system dialog or battery settings.
  LifecycleResumeEffect(Unit) {
    batteryUnrestricted = context.isIgnoringBatteryOptimizations()
    onPauseOrDispose {}
  }
  SettingsScreen(
    state = state,
    batteryUnrestricted = batteryUnrestricted,
    onBack = onBack,
    onUpdate = viewModel::update,
    onPreset = viewModel::applyPreset,
    onPreviewQuiet = viewModel::previewQuietVolume,
    onAllowBattery = { context.startActivity(context.batteryExemptionIntent()) },
    modifier = modifier,
  )
}

@Composable
internal fun SettingsScreen(
  state: SettingsUiState,
  batteryUnrestricted: Boolean,
  onBack: () -> Unit,
  onUpdate: ((RideSettings) -> RideSettings) -> Unit,
  onPreset: (Preset) -> Unit,
  onPreviewQuiet: () -> Unit,
  onAllowBattery: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val settings = state.settings
  val unit = stringResource(speedUnitLabel(settings.useMph))
  fun speed(kmh: Float) = displaySpeed(kmh, settings.useMph)

  Column(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
      Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
      SectionHeader(stringResource(R.string.section_riding_style))
      PresetRow(settings = settings, unit = unit, onPreset = onPreset, speed = ::speed)

      SectionHeader(stringResource(R.string.section_speeds))
      SettingCard {
        SpeedRangeSetting(settings = settings, unit = unit, speed = ::speed, onUpdate = onUpdate)
      }

      SectionHeader(stringResource(R.string.section_volume))
      SettingCard {
        SliderSetting(
          title = stringResource(R.string.quiet_volume),
          value = settings.quietVolumePercent.toFloat(),
          range = 0f..90f,
          steps = 17,
          label = { stringResource(R.string.quiet_volume_value, it.roundToInt()) },
          onChange = { v -> onUpdate { it.copy(quietVolumePercent = v.roundToInt()) } },
        )
        FilledTonalButton(onClick = onPreviewQuiet, enabled = !state.previewing && !state.rideActive, modifier = Modifier.padding(top = 4.dp)) {
          Text(stringResource(if (state.previewing) R.string.try_quiet_playing else R.string.try_quiet))
        }
        if (state.rideActive) Hint(stringResource(R.string.try_quiet_disabled))
        Spacer(Modifier.height(20.dp))
        SliderSetting(
          title = stringResource(R.string.fade_time),
          value = settings.fadeMs / 1000f,
          range = 0f..4f,
          steps = 7,
          label = { if (it == 0f) stringResource(R.string.instant) else stringResource(R.string.seconds_value, formatSeconds(it)) },
          onChange = { v -> onUpdate { it.copy(fadeMs = (v * 1000).roundToInt()) } },
        )
      }

      SectionHeader(stringResource(R.string.section_timing))
      SettingCard {
        SliderSetting(
          title = stringResource(R.string.quiet_delay),
          value = settings.quietDelaySec,
          range = 0f..10f,
          steps = 9,
          label = { if (it == 0f) stringResource(R.string.instant) else stringResource(R.string.seconds_value, formatSeconds(it)) },
          onChange = { v -> onUpdate { it.copy(quietDelaySec = v) } },
        )
        Hint(stringResource(R.string.quiet_delay_hint))
        Spacer(Modifier.height(20.dp))
        SliderSetting(
          title = stringResource(R.string.resume_delay),
          value = settings.resumeDelaySec,
          range = 0f..5f,
          steps = 9,
          label = { if (it == 0f) stringResource(R.string.instant) else stringResource(R.string.seconds_value, formatSeconds(it)) },
          onChange = { v -> onUpdate { it.copy(resumeDelaySec = v) } },
        )
      }

      SectionHeader(stringResource(R.string.section_display))
      SettingCard {
        // Stored by AndroidX rather than DataStore; changing it recreates the screen in the new language.
        var language by remember { mutableStateOf(AppLanguage.current()) }
        ChoiceSetting(
          title = stringResource(R.string.language),
          options = AppLanguage.entries,
          selected = language,
          label = { stringResource(languageLabel(it)) },
          onSelect = {
            language = it
            it.apply()
          },
        )
        Spacer(Modifier.height(20.dp))
        ChoiceSetting(
          title = stringResource(R.string.theme),
          options = ThemeMode.entries,
          selected = settings.themeMode,
          label = { stringResource(themeLabel(it)) },
          onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } },
        )
        Spacer(Modifier.height(20.dp))
        ChoiceSetting(
          title = stringResource(R.string.units),
          options = listOf(false, true),
          selected = settings.useMph,
          label = { stringResource(speedUnitLabel(it)) },
          onSelect = { mph -> onUpdate { it.copy(useMph = mph) } },
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.titleSmall)
            Hint(stringResource(R.string.keep_screen_on_hint))
          }
          Switch(checked = settings.keepScreenOn, onCheckedChange = { on -> onUpdate { it.copy(keepScreenOn = on) } })
        }
      }

      SectionHeader(stringResource(R.string.section_background))
      SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.battery_setting_title), style = MaterialTheme.typography.titleSmall)
            Hint(stringResource(if (batteryUnrestricted) R.string.battery_setting_on else R.string.battery_setting_off))
          }
          if (!batteryUnrestricted) {
            FilledTonalButton(onClick = onAllowBattery, modifier = Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.battery_setting_action)) }
          }
        }
      }

      Text(
        stringResource(R.string.privacy_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp),
      )
    }
  }
}

@Composable
private fun PresetRow(settings: RideSettings, unit: String, onPreset: (Preset) -> Unit, speed: (Float) -> Int) {
  val selected = Preset.of(settings)
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Preset.entries.forEach { preset ->
      PresetCard(
        title = stringResource(if (preset == Preset.CITY) R.string.preset_city else R.string.preset_touring),
        subtitle = stringResource(R.string.preset_summary, speed(preset.quietBelowKmh), speed(preset.resumeAboveKmh)),
        unit = unit,
        selected = selected == preset,
        onClick = { onPreset(preset) },
        modifier = Modifier.weight(1f),
      )
    }
    PresetCard(
      title = stringResource(R.string.preset_custom),
      subtitle =
        if (selected == null) stringResource(R.string.preset_summary, speed(settings.quietBelowKmh), speed(settings.resumeAboveKmh))
        else stringResource(R.string.preset_custom_hint),
      unit = if (selected == null) unit else null,
      selected = selected == null,
      onClick = null,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun PresetCard(title: String, subtitle: String, unit: String?, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(18.dp)
  val border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
  val container = if (selected) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainer
  val content: @Composable ColumnScope.() -> Unit = {
    Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) colors.primary else colors.onSurface)
    Spacer(Modifier.height(4.dp))
    // Numbers and unit on separate lines, so a narrow card never splits "km/sa" in two.
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
    if (unit != null) Text(unit, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
  }
  if (onClick != null) {
    Surface(onClick = onClick, modifier = modifier.fillMaxHeight(), shape = shape, color = container, border = border) {
      Column(Modifier.padding(14.dp), content = content)
    }
  } else {
    Surface(modifier = modifier.fillMaxHeight(), shape = shape, color = container, border = border) { Column(Modifier.padding(14.dp), content = content) }
  }
}

@Composable
private fun <T> ChoiceSetting(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
  Text(title, style = MaterialTheme.typography.titleSmall)
  Spacer(Modifier.height(10.dp))
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, option ->
      SegmentedButton(selected = option == selected, onClick = { onSelect(option) }, shape = SegmentedButtonDefaults.itemShape(index, options.size)) {
        Text(label(option), maxLines = 1)
      }
    }
  }
}

private fun languageLabel(language: AppLanguage) =
  when (language) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.TURKISH -> R.string.language_turkish
  }

private fun themeLabel(mode: ThemeMode) =
  when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
  }

@Composable
private fun SpeedRangeSetting(settings: RideSettings, unit: String, speed: (Float) -> Int, onUpdate: ((RideSettings) -> RideSettings) -> Unit) {
  // Local state while dragging; saved when the finger lifts.
  var range by remember(settings.quietBelowKmh, settings.resumeAboveKmh) { mutableStateOf(settings.quietBelowKmh..settings.resumeAboveKmh) }
  Row(verticalAlignment = Alignment.Bottom) {
    LabeledValue(stringResource(R.string.quiet_below), "${speed(range.start)} $unit", Modifier.weight(1f))
    LabeledValue(stringResource(R.string.back_above), "${speed(range.endInclusive)} $unit", Modifier.weight(1f), alignEnd = true)
  }
  RangeSlider(
    value = range,
    onValueChange = { new ->
      val start = new.start.roundToInt().toFloat()
      val end = new.endInclusive.roundToInt().toFloat()
      if (end - start >= MIN_GAP_KMH) range = start..end
    },
    valueRange = MIN_KMH..MAX_KMH,
    onValueChangeFinished = { onUpdate { it.copy(quietBelowKmh = range.start, resumeAboveKmh = range.endInclusive) } },
  )
  Hint(stringResource(R.string.speed_range_hint))
}

@Composable
private fun SliderSetting(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  steps: Int,
  label: @Composable (Float) -> String,
  onChange: (Float) -> Unit,
) {
  var current by remember(value) { mutableFloatStateOf(value) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    Text(label(current), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
  }
  Slider(value = current, onValueChange = { current = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(current) })
}

@Composable
private fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
  Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
    Text(label.uppercaseLocalized(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.headlineMedium)
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text.uppercaseLocalized(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp),
  )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
  Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp), content = content)
  }
}

@Composable
private fun Hint(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
}

/** "2" rather than "2.0", but keeps "1.5". */
private fun formatSeconds(seconds: Float): String = if (seconds % 1f == 0f) seconds.toInt().toString() else "%.1f".format(seconds)

@Composable
private fun settingsViewModel(): SettingsViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { SettingsViewModel(SettingsRepository(context), SystemMusicVolume(context.getSystemService(AudioManager::class.java))) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 380, heightDp = 1400)
@Composable
private fun SettingsScreenPreview() {
  AVCBikeTheme(darkTheme = true) { SettingsScreen(SettingsUiState(), false, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1400)
@Composable
private fun SettingsScreenLightPreview() {
  AVCBikeTheme(darkTheme = false) { SettingsScreen(SettingsUiState(settings = RideSettings(quietBelowKmh = 12f, useMph = true)), true, {}, {}, {}, {}, {}) }
}

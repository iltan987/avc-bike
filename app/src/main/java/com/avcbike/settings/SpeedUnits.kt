package com.avcbike.settings

import androidx.annotation.StringRes
import com.avcbike.R
import kotlin.math.roundToInt

private const val KM_PER_MILE = 1.609344f

/** Converts a speed stored in km/h to the rider's display unit, rounded to a whole number. */
fun displaySpeed(kmh: Float, useMph: Boolean): Int = (if (useMph) kmh / KM_PER_MILE else kmh).roundToInt()

@StringRes fun speedUnitLabel(useMph: Boolean): Int = if (useMph) R.string.unit_mph else R.string.unit_kmh

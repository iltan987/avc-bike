package com.iltan.avcbike.audio

import android.content.Context
import androidx.core.content.edit

/** The rider's normal volume and the quiet level we turned it down to. */
data class SavedQuiet(val normal: Int, val quiet: Int)

/**
 * Keeps [SavedQuiet] while the music is turned down. If the app is killed before it can turn the
 * music back up (force stop, the phone ending it, a crash), the next start can still restore it.
 */
interface QuietMemory {
  var saved: SavedQuiet?

  /** Remembers nothing. */
  object None : QuietMemory {
    override var saved: SavedQuiet?
      get() = null
      set(_) {}
  }
}

/** Written synchronously: the point is to survive the process dying right after. */
class PrefsQuietMemory(context: Context) : QuietMemory {
  private val prefs = context.applicationContext.getSharedPreferences("quiet_memory", Context.MODE_PRIVATE)

  override var saved: SavedQuiet?
    get() {
      val normal = prefs.getInt(KEY_NORMAL, -1)
      val quiet = prefs.getInt(KEY_QUIET, -1)
      return if (normal < 0 || quiet < 0) null else SavedQuiet(normal, quiet)
    }
    set(value) {
      prefs.edit(commit = true) {
        if (value == null) clear() else putInt(KEY_NORMAL, value.normal).putInt(KEY_QUIET, value.quiet)
      }
    }

  private companion object {
    const val KEY_NORMAL = "normal"
    const val KEY_QUIET = "quiet"
  }
}

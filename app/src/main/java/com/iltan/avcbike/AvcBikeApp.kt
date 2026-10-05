package com.iltan.avcbike

import android.app.Application
import android.media.AudioManager
import com.iltan.avcbike.audio.PrefsQuietMemory
import com.iltan.avcbike.audio.SystemMusicVolume
import com.iltan.avcbike.audio.VolumeController

class AvcBikeApp : Application() {
  override fun onCreate() {
    super.onCreate()
    // A fresh process means no ride or volume preview is running, so a saved quiet level is left
    // over from the app being killed while the music was down. Turn it back up first; otherwise
    // the next ride would take the quiet level as normal and turn it down further.
    VolumeController.recover(SystemMusicVolume(getSystemService(AudioManager::class.java)), PrefsQuietMemory(this))
  }
}

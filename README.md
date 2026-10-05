# AVC Bike

Android app for motorcyclists that turns the music down when you slow down or stop, and back up as
you pull away. It works with any music app and with helmet intercoms. English and Turkish.

## Build and run (debug)

```bash
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # speed logic and volume fade tests
./gradlew lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds show a speed simulator on the ride screen, so you can test at a desk.

## Release builds for sharing

Release builds are shrunk with R8 and signed with your own release key. Every update must be signed
with the **same** key, or friends can't install it over their current version. Keep the key file and
its password backed up somewhere safe, such as a password manager or private cloud storage. Never
commit them.

1. Create the key once (pick your own password when asked):

   ```bash
   mkdir -p ~/keys
   keytool -genkeypair -v -keystore ~/keys/avcbike-release.jks -alias avcbike \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Tell Gradle where it is. Add these lines to `~/.gradle/gradle.properties`, which is outside
   this repo:

   ```properties
   avcbike.release.storeFile=/home/<you>/keys/avcbike-release.jks
   avcbike.release.storePassword=<your password>
   avcbike.release.keyAlias=avcbike
   avcbike.release.keyPassword=<your password>
   ```

3. Build: `./gradlew assembleRelease` produces `app/build/outputs/apk/release/app-release.apk`.

Before each new release, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.

## Sharing with friends

Send `app-release.apk` (Google Drive, Telegram, WhatsApp, …). When they open it, Android asks them
to allow installing apps from that source.

Android developer verification: from 2026-09-30, certified devices in Brazil, Indonesia, Singapore
and Thailand only install apps from verified developers, and this expands worldwide in 2027. A free
*limited distribution* account in the Android Developer Console covers up to 20 devices. Register
the package name `com.iltan.avcbike` and your release key there before it reaches your friends'
countries.

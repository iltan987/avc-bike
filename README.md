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

## Release builds

Release builds are shrunk with R8 and signed with your own **upload key**. Google Play re-signs the
app with its own app signing key (Play App Signing), so the upload key never reaches users, but every
upload must be signed with it. Keep the key file and its password backed up somewhere safe, such as
a password manager or private cloud storage. Never commit them.

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

3. Build:
   - `./gradlew bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab` for
     Google Play.
   - `./gradlew assembleRelease` produces `app/build/outputs/apk/release/app-release.apk` for
     testing a release build on your own phone.

## Publishing on Google Play

Before each upload, raise `versionCode` (Play rejects a repeated one) and `versionName` in
`app/build.gradle.kts`. Then upload `app-release.aab` in the Play Console.

- `store/listing.md`: store listing text in English and Turkish.
- `store/*.png`: icon and feature graphics.
- `store/screenshots/`: phone screenshots.
- `store/play-console.md`: answers for the App content forms (data safety, content rating,
  foreground service declaration) and the testing steps for a new developer account.
- `PRIVACY.md`: the privacy policy linked from the store listing.

If the app changes what it does with data or which permissions it uses, update `PRIVACY.md`,
`store/play-console.md` and the matching Play Console forms in the same release.

An APK you build yourself is signed with the upload key, and a Play install is signed with Google's
app signing key. Android won't install one over the other, so uninstall first when switching (this
wipes the app's settings).

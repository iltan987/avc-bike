# Play Console answers

Answers for the Play Console's setup and **Policy → App content** forms, so they stay the same
across updates. If the app changes (for example new permissions, network access or analytics),
update this file and the forms together.

## App setup

- App or game: **App**. Free or paid: **Free**. Default language: **English (United States)**
- Category: **Auto & Vehicles**
- Tags (optional): Motorcycle, Music, Navigation
- Contact email: iltancanerr@gmail.com
- Privacy policy: https://github.com/iltan987/avc-bike/blob/main/PRIVACY.md

## App signing

- Let Google generate and keep the **app signing key** (Play App Signing).
- `~/keys/avcbike-release.jks` is the **upload key**. Every `.aab` you upload must be signed with it.
  If it's lost, Play Console can reset the upload key, but that takes support time. Keep it backed up.

## App content

| Form | Answer |
| --- | --- |
| Privacy policy | URL above |
| Ads | No, the app doesn't contain ads |
| App access | All functionality is available without special access (no login) |
| Content rating | Category **All other app types**. Answer **No** to everything (no violence, sexuality, language, controlled substances, gambling, user interaction or sharing, no location sharing with other users, no purchases). |
| Target audience | **18 and over** only. The app isn't designed for children, so the Families policy doesn't apply. |
| News app | No |
| Health apps | None of the health features apply |
| Financial features | None |
| Government app | No |
| Data safety | See below |
| Foreground service permissions | See below |
| Advertising ID | No, the app doesn't use the advertising ID |

## Data safety

- Does your app collect or share any of the required user data types? **No.**
  - Location is read only during a ride, processed on the device, and never leaves it, so under Play's
    definitions it isn't "collected". The app has no INTERNET permission.
  - Settings stay on the device. Android's own backup is handled by the system, not by the app.
- Is all user data encrypted in transit? Not applicable, because nothing is transmitted.
- Do you provide a way for users to request that their data be deleted? Not applicable, because no
  data is collected. Uninstalling removes everything.

## Foreground service permissions

`FOREGROUND_SERVICE_LOCATION` needs a declaration.

- Type: **Location**
- Task: "User-initiated ride tracking: while a ride the user started is running, GPS speed is read
  every second to turn the music volume down when the rider slows or stops, and back up when they
  speed up. The ride runs with the screen off and is shown in a notification with a Stop button."
- Why it can't be deferred or interrupted: the volume has to follow the rider's speed in real time
  for the whole ride.
- Video: an unlisted YouTube link showing a ride being started in the app, the ride notification,
  the music turning down when slow, and the ride being stopped from the notification.

## Background location

Not requested. The app has no `ACCESS_BACKGROUND_LOCATION`, so no declaration is needed.

## Testing track (new personal developer account)

1. **Internal testing:** upload `app-release.aab`, add your own Google account, install from the
   Play link and check the pre-launch report.
2. **Closed testing:** create a track, add at least **12 testers** (a Google Group or an email
   list), and share the opt-in link. They must stay opted in for **14 days in a row**.
3. After 14 days: **Dashboard → Apply for production**. Answer the questions about the test (how
   testers were recruited, feedback received, what changed).

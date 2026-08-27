# AmpBubble

AmpBubble is an Android overlay app that lets you rate the currently playing Plexamp track without leaving what you are doing. It provides a fully opaque floating bubble, an expandable now-playing panel, quick star rating, queued retry for failed ratings, and settings for reliability and UX.

## VirusTotal Readout

[![VirusTotal report](https://img.shields.io/badge/VirusTotal-view%20report-394EFF?logo=virustotal&logoColor=white)](https://www.virustotal.com/gui/file/940A90CE0C7D42173ABB8036695CDA7AA6A6CDC1A8768441CCDDEFFAF922838C/detection)

| Scan target | SHA-256 | Artifact |
| --- | --- | --- |
| Production release | `940A90CE0C7D42173ABB8036695CDA7AA6A6CDC1A8768441CCDDEFFAF922838C` | `app-release.apk` |

The badge opens VirusTotal's vendor-by-vendor detection report for this exact APK. Scan results apply only to this signed production artifact; future builds have different hashes.

To verify the hash locally on Windows:

```powershell
Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

### Production signing

Release builds use a local `keystore.properties` file and never store signing credentials in Git. Create a production keystore once, then create `keystore.properties` in the project root with these values:

```properties
storeFile=ampbubble-release.jks
storePassword=your-keystore-password
keyAlias=ampbubble
keyPassword=your-key-password
```

Keep both `ampbubble-release.jks` and `keystore.properties` backed up securely. Build the signed release APK with:

```powershell
.\gradlew.bat assembleRelease
```

## What It Does

- Shows an always-on-top floating bubble; tap it again to close the expanded panel.
- Expands into a panel with a large hero-style square album art tile and Plexamp metadata.
- Lets you rate tracks with drag-friendly half stars and one haptic tick per selected or crossed half-star step.
- Sends ratings to Plex Media Server using the Plex rating API.
- Caches ratings locally and retries failed submissions later.
- Keeps recent ratings in local history and can show the latest three directly at the bottom of the panel.
- Shows a compact, read-only playback progress bar with time labels when room allows.
- Resolves the active Plex track and rating after each Plexamp track change, even when the panel is closed.
- Supports manual server override for URL and token.
- Uses secure encrypted token storage for Plex auth tokens.

## Current Playback Behavior

- Album art loads instantly from the Plexamp notification's embedded artwork, falling back to the Plex thumb URL only when no embedded art is available.
- The panel shows a read-only progress bar below now-playing information; it advances during playback and freezes when paused.
- The panel opens above the bubble when space below is insufficient, avoiding a visible position jump near the bottom edge of the screen.
- If the positioned panel would still cover the bubble, the bubble slides just clear of the panel and returns to its original spot once the panel closes.
- A brief "Rating saved" confirmation flashes next to Quick presets after a successful rating, then disappears.

## Tech Stack

- Kotlin
- Jetpack Compose
- Lifecycle Service + NotificationListenerService
- DataStore Preferences
- EncryptedSharedPreferences via AndroidX Security
- OkHttp
- Coil

## Requirements

- Android 11 or newer (minSdk 30)
- A Plex account
- Plexamp installed and actively playing media
- Reachable Plex Media Server

## Permissions Used

- Display over other apps: required for the floating bubble.
- Notification access: required to read Plexamp metadata and playback state.
- Foreground service + notifications: required to keep overlay service alive.
- Vibration: used for half-star rating feedback.
- Internet/network state: required for Plex API calls.

See manifest details in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml).

## Project Layout

- App entry and setup UI: [app/src/main/java/com/ampbubble/app/MainActivity.kt](app/src/main/java/com/ampbubble/app/MainActivity.kt)
- Overlay service and runtime state: [app/src/main/java/com/ampbubble/app/overlay/BubbleOverlayService.kt](app/src/main/java/com/ampbubble/app/overlay/BubbleOverlayService.kt)
- Overlay Compose UI: [app/src/main/java/com/ampbubble/app/overlay/BubbleView.kt](app/src/main/java/com/ampbubble/app/overlay/BubbleView.kt)
- Star interaction control: [app/src/main/java/com/ampbubble/app/overlay/StarRatingControl.kt](app/src/main/java/com/ampbubble/app/overlay/StarRatingControl.kt)
- Plexamp notification bridge: [app/src/main/java/com/ampbubble/app/notification/PlexampNotificationListener.kt](app/src/main/java/com/ampbubble/app/notification/PlexampNotificationListener.kt)
- Plex auth flow: [app/src/main/java/com/ampbubble/app/plex/PlexAuthRepository.kt](app/src/main/java/com/ampbubble/app/plex/PlexAuthRepository.kt)
- Plex server discovery: [app/src/main/java/com/ampbubble/app/plex/PlexServerRepository.kt](app/src/main/java/com/ampbubble/app/plex/PlexServerRepository.kt)
- Active session matching: [app/src/main/java/com/ampbubble/app/plex/NowPlayingRepository.kt](app/src/main/java/com/ampbubble/app/plex/NowPlayingRepository.kt)
- Rating API writes: [app/src/main/java/com/ampbubble/app/plex/PlexRatingRepository.kt](app/src/main/java/com/ampbubble/app/plex/PlexRatingRepository.kt)
- Persisted settings and secure token store: [app/src/main/java/com/ampbubble/app/data/SettingsStore.kt](app/src/main/java/com/ampbubble/app/data/SettingsStore.kt)
- App module build config: [app/build.gradle.kts](app/build.gradle.kts)

## Build and Run

1. Open the project in Android Studio.
2. Sync Gradle.
3. Connect an Android device or start an emulator (API 30+).
4. Run the app module.

Toolchain details:

- Java target: 17
- compileSdk: 35
- targetSdk: 35

See [app/build.gradle.kts](app/build.gradle.kts).

### Build a sideloadable APK on Windows

Run this from the project root after Java 17 is available to Gradle:

```powershell
.\gradlew.bat assembleDebug
```

The debug-signed APK is currently created at `app\build\intermediates\apk\debug\app-debug.apk`. Copy it to an Android 11+ phone and allow the file manager or browser to install unknown apps when Android prompts you.

## First-Time Setup

1. Launch the app.
2. Grant overlay permission.
3. Grant notification listener access.
4. Sign in with Plex.
5. Let the app discover your server automatically.
6. If discovery fails, use Manual server override with base URL and token.
7. Enable Bubble.

If overlay permission is missing when Bubble is enabled, the app opens Android's per-app overlay permission screen instead of starting the bubble.

## User Workflow

1. Start playback in Plexamp.
2. Tap the floating bubble to open the panel.
3. Confirm track metadata and playback status.
4. Drag or tap stars to rate; each half-star step produces one haptic tick.
5. Optional: use preset ratings.
6. Enable `Show recent rating` in the app to display recent ratings at the bottom of the panel.

## Reliability and Data Behavior

- Rating writes go to Plex on a 0 to 10 scale (mapped from 0 to 5 stars).
- Failed rating submissions are added to a pending queue.
- Pending queue is retried when service conditions allow.
- Recent ratings are kept in local history.
- Whole-number ratings display without a decimal, such as `5/5`.
- Basic diagnostic events are stored for troubleshooting.

## Playback and Metadata Model

The app distinguishes between playing, paused, and stopped using MediaController playback state from Plexamp notification sessions.

- Paused retains metadata in the panel.
- Stopped clears active track state.
- No metadata yields waiting/idle behavior.

## Troubleshooting

### Bubble does not appear

- Verify overlay permission is granted.
- Verify Bubble is enabled in the app.
- Confirm foreground service notification is present.
- Enabling Bubble without overlay permission should open Android's `Display over other apps` setting for AmpBubble.

### Track info does not update

- Verify notification listener access is granted.
- Confirm Plexamp is producing active media notifications.
- Wait briefly after a track change for the background Plex session match to complete.

### Ratings do not save

- Verify token is valid.
- Verify server base URL is reachable.
- Check manual override settings if auto-discovery picked the wrong endpoint.
- Failed writes should queue and retry; check diagnostics in app.

### Signed in but server not found

- Your account token may be valid but reachable connection selection failed.
- Use manual override URL and token.

## Security and Privacy

- Plex auth token is stored using encrypted shared preferences.
- Non-secret app settings are stored in DataStore.
- The app does not require media file access.
- The app communicates with Plex endpoints you configure or discover.

## Development Notes

- Root project name is AmpBubble and module is app.
- See [settings.gradle.kts](settings.gradle.kts) for Gradle repository and module setup.
- Notification package matching for Plex/Plexamp clients is handled in the listener companion object.

## Known Limitations

- Behavior depends on Plexamp session metadata availability from Android media notifications.
- Session matching can fail for ambiguous metadata.
- Network reliability impacts immediate rating writes.
- Recent-ratings thumbnails still rely on the network Plex thumb URL (not the embedded notification bitmap), so they can lag or fail independently of the main now-playing art.

## Recent Changes

- Rebranded from PlexBubble to AmpBubble for public release: new package `com.ampbubble.app`, new app name/theme/deep-link scheme, and removal of Plex-trademarked logo assets in favor of an original placeholder mark (swap in final brand art later).
- Album art reliability fix: the now-playing panel prefers the bitmap embedded in Plexamp's media notification (instant, no network round trip) and only falls back to the Plex thumb URL when no embedded art is present.
- Now-playing panel redesign: hero-style full-width square album art with title/artist/album centered below it, softer shadow/gradient card styling instead of hard borders, removed the redundant "Playing now/Paused" text and the numeric star rating label, and removed the panel's close button (tap the bubble to close instead).
- "Saved to Plex" confirmation reworded to "Rating saved" and moved next to Quick presets, right-aligned, matching its font size and auto-dismissing after a few seconds.

## Suggested Next Improvements

- Add screenshot section for setup and panel states.
- Add unit tests for session matching and preset parsing.
- Add instrumentation tests for permission and service lifecycle flows.
- Add explicit in-app diagnostics screen export action.

## License

No license file is currently included in this repository.
Add one if you intend to distribute or accept external contributions.

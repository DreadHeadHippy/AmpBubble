# PlexBubble

PlexBubble is an Android overlay app that lets you rate the currently playing Plexamp track without leaving what you are doing. It provides a floating bubble, an expandable now-playing panel, quick star rating, queued retry for failed ratings, and settings for reliability and UX.

## What It Does

- Shows an always-on-top floating bubble.
- Expands into a panel with now-playing information from Plexamp notifications.
- Lets you rate tracks with drag-friendly stars.
- Sends ratings to Plex Media Server using the Plex rating API.
- Caches ratings locally and retries failed submissions later.
- Keeps recent ratings and optional recent history in the panel.
- Supports manual server override for URL and token.
- Uses secure encrypted token storage for Plex auth tokens.

## Current Playback Behavior

- Playing state shows: Playing now.
- Paused state shows: Paused.
- Stopped/no active session shows: Nothing playing.
- Long-press stop behavior was removed to match requested behavior and avoid non-Plexamp-like side effects.

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
- Internet/network state: required for Plex API calls.

See manifest details in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml).

## Project Layout

- App entry and setup UI: [app/src/main/java/com/plexbubble/app/MainActivity.kt](app/src/main/java/com/plexbubble/app/MainActivity.kt)
- Overlay service and runtime state: [app/src/main/java/com/plexbubble/app/overlay/BubbleOverlayService.kt](app/src/main/java/com/plexbubble/app/overlay/BubbleOverlayService.kt)
- Overlay Compose UI: [app/src/main/java/com/plexbubble/app/overlay/BubbleView.kt](app/src/main/java/com/plexbubble/app/overlay/BubbleView.kt)
- Star interaction control: [app/src/main/java/com/plexbubble/app/overlay/StarRatingControl.kt](app/src/main/java/com/plexbubble/app/overlay/StarRatingControl.kt)
- Plexamp notification bridge: [app/src/main/java/com/plexbubble/app/notification/PlexampNotificationListener.kt](app/src/main/java/com/plexbubble/app/notification/PlexampNotificationListener.kt)
- Plex auth flow: [app/src/main/java/com/plexbubble/app/plex/PlexAuthRepository.kt](app/src/main/java/com/plexbubble/app/plex/PlexAuthRepository.kt)
- Plex server discovery: [app/src/main/java/com/plexbubble/app/plex/PlexServerRepository.kt](app/src/main/java/com/plexbubble/app/plex/PlexServerRepository.kt)
- Active session matching: [app/src/main/java/com/plexbubble/app/plex/NowPlayingRepository.kt](app/src/main/java/com/plexbubble/app/plex/NowPlayingRepository.kt)
- Rating API writes: [app/src/main/java/com/plexbubble/app/plex/PlexRatingRepository.kt](app/src/main/java/com/plexbubble/app/plex/PlexRatingRepository.kt)
- Persisted settings and secure token store: [app/src/main/java/com/plexbubble/app/data/SettingsStore.kt](app/src/main/java/com/plexbubble/app/data/SettingsStore.kt)
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

## First-Time Setup

1. Launch the app.
2. Grant overlay permission.
3. Grant notification listener access.
4. Sign in with Plex.
5. Let the app discover your server automatically.
6. If discovery fails, use Manual server override with base URL and token.
7. Enable Bubble.

## User Workflow

1. Start playback in Plexamp.
2. Tap the floating bubble to open the panel.
3. Confirm track metadata and playback status.
4. Drag or tap stars to rate.
5. Optional: use preset ratings.
6. Optional: undo recent rating within the short undo window.

## Reliability and Data Behavior

- Rating writes go to Plex on a 0 to 10 scale (mapped from 0 to 5 stars).
- Failed rating submissions are added to a pending queue.
- Pending queue is retried when service conditions allow.
- Recent ratings are kept in local history.
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

### Track info does not update

- Verify notification listener access is granted.
- Confirm Plexamp is producing active media notifications.
- Open the panel while playback is active to force matching refresh.

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

- Root project name is PlexBubble and module is app.
- See [settings.gradle.kts](settings.gradle.kts) for Gradle repository and module setup.
- Notification package matching for Plex/Plexamp clients is handled in the listener companion object.

## Known Limitations

- Behavior depends on Plexamp session metadata availability from Android media notifications.
- Session matching can fail for ambiguous metadata.
- Network reliability impacts immediate rating writes.

## Suggested Next Improvements

- Add screenshot section for setup and panel states.
- Add unit tests for session matching and preset parsing.
- Add instrumentation tests for permission and service lifecycle flows.
- Add explicit in-app diagnostics screen export action.

## License

No license file is currently included in this repository.
Add one if you intend to distribute or accept external contributions.

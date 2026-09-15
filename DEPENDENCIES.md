# Dependency Licenses

This inventory covers the direct dependencies declared in `app/build.gradle.kts`. Transitive dependencies should be reviewed before each public release if the dependency graph changes.

## Runtime Dependencies

The following runtime libraries are packaged into the application or used by its runtime build and are distributed under Apache License 2.0:

- AndroidX Core KTX 1.13.1
- AndroidX Lifecycle Runtime, Service, and ViewModel KTX 2.8.4
- AndroidX SavedState KTX 1.2.1
- AndroidX Activity Compose 1.9.1
- Jetpack Compose BOM 2024.09.00 and Compose UI, Material 3, Material Icons Extended, and tooling preview
- AndroidX Browser 1.8.0
- AndroidX DataStore Preferences 1.1.1
- AndroidX Security Crypto 1.1.0-alpha06
- OkHttp 4.12.0
- Coil Compose 2.7.0
- Kotlin Coroutines Android 1.8.1

Apache License 2.0 permits redistribution in source and binary form, subject to preserving the license and notice terms. These libraries may bring additional transitive dependencies with their own notices.

## Build and Test Dependencies

These dependencies are not packaged into the release APK:

- JUnit 4.13.2: Eclipse Public License 1.0
- `com.vaadin.external.google:android-json:0.0.20131108.vaadin1`: Apache License 2.0; used only to provide a concrete `org.json` implementation for local JVM tests

## Distribution Notes

- The app's own source is licensed under the MIT License; see [LICENSE](LICENSE).
- This file is a practical direct-dependency inventory, not a substitute for shipping the complete notices required by a final app store distribution process.
- Before publishing a release, generate or inspect the resolved dependency graph and include any required third-party notices in the release materials.
- Plex, Plexamp, and related marks belong to their respective owners. AmpBubble is independent and is not affiliated with Plex or Plexamp.

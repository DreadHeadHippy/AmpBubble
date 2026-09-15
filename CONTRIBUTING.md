# Contributing to AmpBubble

Thanks for helping improve AmpBubble.

## Before You Start

- Check existing issues and pull requests before opening a new one.
- For security issues, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
- Do not include Plex tokens, passwords, private server URLs, library data, crash dumps, keystores, or signing properties in commits.

## Development Setup

1. Install Android Studio and a JDK 17 runtime.
2. Clone the repository and open it in Android Studio.
3. Grant overlay and notification-listener permissions on a test device or emulator when testing playback behavior.
4. Copy [keystore.properties.example](keystore.properties.example) only if you need local release signing. Debug development does not require a release keystore.

## Before Opening a Pull Request

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Also run `git diff --check` and review the complete diff. Keep changes focused and update the README when user-visible behavior changes.

## Pull Requests

- Explain the user-visible problem and the chosen solution.
- Include focused tests for parsing, state changes, or other behavior that can be tested without a device.
- Describe any device-only verification that was performed.
- Avoid unrelated formatting or dependency upgrades.
- Do not commit generated build output.

## Commit Guidance

Use concise, descriptive commit messages, such as:

```text
Fix FLAC quality metadata parsing
```

## Code Style

Follow the existing Kotlin style and Android architecture. Prefer small changes, existing helpers, and nullable fields when Plex may omit metadata.

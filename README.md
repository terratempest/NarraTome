# NarraTome

An Android client for a user-managed Audiobookshelf server. Browse your library, stream or download audiobooks, and synchronize listening progress. Includes bookmarks, sleep controls, background playback, and Android Auto integration.

## Install

Download the signed APK from this repository's GitHub Releases page and open it on Android 7.0 or later. Android may ask you to allow installation from your browser or file manager. Future updates are installed manually from Releases; use APKs signed by the same maintainer key to preserve app data.

NarraTome uses application ID `com.narratome`.  

## Build

Use JDK 17 and an installed Android SDK with platform 36. Create an untracked `local.properties` containing `sdk.dir=/path/to/Android/Sdk` (on Windows, use forward slashes). Android Studio can configure this file too.

From the repository root:

```powershell
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
.\gradlew lintDebug
```

On Linux/macOS, use `bash ./gradlew` instead of `.\gradlew`. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Release instructions are in [docs/RELEASE.md](docs/RELEASE.md).

## Project

One Android module (`app`), using Kotlin, Compose, Hilt, Room, Media3, and WorkManager. Packages under `app/src/main/java/com/narratome/` separate presentation, domain, data, playback, synchronization, and dependency wiring. Room schema history and regression tests are included.

Read [SECURITY.md](SECURITY.md) before reporting vulnerabilities. The app includes an offline [privacy policy](app/src/main/res/raw/privacy_policy.txt). HTTP servers are supported; use HTTPS on untrusted networks. Do not attach credentials, personal server details, or private audio to issues.

## License

[MIT](LICENSE). Dependency licenses remain with their respective authors. NarraTome is an independent client, not an official Audiobookshelf project.

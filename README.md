# NarraTome

An Android client for a user-managed Audiobookshelf server. Browse your library, stream or download audiobooks, and synchronize listening progress. Includes bookmarks, sleep controls, background playback, and Android Auto integration.

<img width="200"  alt="image" src="https://github.com/user-attachments/assets/3d8b2420-5d6b-4888-9b7d-1844ce89d454" />      
<img width="200"  alt="image" src="https://github.com/user-attachments/assets/a09808bc-af4c-4bfe-bdd1-f98b3fdc02c6" />      
<img width="200"  alt="image" src="https://github.com/user-attachments/assets/3187ff7f-64e0-4de3-afe6-761293a52981" />      
<img width="200"  alt="image" src="https://github.com/user-attachments/assets/05415ccb-4d60-4df4-a6a6-33547f01b33b" />


## Install

Download the signed APK from this repository's GitHub Releases page and open it on Android 7.0 or later. Android may ask you to allow installation from your browser or file manager. Future updates are installed manually from Releases.

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


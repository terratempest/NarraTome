# GitHub APK releases

NarraTome is distributed through GitHub Releases. No Play Store account, app bundle, or Play App Signing enrollment is needed.

## Signing

Keep the release keystore outside the repository and retain a secure recovery copy. This is the app-signing key: future APK updates must use the same key and application ID, with a higher `versionCode`. Losing it prevents normal updates for existing installations. Never publish a debug-signed APK as a release or commit signing credentials.

For local release builds, supply `NARRATOME_STORE_FILE`, `NARRATOME_STORE_PASSWORD`, `NARRATOME_KEY_ALIAS`, and `NARRATOME_KEY_PASSWORD` through a secret manager or process environment. Optional `NARRATOME_PRIVACY_POLICY_URL` and `NARRATOME_DEVELOPER_CONTACT` set public in-app information; they are embedded in the APK, so use only a project contact you intend to publish. The full privacy policy is available offline without either value.

Use JDK 17 and the repository wrapper. Run in order, stopping on failure:

```powershell
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
.\gradlew lintDebug
.\gradlew assembleRelease -PrequireReleaseSigning=true --no-configuration-cache
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Signing-required mode fails without a keystore and all four signing inputs. Without that flag, Gradle permits unsigned diagnostic builds; do not distribute those. Verify using Android SDK `apksigner verify --verbose --print-certs <apk>` and compare the certificate fingerprint against your retained signing certificate. Check native packaging with `zipalign -v -c -P 16 4 <apk>`. Retain mapping files privately for each release.

## GitHub workflow

1. Create the new repository from this sanitized directory, with fresh history. Enable GitHub private vulnerability reporting. Review the proposed source files before the first push; do not import the old Git history, backups, credentials, or local configuration.
2. Configure the GitHub `release` environment with secrets `NARRATOME_SIGNING_KEY_BASE64` (Base64-encoded release keystore), `NARRATOME_STORE_PASSWORD`, `NARRATOME_KEY_ALIAS`, and `NARRATOME_KEY_PASSWORD`. Optional environment variables are `NARRATOME_PRIVACY_POLICY_URL` and `NARRATOME_DEVELOPER_CONTACT`. Restrict environment deployment branches/tags and reviewers as appropriate.
3. Update `versionName` and `versionCode` in `app/build.gradle.kts`, commit the reviewed source, and push a matching tag such as `v1.0`. Use a public project Git author identity; author names/emails become public in commits and tags.
4. Run **GitHub APK release** manually with that existing tag. The workflow checks out the tag, validates tests/build/lint, signs and verifies the release APK, and creates a **draft** GitHub Release containing the APK and SHA-256 checksum. It refuses an existing release instead of overwriting assets. Build reports and mapping files stay in a separate Actions artifact.
5. Download and test the draft APK, inspect its certificate/checksum and release notes, then publish the draft through GitHub. The workflow does not publish automatically.

The workflow uses the repository's current GitHub identity dynamically; no personal repository URL or contact is hard-coded. A real signed release requires the owner's signing secrets and GitHub setup; none are included here.

## Runtime acceptance

Record device/OS, source revision, APK hash, and actual outcomes. Build checks alone do not prove runtime correctness. Preserve app data during testing; never uninstall or clear storage to work around signature conflicts.

Exercise login/reauthentication; HTTP default and Require HTTPS; rejected redirects; streaming/offline playback; seek/resume and process death; progress sync; interrupted downloads and network switching; background/notification controls; Android Auto browse/search/voice; TalkBack, large text, and locale changes. Test minified builds and 16 KB page-size startup/playback/downloads on suitable hardware or an emulator. Record `adb shell getconf PAGE_SIZE` for that check.

Review lint findings and resolve material security, accessibility, data-loss, or playback defects before publishing. Android Auto search currently returns up to 80 ranked matches per snapshot; expand backend/local paging together if that limit needs to grow.

## Public source hygiene

The public copy excludes original Git history, machine/IDE state, build outputs, prototype files, and app backups. `.gitignore` excludes future local outputs and common secret formats. The HTTPS regression test generates a disposable localhost key with the JDK and deletes it; no private-key fixture is distributed. Test tokens and reserved example addresses are synthetic.

Before each push, review `git diff --cached` and `git status --short`. Ignoring files does not sanitize already tracked files. Keep logs, server exports, screenshots with personal content, and signing material out of commits and release attachments. Preserve dependency copyright/license notices.

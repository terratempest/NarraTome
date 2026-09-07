# Security

NarraTome connects to user-managed Audiobookshelf servers. HTTP is supported by default, including release builds. HTTP traffic, including credentials, is unencrypted; use HTTPS across untrusted networks.

Server settings offer **Require HTTPS**, off by default. The shared client checks initial requests and redirects; enabling it cancels HTTP transfers. Blocked downloads retain partial files and pause. HTTP playback stops with its position retained; local playback remains available. Saved URLs are never silently rewritten.

HTTPS retains platform certificate and hostname validation. Certificate pinning is intentionally absent because servers and certificates are user-managed. No trust-all certificate manager or hostname bypass is permitted.

Session tokens are encrypted using Android Keystore AES-GCM before DataStore storage. Passwords are not persisted. Media requests use Bearer headers instead of app-added query tokens. Credentials are restricted to configured endpoint scheme/host/port/path boundaries; foreign credential-bearing requests and redirects are rejected. Release HTTP logging is disabled. Never include credentials in diagnostics or artifacts.

Credentials, databases, preferences, audio and cached covers are excluded from Android backup and transfer. Unsynchronized local state may not migrate to another device. An unreadable encrypted token results in reauthentication, not deletion of the library or downloads.

The exported media service supports Android Auto/media controllers. Cached artwork is intentionally readable for those integrations; the provider accepts only read-only single-ID requests and never arbitrary filesystem paths.

Release signing keys/passwords must stay outside the repository. Configure protected release-environment secrets as described in `docs/RELEASE.md`. Never distribute a release signed with the Android debug key or an APK whose signature was not verified.



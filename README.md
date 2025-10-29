# ntfy-noti-push (**Cyrillic support**) (Android → ntfy bridge)

Tiny Android app that listens to **all notifications** on your phone and forwards them to your **self-hosted ntfy** as JSON (UTF-8 safe, supports emoji/**Cyrillic**).
Built headlessly with Docker—no Android Studio required.

## Features

- NotificationListenerService (works in background)
- Sends to ntfy via **JSON POST to server root** with `topic` in body
- WorkManager queue with retries & backoff; runs when network is available
- Optional **Allow insecure TLS** (for self-signed test servers)
- Status panel in Settings:

  - Last notification seen
  - Last enqueued
  - Last send attempt
  - Last HTTP result (+ URL)

- Debug/test button (sends a sample message now)
- Loop prevention/controls (ignore ntfy app or this app; togglable)

## Prerequisites

- Docker + Docker Compose
- A self-hosted ntfy server (HTTPS recommended)
- (Optional) Bearer token on your ntfy server for auth

## Project structure (short)

```
.
├─ app/
│  ├─ src/main/java/com/example/ntfynotipush/...
│  ├─ src/main/res/...
│  └─ build.gradle.kts
├─ Dockerfile.android
├─ docker-compose.yml
├─ gradle.properties
├─ gradlew / gradlew.bat
├─ settings.gradle.kts
└─ local.properties   # auto-written by compose, not committed
```

## Build the APK with Docker

> **Windows PowerShell / macOS / Linux:**

```bash
docker compose build
docker compose run --rm android-build
```

**Result:**
`app/build/outputs/apk/debug/app-debug.apk`

> If you’re on Apple Silicon and hit toolchain issues, open `docker-compose.yml` and uncomment:
>
> ```
> # platform: linux/amd64
> ```

### (Optional) Clean SDK/Gradle caches

```bash
# removes volumes used for caching between runs
docker volume rm ntfy_android-sdk ntfy_gradle-cache 2>NUL || true
```

## Install & configure (on your phone)

1. Install `app-debug.apk`.
2. Open the app → tap **Grant Notification Access** and enable it for this app (toggle OFF→ON if already enabled).
3. Set:

   - **Server URL**: `https://your-ntfy.example.com`
   - **Topic**: e.g. `android-bridge` (single segment, no slashes)
   - **Bearer token** (optional)
   - If you use a **self-signed cert**, enable **Allow insecure TLS**.

4. Tap **Send Test to ntfy**—you should see an HTTP code toast and a message on your ntfy server.

## How messages are sent (JSON)

We POST to the **server root** with JSON:

```json
{
  "topic": "android-bridge",
  "title": "<Notification title>",
  "message": "<Notification text + details>",
  "tags": ["bell"]
}
```

Authorization (if used) is the only header: `Authorization: Bearer <token>`.

## Status panel (debugging)

Open the app to see:

- **Notification access**: Enabled/Disabled hint
- **Last notification seen**: proves the listener is grabbing events
- **Last enqueued**: Work was queued
- **Last send attempt** & **Last HTTP result**: delivery outcome

If “seen” updates but HTTP doesn’t:

- Check URL/token
- For 401/403: add the token
- For TLS errors: try **Allow insecure TLS** (then fix certs)
- OEM battery settings: set **No restrictions/Auto start** for this app

## Useful toggles

- **Include notifications from the ntfy app**: off by default to avoid loops while testing
- **Include notifications from this app**: off by default; enable only for self-tests
- **Debug: send a tiny ntfy event** per notification (optional)

## Release build (optional)

1. Create a keystore & add a `signingConfig` in `app/build.gradle.kts`.
2. Change compose command to:

```bash
docker compose run --rm android-build bash -lc "./gradlew --no-daemon assembleRelease"
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Troubleshooting

- **No notifications seen**: grant access; toggle OFF→ON; ensure test app isn’t in “Exclude packages”.
- **Test works, real doesn’t**: you were maybe testing _from_ the ntfy app (ignored by default). Enable the toggle to include it.
- **401/403**: missing/wrong token.
- **404**: topic mismatch; remember JSON publish expects **root URL**.
- **TLS handshake**: self-signed → enable “Allow insecure TLS” (for testing).

---

### TL;DR commands for Compose

```bash
# Build image
docker compose build

# Build APK
docker compose run --rm android-build
```

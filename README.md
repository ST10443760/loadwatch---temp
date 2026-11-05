# LoadWatch

[![CI](https://github.com/ST10443760/loadwatch---temp/actions/workflows/ci.yml/badge.svg)](https://github.com/ST10443760/loadwatch---temp/actions/workflows/ci.yml)

LoadWatch is a load-shedding companion app for Android. It answers one question for a South African
household: is my power on, when does it go off, and when does it come back?

You pick your suburb and the app pulls that area's schedule from the LoadWatch REST API. The home screen
then shows the current supply state, the next event and a live countdown to it. Signed-in users can confirm
what is actually happening in their area (power off or power back on). Those confirmations build a
per-area accuracy score, so you can see how far the published schedule can be trusted. You can also save
several areas as favourites and compare their next events side by side. Cached schedules stay readable
when you are offline, which is exactly when you need them.

The Android client is written in Kotlin with Jetpack Compose. The backend is an ASP.NET Core Web API
backed by Azure SQL.

## Architecture

_To be added._

## Setup

### Requirements

- Android Studio with the bundled JDK (JDK 21)
- Android SDK platform 36.1 (API 36, minor level 1) installed through the SDK Manager
- .NET 8 SDK or newer, plus SQL Server LocalDB, to run the API locally

### Pinned versions

The Android project is pinned to these versions on purpose. Newer releases of several libraries require
Android Gradle plugin 9.1 and compileSdk 37 and fail the build's AAR metadata check, so do not bump
anything here without upgrading AGP and compileSdk together. The reasons are also recorded at the top of
`gradle/libs.versions.toml`.

| Component | Version | Why it is pinned |
|---|---|---|
| Android Gradle plugin | 9.0.1 | The version this Android Studio install syncs with |
| compileSdk / targetSdk | 36.1 / 36 | Newest platform installed |
| Kotlin | 2.2.10 | Must match the Kotlin built into AGP 9.0.1 |
| KSP | 2.3.12 | |
| Compose BOM | 2025.09.01 | Keeps every Compose library on Compose UI 1.9.2 |
| core-ktx | 1.18.0 | 1.19.x needs AGP 9.1 and compileSdk 37 |
| Lifecycle | 2.10.0 | 2.11.x needs AGP 9.1 and compileSdk 37 |
| Navigation Compose | 2.9.8 | 2.10.x pulls in Lifecycle 2.11 |
| Hilt | 2.60.1 | |
| Hilt Lifecycle ViewModel Compose | 1.3.0 | 1.4.0 needs AGP 9.1 |
| Room | 2.8.5 | |
| Retrofit | 3.0.0 | |
| OkHttp | 5.4.0 | 5.5.x needs compileSdk 37 |
| kotlinx serialization | 1.9.0 | |
| Coroutines | 1.10.2 | |
| Biometric | 1.1.0 | Newest stable release |
| AppCompat | 1.7.1 | Hosts the biometric prompt and the per-app language before Android 13 |

### API base URL

The app reads the API address from `local.properties` in the project root, under the key `api.base.url`.
That file is not committed, so each machine sets its own:

```properties
api.base.url=https://your-loadwatch-api.azurewebsites.net/
```

Put the key on its own line. A trailing slash is added if you leave it off. Without the key the app builds
with the placeholder `https://loadwatch-api.invalid/`, which never resolves. A physical phone cannot reach
`localhost` on your computer, so point this at the hosted API or at your computer's LAN address.
Re-sync Gradle after changing it; the value is compiled into `BuildConfig.API_BASE_URL`.

### Build and test the app

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
```

The unit tests include a check that no feature package imports another feature package or reaches Room or
the API directly (NFR-01), and a check that every string is translated into isiZulu and Afrikaans with the
same placeholders.

### Biometric unlock

After signing in with Google the app offers biometric unlock once, and it can be turned on under Settings →
Security. Turning it on first asks for a fingerprint, face or the screen lock, so it is only stored once the
phone has shown it can unlock. From then on LoadWatch asks for biometrics each time it is launched, with the
PIN, pattern or password as the fallback. Cancelling leaves the lock screen up with Unlock and Sign out. If
the phone no longer has any fingerprint, face or screen lock, the lock screen explains why and lets the user
continue.

### Languages

LoadWatch is translated into English (the default), isiZulu (`values-zu`) and Afrikaans (`values-af`). The
language picker in Settings saves the choice to Room and the settings API straight away, and the app switches
language at once through AppCompat's per-app locales. On Android 13 and later the language also appears in
the system's per-app language settings. New strings go into all three `strings.xml` files, or are marked
`translatable="false"` if they are only symbols or formats.

### Logcat

Every log line uses a tag from `LogTags`, all starting with `LW.`, so filtering Logcat on `LW.` shows only
LoadWatch:

| Tag | What it shows |
|---|---|
| `LW.Lifecycle` | Activity and screen lifecycle events, view models being created and cleared, language changes |
| `LW.Navigation` | Every navigation transition and the area chosen during onboarding |
| `LW.State` | Screen states changing kind (for example Loading to Ready) and the schedule range |
| `LW.Auth` | Sign-in, sign-out and session expiry |
| `LW.Biometric` | Availability checks, the launch lock and every prompt outcome |
| `LW.Api` | HTTP traffic and API results, with server errors logged as errors |
| `LW.Room` | The database opening and settings being written |
| `LW.Sync` | Reports and settings waiting on the phone, retries and rejections |
| `LW.Countdown` | The home countdown rolling over to the next window |
| `LW.Push` | FCM token registration, area topic changes, incoming stage alerts and whether each was shown |

### Stage change alerts

Stage changes reach the phone through Firebase Cloud Messaging:

1. After signing in, and whenever Firebase rotates it, the app sends its FCM token to
   `POST /api/notifications/registrations`. The API keeps one row per token in `DeviceTokens`.
2. The app subscribes to one topic per phone, `area-<areaId>`, for the selected area. It leaves the old topic
   when the area changes and leaves it on sign-out.
3. A background service in the API checks every 30 seconds (`StageAlerts:PollSeconds`) for an area whose stage
   moved. An area's stage is its own entry in `AreaStages` if it has one, otherwise the national stage. Each
   change is sent once to the area's topic as a data-only message.
4. The app decides whether to show it. Alerts must be on, Stage changes ticked, and the alert must be for the
   selected area. The text is in the user's chosen language, and it adds the next outage when that starts
   within the alert lead time.

On Android 13 and later the app explains alerts once before Android asks for the notification permission.
Saying no is fine: alerts are then skipped, and Settings shows a button to allow them later.

The API sends nothing unless `FIREBASE_SERVICE_ACCOUNT_JSON` holds the full JSON of a Firebase service account
key. Without it, the API logs a warning at startup and logs each change as skipped. Never commit the key; key
files are in `.gitignore`.

To demo an alert, call the test endpoint with the key set in `StageAlerts__TestKey`. It sets the area's own
stage and sends the alert at once. A null stage puts the area back on the national stage:

```bash
curl -X POST https://your-loadwatch-api.azurewebsites.net/api/stages/areas/eth-berea/test-change \
  -H "Content-Type: application/json" -H "X-Stage-Test-Key: <your key>" -d '{"stage": 6}'
```

The response says whether the alert was `Sent`, `Skipped` (Firebase not configured), `Failed` or `NoChange`.
Without a configured key the endpoint answers 404, and with the wrong key 403.

### Run the API locally

```bash
cd api/LoadWatch.Api
dotnet run
```

The development connection string in `appsettings.Development.json` points at a LocalDB database named
`LoadWatchDb`. On every start the API applies any pending EF Core migrations, logging which ones it applied,
and then seeds Durban areas, a 14-day schedule and the current stage if they are missing. This runs in the
background after the API starts listening. A paused serverless Azure SQL database can take about a minute to
resume, so errors like 40613 and connection resets are retried for up to three minutes, each attempt logged as
a warning. A permanent error, such as a wrong password, or still failing after three minutes, makes the API
log the error and exit with code 1.

Three endpoints show how the API is doing. None needs sign-in:

- `GET /` answers `{"status":"Running",...}` at once and never touches the database, so a platform warmup or
  startup probe gets a quick 200 even while the database is resuming.
- `GET /health` returns `{"status":"Healthy","database":"Healthy",...}` when the database is reachable. While
  it is still being prepared or cannot be reached, it returns a 503 with a `detail` explaining why.
- Every `/api` route answers 503 with a `Retry-After` header until the database is ready.

On Azure the same keys come from the web app's settings instead of `appsettings.json`: the connection string
named `LoadWatchDb` and the app setting `Firebase__ProjectId`. Stage change alerts also need the app settings
`FIREBASE_SERVICE_ACCOUNT_JSON` and, for the demo endpoint, `StageAlerts__TestKey`. No connection string,
password or key is committed.

### Run the API in a container

```bash
docker build -t loadwatch-api api
docker run -p 8080:8080 -e "ConnectionStrings__LoadWatchDb=<your connection string>" loadwatch-api
```

This is the same image the deploy workflow publishes. The image itself sets `ASPNETCORE_URLS=http://+:8080`
and exposes port 8080, so the port doesn't depend on any host setting. `/` and `/health` are at
`http://localhost:8080/` and `http://localhost:8080/health`. It runs as a non-root user, which cannot bind
port 80. The container cannot reach LocalDB, so give it a SQL Server it can reach.

### Test the API

```bash
dotnet test api/LoadWatch.Api.Tests
```

Each test class creates its own throwaway database and deletes it afterwards. Locally the tests use LocalDB.
To use another SQL Server instead, set `LOADWATCH_TEST_SQL` to a connection string without a database name;
CI does this with a SQL Server container.

## GitHub Actions

Two workflows in `.github/workflows` build and check the project on GitHub's own Linux machines. Each run
starts from a fresh checkout, with no Android Studio, no `local.properties` and no LocalDB. A green run shows
that everything needed to build is in the repository, not only on my laptop.

### CI (`ci.yml`)

Runs on every push to any branch and on every pull request. The badge at the top of this README shows its
latest result. It has two jobs that run side by side:

- **Android app.** Sets up JDK 21 (AGP 9.0.1 needs at least 17, and the Gradle daemon is pinned to 21),
  installs SDK platform 36.1 and caches Gradle between runs. It then runs `./gradlew assembleDebug` and
  `./gradlew testDebugUnitTest`. With no `local.properties`, the build uses the placeholder API URL.
  `google-services.json` is committed. The debug APK is uploaded as a downloadable artifact named
  `loadwatch-debug-apk`, and if a test fails the test report is uploaded too.
- **API.** Starts SQL Server in a Docker container, because LocalDB only exists on Windows. It then builds the
  API and test project in Release and runs `dotnet test`. The tests get the container through the
  `LOADWATCH_TEST_SQL` environment variable. The container's password is generated for each run and
  hidden in the logs, so it is never stored. These steps live in `api-tests.yml`, a reusable workflow that
  the deploy workflow calls too, so both run exactly the same tests.

### Deploy API (`deploy-api.yml`)

Runs when a push to `main` changes files under `api/`, and can also be started by hand from the Actions tab.
First it runs the same API tests as CI. Only if they pass does it build `api/Dockerfile` into a container
image and push it to GitHub Container Registry as `ghcr.io/st10443760/loadwatch-api`. Each image gets two
tags: `latest` and the commit SHA, so any earlier build can still be run. The workflow signs in to the
registry with the built-in `GITHUB_TOKEN`, which it is given `packages: write` permission for. No Azure
secret is involved.

The workflow does not touch Azure itself. The Azure web app is set to run the
`ghcr.io/st10443760/loadwatch-api:latest` image, and it pulls the newest `latest` image whenever it is
restarted. So after a green run, restarting the web app puts the new API live. The container listens on port
8080 only, so the web app must probe and route to 8080. Set `WEBSITES_PORT=8080`. If the web app uses the
newer sidecar container setup, also set the port on the main container under Deployment Center, because that
setup ignores `WEBSITES_PORT`. `/` answers within about a second of the container starting, so the startup
probe passes even while the database is resuming. `/health` then shows when the API can reach the database. The connection string and Firebase project id are set on the web app in the Azure portal and never
pass through GitHub.

## Demo Video

Watch the demo on YouTube: https://youtu.be/Tpmgyyzz67U

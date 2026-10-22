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
the API directly (NFR-01).

### Run the API locally

```bash
cd api/LoadWatch.Api
dotnet run
```

The development connection string in `appsettings.Development.json` points at a LocalDB database named
`LoadWatchDb`. On every start the API applies any pending EF Core migrations, logging which ones it applied,
and then seeds Durban areas, a 14-day schedule and the current stage if they are missing. If a migration
fails, the API logs the error and refuses to start. `GET /health` needs no sign-in and returns
`{"status":"Healthy","database":"Healthy",...}`, or a 503 when the database cannot be reached.

On Azure the same keys come from the web app's settings instead of `appsettings.json`: the connection string
named `LoadWatchDb` and the app setting `Firebase__ProjectId`. No connection string or password is committed.

### Test the API

```bash
dotnet test api/LoadWatch.Api.Tests
```

Each test class creates its own throwaway database and deletes it afterwards. Locally the tests use LocalDB.
To use another SQL Server instead, set `LOADWATCH_TEST_SQL` to a connection string without a database name;
CI does this with a SQL Server container.

## GitHub Actions

_To be added._

## Demo Video

_To be added._

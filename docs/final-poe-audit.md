# Final POE audit

A snapshot of what the Android app and the API already had at the start of the Final POE, before any Final POE
work. `[x]` exists, `[~]` partly exists, `[ ]` missing.

## Biometric login

- [ ] `androidx.biometric` is not a dependency and there is no `BiometricPrompt` anywhere in the app
- [~] `biometricEnabled` is stored in Room (`UserSettingsEntity`), sent to the API and shown as a switch on S09
  Settings, but nothing reads it. The note under the switch says unlock "arrives in a later update"
- [ ] No lock on app launch and no check for missing hardware or enrolment
- [ ] `MainActivity` is a `ComponentActivity`, but `BiometricPrompt` needs a `FragmentActivity`

## Offline mode with sync (Room, queued writes)

- [x] Room database (`LoadWatchDatabase`, schema version 3, exported schemas committed) caching areas, schedules,
  the current stage, accuracy, favourites, settings and outage reports
- [x] Offline-first repositories: the schedule, area search, favourites and settings read Room first and refresh
  from the API, and fall back to the cache with an "offline" notice when the API cannot be reached
- [x] Outage reports are written to Room as `PENDING` before they are sent, keep the same `clientReportId` on every
  retry so they are only counted once, and can be retried from S07 ("Retry sending")
- [x] Settings saved offline are kept on the device (`SavedOnDeviceOnly`) and can be sent again
- [~] A generic `pending_sync` queue table and `PendingSyncDao` exist, but nothing writes to them yet
- [ ] No background sync (no WorkManager job), so unsent reports only go out when the user retries on S07
- [~] API: `POST /api/sync/outage-reports` is routed but answers with the Final POE scope placeholder

## Firebase Cloud Messaging push notifications

- [x] Firebase is set up (`google-services.json`, the Google services plugin, Firebase BoM and Firebase Auth)
- [ ] `firebase-messaging` is not a dependency, and there is no `FirebaseMessagingService`, token handling,
  notification channel or `POST_NOTIFICATIONS` permission. `core/notifications` only holds a `.gitkeep`
- [~] The alert lead time, the on/off switch and the alert types are stored and synced from S09 Settings
- [~] API: `POST /api/notifications/registrations` is routed but answers with the Final POE scope placeholder,
  and the API has no FCM sender

## Multi-language (isiZulu and Afrikaans)

- [x] Almost every user-facing string is already in `res/values/strings.xml`, including plurals, and dates and
  times are formatted with the current locale
- [ ] No `values-zu` or `values-af` folders, and no `locales_config.xml`
- [~] S09 Settings has an English / isiZulu / Afrikaans picker. The choice is validated and saved to Room and to
  the API (which accepts `en`, `zu` and `af`), but it does not change the app's language. The note says so
- [ ] A few strings are still hardcoded: the lead time stepper's `−` and `+`, and the `d` day suffix in the
  countdown

## Log usage

- [x] `LogTags` gives every log line an `LW.` tag (`LW.Lifecycle`, `LW.Navigation`, `LW.Auth`, `LW.Api`,
  `LW.Room`, `LW.Sync`, `LW.Countdown`), so Logcat can be filtered on `LW.`
- [x] Activity and application lifecycle, every screen's lifecycle (`LogScreenLifecycle`), navigation
  transitions, the Room database opening, HTTP traffic, token refreshes and Firebase sign-in are logged
- [~] Gaps: `LoginViewModel`, `AreaOnboardingViewModel`, `AppViewModel`, the area and settings data sources and
  the remote data sources other than reports log nothing. There is only one `Log.e` in the whole app
  (`SplashViewModel`) and almost no `Log.d` for state changes
- [x] API: startup migrations, seeding and retries are logged through `ILogger`

## Unit tests

- [x] Android: 81 JVM unit tests across 15 test classes, with shared fakes for the API, DAOs, auth, transactions and
  a virtual-time clock. They cover the countdown, schedule days and freshness, area search, the home, schedule
  and settings view models, settings validation, report submission, offline reports, favourites and sign-out
- [x] API: 28 xUnit tests for authentication, health, the root endpoint, startup retry and the strict enum
  converter, run against LocalDB or a SQL Server container
- [x] Both test suites run in GitHub Actions on every push and pull request
- [ ] No tests for biometrics, language switching, sync or notifications, because those features do not exist yet

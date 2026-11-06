# LoadWatch release notes

## Version 2.0: Final POE release

These notes cover every change since the Part 2 prototype (version 1.0, commit `7ad0c1f`, 27 October 2026).
Items marked **NEW in the final version** did not exist in the Part 2 prototype. The rest improve or extend
what was already there.

### New in the final version

| Feature | In Part 2 | In the final version |
|---|---|---|
| Biometric unlock | No | **NEW** |
| Offline sync with background WorkManager sync | No | **NEW** |
| Push notifications (Firebase Cloud Messaging) | No | **NEW** |
| Stage-change alerts (user-defined feature 4) | No | **NEW** |
| isiZulu and Afrikaans | No | **NEW** |
| Outage history and calendar (user-defined feature 5) | No | **NEW** |
| New app icon | No | **NEW** |

### Biometric unlock (NEW in the final version)

- Biometric unlock can be turned on under Settings → Security. It is only saved after the phone has shown
  it can unlock.
- When it is on, LoadWatch asks for a fingerprint, face or the screen lock (PIN, pattern or password) each
  time it opens. Nothing behind the lock is shown until it is unlocked.
- After signing in with Google, the app offers biometric unlock once.
- Cancelled prompts, too many attempts, and phones without a sensor or screen lock are each explained, and
  the lock screen always offers Sign out.

### Offline mode and sync (NEW in the final version)

- Community reports, favourites (add, remove, relabel) and settings now work without a connection. Each
  change is saved on the phone at once and queued in `pending_sync`.
- A WorkManager job with a network constraint sends the queue as soon as the phone is back online. It
  retries with exponential backoff from 30 seconds, and also runs on launch if anything is left over.
- A banner on every main screen shows how many changes are waiting to sync.
- Conflicts:
  - A retried report is recognised by its `clientReportId` and is never stored twice.
  - Settings use last write wins, based on when they were saved.
  - Favourite changes apply in order, and the server's list then replaces the phone's.
- API: the placeholder sync route is replaced by `POST /api/sync`, which takes reports, favourite changes and
  settings in one request. It answers for each item: accepted, duplicate, superseded or rejected.
- Every sync step is logged under `LW.Sync`.

### Push notifications and stage-change alerts (NEW in the final version)

- The app registers its FCM token with the API on sign-in and whenever Firebase rotates it. It follows one
  topic per phone, `area-<areaId>`, and moves it when the area changes or the user signs out.
- A background service in the API checks every 30 seconds for an area whose stage changed, and sends one
  data-only alert to that area's topic.
- The phone shows the alert in the user's language. It respects the alert switch, the ticked alert types and
  the selected area, and mentions the next outage when it starts within the alert lead time.
- On Android 13 and later the app explains alerts before asking for the notification permission. Saying no
  is handled quietly, and Settings shows a button to allow them later.
- API:
  - A real `POST /api/notifications/registrations` endpoint.
  - New `DeviceTokens` and `AreaStages` tables.
  - The Firebase Admin SDK, configured from the `FIREBASE_SERVICE_ACCOUNT_JSON` environment variable.
  - A key-protected demo endpoint that changes an area's stage.

### isiZulu and Afrikaans (NEW in the final version)

- Every string in the app is translated into isiZulu (`values-zu`) and Afrikaans (`values-af`), including
  notifications.
- The language picker in Settings switches language at once and saves the choice with the account. On
  Android 13 and later the language also appears in the system's per-app language settings.
- A unit test fails the build if a translation is missing or changes a placeholder.

### Outage history and calendar (NEW in the final version)

- New screen S10 Outage history, opened from Schedule. It shows the selected area's past outages and
  community reports for one month, as a calendar or a list, going back a year.
- Calendar days are shaded by hours without power, and a dot marks days with community reports. Tapping a
  day shows its outages, how many reports confirmed each one, and the reports themselves.
- Each month is cached in Room (database version 4), so any month opened before is available offline.
- API: the placeholder history route is replaced by `GET /api/areas/{areaId}/history?month=yyyy-MM`, grouped
  by South African day. The seeder fills in the previous 60 days once, so a new database has history to show.

### New app icon (NEW in the final version)

- A custom adaptive icon from the design: an amber location pin with a lightning bolt inside, in a thin amber
  ring, on deep slate.
- It includes a monochrome layer for Android 13 themed icons, matching bitmaps for Android 7, and a 512 pixel
  store icon.
- The LoadWatch logo now appears on the splash and sign-in screens.

### Polish and reliability

- Shared loading and empty states on every main screen, and a spacing scale so gaps line up.
- A response from the API that the app cannot read, such as a malformed date, is shown as a server problem
  instead of crashing the app.
- Settings edits made in quick succession are no longer lost.
- Logging across the app, with a consistent `LW.*` tag per area (lifecycle, navigation, state, auth,
  biometric, API, Room, sync, countdown and push). Screen states are logged each time they change kind.

### Release build

- The release build is shrunk and obfuscated with R8, with resource shrinking and the ProGuard rules the app
  needs.
- Release signing reads the keystore path, passwords and alias from Gradle properties or environment
  variables. Keystores and signing files are git-ignored.
- The version is now 2.0 (version code 2).

### API

- Report and settings rules now live in shared services, so the single endpoints and the sync batch behave
  the same way.
- Stage changes and their alert checks run under one lock, so the demo endpoint always reports the alert it
  caused.

### Tests and CI

- Android unit tests grew from 81 to 178. They now cover:
  - biometric decisions
  - language saving and translation coverage
  - stage alert parsing and policy
  - push topic changes
  - the sync queue and sync engine, including retries and conflicts
  - offline reports, favourites and settings
  - the history calendar, repository and view model
  - unreadable API responses
- API integration tests grew from 28 to 77. They now cover device registration, stage change detection and
  alerts, the history endpoint and the sync endpoint.
- CI now also runs `lintDebug` on every push and uploads the lint report when it fails.
- A timing assertion that was too tight for the shared CI runner was loosened.

### Documentation

- The README is rewritten as the final technical report. It covers purpose, features, architecture, design
  decisions, API endpoints, database schema, security, DevOps, languages, screenshots, the demo video, and
  how to build and release.

# Play Store listing (readiness evidence)

This page holds the store listing text and the release details LoadWatch needs for the Google Play Console.
The app has not been published. This is the listing it would go out with.

## App details

| Field | Value |
|---|---|
| App name | LoadWatch |
| Default language | English (South Africa) |
| Also translated | isiZulu, Afrikaans |
| Category | Tools |
| Tags | Utilities, Weather and alerts |
| Price | Free, no ads, no in-app purchases |
| Contact email | Group 12 project email (set in the Play Console) |

## Short description

> Know before the power goes: load-shedding times, countdowns and outage alerts.

(78 characters, limit 80.)

## Full description

> LoadWatch tells you whether your power is on, when it goes off next and when it comes back.
>
> Choose your suburb once. The home screen then shows the current supply state, the next scheduled outage
> and a live countdown to it. You get an alert before the power goes off, when it comes back, and when
> the load-shedding stage changes in your area.
>
> **Features**
>
> - Live countdown to the next outage or to when power returns
> - Schedule for the next 3, 7 or 14 days, grouped by day and stage
> - Push notifications before outages and when your area's stage changes
> - Community reports: say whether the power really went off, and see how closely your area follows the
>   published schedule
> - Compare saved areas, such as home and work, side by side
> - Outage history with a monthly calendar and a day-by-day list
> - Works offline: the schedule is cached on your phone, and reports, favourites and settings made offline
>   are sent automatically when you reconnect
> - Optional fingerprint, face or screen-lock unlock
> - English, isiZulu and Afrikaans, plus light, dark and system themes
>
> **Your data**
>
> You sign in with your Google account. LoadWatch uses your name and email only to identify your account,
> and it keeps your areas, settings and reports so they follow you between devices. LoadWatch never sells
> your data and shows no ads.
>
> LoadWatch is a student project for PROG7314 / OPSC7312. Schedules come from LoadWatch's own API and may
> differ from your municipality's official announcements.

(About 1,500 characters, limit 4,000.)

## Target audience and content

| Field | Value |
|---|---|
| Target age group | 18 and over |
| Appeals to children | No |
| Content rating | Everyone (no violence, gambling, user chat or user-generated media; reports are fixed choices) |
| Ads | None |
| Countries | South Africa |

## Data safety summary

All data is sent over HTTPS. Users can sign out at any time, and an account deletion request goes through
the contact email.

| Data type | Collected | Shared with third parties | Why | Optional |
|---|---|---|---|---|
| Name and email address | Yes | No | Account management: identifies the signed-in user (Google sign-in through Firebase Authentication) | No, sign-in is required |
| User IDs (Firebase UID) | Yes | No | Links the user's settings, favourites and reports to their account | No |
| App activity: outage reports | Yes | No | App functionality: the area's schedule accuracy score is built from these reports | Yes |
| App info: saved areas and settings | Yes | No | App functionality: syncs the default area, favourites, alert settings, language and theme across devices | Yes |
| Device ID: FCM registration token | Yes | No | App functionality: delivers stage-change push notifications to the device | Yes, alerts can be turned off |
| Location | No | No | The user picks a suburb by name. The app never reads GPS. | Not applicable |
| Biometric data | No | No | BiometricPrompt runs on the device. LoadWatch only receives a pass or fail result. | Not applicable |
| Financial, contacts, photos, messages | No | No | Not used | Not applicable |

Service providers that process this data on LoadWatch's behalf: Google Firebase (authentication and Cloud
Messaging) and Microsoft Azure (API and Azure SQL database).

**Permissions:** `INTERNET` to reach the API, and `POST_NOTIFICATIONS` (Android 13 and later) for outage
and stage-change alerts.

## Release signing and versioning

| Field | Value |
|---|---|
| Version name | 2.0 |
| Version code | 2 |
| Min SDK / target SDK | 24 / 36 |
| Build | `.\gradlew.bat assembleRelease`, with R8 minification and resource shrinking |
| Output | `app/build/outputs/apk/release/app-release.apk` |
| Signing | Release keystore kept outside the repo. The path, passwords and alias come from `LOADWATCH_KEYSTORE_PATH`, `LOADWATCH_KEYSTORE_PASSWORD`, `LOADWATCH_KEY_ALIAS` and `LOADWATCH_KEY_PASSWORD` in the user-level `gradle.properties` or the environment. |
| Firebase | The release key's SHA-1 fingerprint is registered in Firebase, and `app/google-services.json` includes it so Google sign-in works in the signed build. |

The next store update must raise `versionCode` above 2 and be signed with the same key. For the Play Store
itself, the build would be uploaded as an Android App Bundle (`.\gradlew.bat bundleRelease`) and enrolled in
Play App Signing.

**Still needed before publishing:**

- The application ID is `com.example.loadwatch`, and Google Play does not accept the `com.example` prefix.
  Publishing needs a new ID, such as `za.co.loadwatch`, registered as a new Android app in Firebase with a
  fresh `google-services.json`.
- The sign-in screen's privacy policy is still a short in-app summary. Play needs a public privacy policy
  URL.

## Promotional screenshots

These phone screenshots in [`screenshots/`](screenshots/) serve as the store's promotional screenshots, in
this order:

1. [`s04-home.png`](screenshots/s04-home.png): home with the live countdown
2. [`s06-schedule.png`](screenshots/s06-schedule.png): 7-day schedule
3. [`stage-alert.png`](screenshots/stage-alert.png): stage-change push notification
4. [`s07-report.png`](screenshots/s07-report.png): community report and accuracy score
5. [`s08-compare.png`](screenshots/s08-compare.png): comparing saved areas
6. [`s10-history-calendar.png`](screenshots/s10-history-calendar.png): outage history calendar
7. [`s10-history-list.png`](screenshots/s10-history-list.png): outage history list
8. [`offline-pending.png`](screenshots/offline-pending.png): report waiting to send while offline
9. [`languages.png`](screenshots/languages.png): home screen in isiZulu
10. [`lock.png`](screenshots/lock.png): biometric unlock
11. [`s09-settings.png`](screenshots/s09-settings.png): settings
12. [`s02-sign-in.png`](screenshots/s02-sign-in.png): sign-in

Before uploading to Play, crop `stage-alert.png` down to the notification, because it shows the other apps
on the phone, and hide the signed-in email address in `s09-settings.png`. `lock.png` is a photo of the
screen, because Android blocks screenshots of the biometric prompt.

# TeeUp Android client

Kotlin Android app for TeeUp. Talks only to the TeeUp API (`../api`) —
never touches the database directly.

## Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK 34

## Build

```
./gradlew build
./gradlew test
```

## Screen scaffold (EME-308)

Every screen from the Figma prototype has an Activity + layout stub already
wired into navigation (plain platform `Activity`, no Jetpack/AndroidX —
that's a deliberate team decision). Pick up your ticket and fill in the
existing file rather than creating a new screen:

| # | Figma frame | Activity / layout | Ticket |
|---|---|---|---|
| 1 | Sign In | `SignInActivity` / `activity_sign_in.xml` | EME-295 |
| — | *(implied by "New here? Register")* | `RegisterActivity` / `activity_register.xml` | EME-296 |
| 2 | Home / Find a Tee Time | `HomeActivity` / `activity_home.xml` | EME-297 |
| — | *(no frame — 4th bottom-nav tab)* | `RoundsActivity` / `activity_rounds.xml` | unticketed |
| 3 | Tee Time Detail / Join Request | `TeeTimeDetailActivity` / `activity_tee_time_detail.xml` | EME-298, EME-299 |
| 4 | Profile & Settings | `ProfileActivity` / `activity_profile.xml` | EME-301, EME-307 |
| 5 | Live Scorecard | `ScorecardActivity` / `activity_scorecard.xml` | EME-303 |
| 6 | Notifications | `NotificationsActivity` / `activity_notifications.xml` | unticketed |

Bottom nav (Home/Rounds/Scorecard/Profile) is `res/layout/bottom_nav.xml`,
wired via `nav/BottomNav.kt` — `include` it and call
`BottomNav.wire(this, BottomNavTab.X)` in `onCreate` for any new tab-root
screen. `colors.xml`/`bg_card*.xml`/`bg_button_outline.xml` are rough
placeholders sampled from the Figma file; EME-307 owns the real pass.

Note: the Figma frame for Live Scorecard has no bottom nav (it reads as an
immersive in-round view), but it's wired here as the Scorecard tab's direct
landing page for scaffolding simplicity. Worth revisiting when EME-303 is
built — might want an "active rounds" list before dropping into
hole-by-hole scoring.

## Running against the real API (EME-297)

`HomeActivity` calls the live `GET /api/courses` + `GET /api/teetimes`
endpoints — no mock data. To see it work end to end on the emulator:

1. Get the API running locally against a real Postgres (`../api/README.md`
   covers provisioning; for local dev, `brew install postgresql@16`, then
   `createdb teeup` and `dotnet ef database update --project TeeUp.Api`
   with `ConnectionStrings__Default=Host=localhost;Database=teeup;Username=<you>`
   works fine).
2. Run the API: `ConnectionStrings__Default=... dotnet run --project ../api/TeeUp.Api`
   (defaults to `http://localhost:5017`).
3. Run the Android app on an **emulator** (not a physical device) —
   `data/ApiConfig.kt` points at `http://10.0.2.2:5017/`, the emulator's
   alias for your host machine. EME-294 owns real per-environment config;
   swap that constant for a physical device or the deployed API.

If the API isn't reachable, Home shows an inline error instead of crashing
or silently falling back to fake data — that's deliberate (Application
Robustness requirement).

Networking is plain `HttpURLConnection` + `org.json` (`data/TeeUpApiClient.kt`)
rather than Retrofit/OkHttp, since that's EME-294's base scaffold to set up
and this ticket didn't need to wait on it. Worth migrating onto whatever
EME-294 lands.

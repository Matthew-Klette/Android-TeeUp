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

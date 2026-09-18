# TeeUp

TeeUp is a golf tee-time booking, skill-based player matching, and live GPS
scorecard app. It combines three things no single competitor offers together:
tee-time booking (like GolfNow), matching with golfers you don't already know
(unlike GolfNow/18Birdies), and a live GPS scorecard (like 18Birdies/Wedge).

This is a monorepo containing both the Android client and the backend API.

## Structure

```
android/   Kotlin Android client (plain Android SDK, no Jetpack/AndroidX)
api/       ASP.NET Core Web API (controllers/services/repositories) + EF Core/PostgreSQL
```

See `android/README.md` and `api/README.md` for module-specific setup.

## Architecture

- **Client**: Kotlin Android app. Never talks to the database directly —
  all persistence goes through the REST API.
- **API**: ASP.NET Core Web API backed by Azure Database for PostgreSQL via
  EF Core. Six controllers: Auth, TeeTimes, JoinRequests, Rounds, Profiles,
  Courses.
- **Auth**: Firebase Authentication issues ID tokens on the client; the API
  verifies them as JWT bearer tokens against Firebase's public keys.

## CI

GitHub Actions builds and tests both modules on every push:
- `.github/workflows/android-ci.yml` — Gradle build + unit tests
- `.github/workflows/api-ci.yml` — dotnet build + unit tests

## Branching

Work happens on `<initials>/<issue-id>-<slug>` branches cut from `main`,
matching the branch name Linear generates for each issue.

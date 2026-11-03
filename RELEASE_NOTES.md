# TeeUp Release Notes

## Version 1.0.0 (Final POE), November 2026

`versionCode 2` · `versionName 1.0.0` · minimum Android 7.0 (API 24) · target API 34

This release turns the Part 2 prototype (version 0.1, submitted 22 September 2026) into the
finished, store-ready app. Every functional change since the prototype is listed below, grouped by
the POE requirement it meets. Items marked **New feature** are the innovative features added in
this final version.

### 1. Offline mode with synchronisation (New feature)

- **Room database on the device** (`data/offline/TeeUpDatabase.kt`) with two tables:
  - `cached_responses`: the last successful copy of every API read (tee times, courses, your
    rounds, your profile, notifications), stored per signed-in user.
  - `pending_scorecards`: the outbox of finished rounds that have not reached the server yet.
- **Read offline:** when the API can't be reached, `TeeUpApiClient` serves the saved copy, so Home,
  Rounds, the Scorecard, Profile and Notifications keep working without a connection.
- **Write offline:** finishing a round with no signal saves it to the outbox and marks it
  *Waiting to sync* on the Rounds tab, instead of failing.
- **Automatic sync:** a WorkManager job (`ScorecardSyncWorker`) runs only when the network is
  connected, uploads every queued round, retries with exponential backoff, and survives the app
  being closed or the phone restarting. A system notification confirms when rounds have uploaded.
- **Conflict handling:** holes already on the server are skipped before upload, so a retried
  upload never duplicates scores. The server's last accepted write wins, as specified in Part 1.
  A round the server permanently rejects is kept with its reason and can be discarded.
- **Live connectivity:** a "You're offline" banner appears on every screen the moment the
  connection drops and disappears when it returns (`NetworkMonitor`, `OfflineBanner`).
- **Offline Data & Sync Status** (Profile) now shows the real queue, the last successful sync,
  the live connection type, and a *Sync Now* button.

### 2. Real-time push notifications with Firebase Cloud Messaging (New feature)

- Android: `TeeUpMessagingService` receives FCM messages. Pushes appear as system notifications
  in the background and in the foreground, and an open Notifications screen refreshes instantly.
- Two notification channels: *Group alerts* and *Offline sync*. On Android 13 and later the app
  asks for the notification permission on Home.
- Each device's FCM token is stored in **Firebase Realtime Database** (NoSQL) at
  `deviceTokens/{uid}/{token}`, and removed on sign-out. Security rules in
  `firebase/database.rules.json` let each user write only their own tokens.
- API: every notification the server saves is now also pushed (Firebase Admin SDK). The API reads
  the recipient's tokens from Realtime Database, deletes dead tokens, and honours the user's
  notification preferences. A push failure never fails the request.
- New server-side triggers: the host is notified when someone asks to join their group, and
  partners are notified when they're invited to a partners-only group.

### 3. Playing partners and partners-only groups (New feature, user-defined feature from Part 1)

- **Playing Partners** screen (Profile): search golfers by name, add and remove partners.
- Add a partner straight from a group's member list ("Add" next to their name).
- **Partners only** switch on Create/Edit Group. Partners-only groups are visible to, and joinable
  by, only the host's partners. Each partner gets a push invite when one is created.
- Partners-only groups are labelled on Home cards and on the group detail screen.
- API: `GET/POST/DELETE /api/friends`, `GET /api/users/search`, `TeeTime.IsPrivate`, a new
  `Friendships` table (migration `AddPartnersAndPrivateGroups`), and server-side visibility checks.

### 4. Live GPS scorecard and hole map (user-defined feature from Part 1)

- Satellite hole map (MapLibre, bundled so it works offline) with live front, middle and back of
  green distances from the phone's GPS.
- The map follows the golfer from hole to hole. The player dot, layup target and flag can be
  dragged, and Reset puts them back.
- Real South African course catalogue with hole maps from OpenStreetMap, plus traced Port
  Elizabeth courses. API endpoint `GET /api/courses/{id}/holes`.
- Round progress (hole, target, unsent scores) is kept on the device, so leaving and returning
  lands on the same hole. *Start a Round* suggests nearby courses.

### 5. Multi-language support

- English, **Afrikaans** and **isiXhosa** now cover every screen, including all new Part 3 text
  (124 strings were added to each language in this release).
- The lint `MissingTranslation` check is switched back on, so CI fails if any string is left
  untranslated.

### 6. User interface and production assets

- Every screen rebuilt in Jetpack Compose with one consistent design system (typography, colour,
  spacing, glass bars, sheets and motion).
- Custom splash animation.
- **Production launcher icon:** an adaptive icon (Android 8+) plus round and legacy icons, built
  from the official TeeUp logo, with a monochrome layer for themed icons and a dedicated
  status-bar notification icon.

### 7. Store readiness, security and quality

- Release builds are signed with the TeeUp upload key, and shrunk and obfuscated with R8 (rules in
  `proguard-rules.pro`). The keystore and passwords stay out of git.
- `usesCleartextTraffic` is now `false`: all traffic is HTTPS.
- Play Store listing text, privacy policy, 512 px icon, 1024 x 500 feature graphic and screenshots
  are in `docs/play-store/`.
- **Logging:** every Activity's lifecycle is logged (`ui/LifecycleLogging.kt`), along with network
  calls, cache hits, sync runs, push events and key state changes in the ViewModels.
- CI now runs on every push and pull request, on every branch.
- Compressed files and published API binaries were removed from the repository.

### Fixes since the prototype

- A hole posted twice can no longer create a duplicate score (server check plus the sync worker's
  skip of saved holes).
- Join request, round and auto-decline concurrency races were fixed in the API.
- Course pins were moved to the real course centres, and closed courses were removed.
- A course with no hole map returns "not mapped" instead of an error.

## Version 0.1 (Prototype, Part 2), September 2026

Google and email/password sign-in, biometric unlock, browsing and creating groups, join requests,
hole-by-hole scoring with round stats (net score, Stableford), profile and notification settings,
in-app notifications, English/Afrikaans/isiXhosa, and GitHub Actions CI for the app and the API.

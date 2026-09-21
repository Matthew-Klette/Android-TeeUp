# EME-305 — Manual QA pass: input validation & crash handling

Scope: Part 2 Application Robustness criterion — "The user interface must be
intuitive, user-friendly, and capable of gracefully handling invalid inputs
through validation checks without crashing." This is a pass over every screen
that had landed by the time this ticket was picked up (blocked by EME-303,
EME-304), not a new feature. Findings below are grouped per screen: what was
checked, what was already solid, what was fixed here, and what's explicitly
deferred (with the ticket that owns it).

## Method

Static review of every `Activity`/`ViewModel` plus its layout, cross-checked
against three failure classes per the ticket brief:

1. **Empty states** — zero rows, no data yet, nothing to show.
2. **Bad input** — malformed text, out-of-range numbers, missing selections,
   on registration, settings (Profile), and the scorecard-entry surfaces.
3. **Network failures** — timeouts, unreachable API, non-2xx responses,
   auth/session errors — does the screen show a message and a retry, or does
   it hang/crash?

Every `R.string` / `R.array` / `R.id` / `R.layout` / `R.drawable` / `R.color`
/ `R.dimen` reference across the Kotlin source was cross-referenced against
the resource XML to rule out a missing-resource build break (none found).
`grep` passes for `!!`, non-`OrNull` numeric conversions, and unguarded
collection indexing found one real non-null-assertion smell (see RoundModels
below) and no unguarded array/index access.

No Android SDK or .NET SDK is available in the environment this pass was run
in, so nothing here was compiled or executed — see **Still to run** at the
bottom for the exact commands to do that locally before merging.

## Screen-by-screen

### Sign In (`SignInActivity`)
Google sign-in failure, cancellation, and a thrown `ApiException` from the
Google Play Services client are all caught and re-enable the button with a
message (`qa_sign_in_unavailable`); the Firebase credential exchange has a
30s timeout via `Tasks.await`. **No changes needed.**

### Register / profile setup (`RegisterActivity`)
Display name and handicap are validated against the same rules as the API
(`InputValidation.kt` — `validDisplayName`, `validHandicap`) before any
network call, with `EditText.error` set and focus moved to the offending
field. Home-course fetch failure doesn't block registration (it's optional).
Save failure re-enables the button and shows the error inline. **No changes
needed.**

### Home / Find a Tee Time (`HomeActivity`)
Loading, retry-on-failure, and a genuine empty state ("no tee times nearby")
are all present; the skill/pace filter dialog validates the handicap field
with `validHandicap` before applying. A stale background load can't overwrite
a newer one (`loadGeneration` guard).

- **Fixed:** the empty-state message was a hardcoded string literal instead
  of a string resource (`home_empty_no_teetimes` added to `strings.xml`) —
  inconsistent with every other message on the screen and a blocker for the
  multi-language requirement later in the POE.

### Tee Time Detail / Join Request (`TeeTimeDetailActivity`)
Missing/invalid tee time ID shows a clear error instead of a blank screen;
load failure has retry; Request-to-Join disables its button and shows
"Sending…" while in flight, and re-enables on failure with the server's
message.

- **Fixed — race condition, not previously a crash but a real bug:** the
  Pending Requests dialog's Accept/Decline buttons had no double-tap guard.
  Tapping Accept and Decline in quick succession (or double-tapping one)
  fired two concurrent `PATCH` requests for the same join request. The API's
  status guard (`JoinRequestService.UpdateStatusAsync`) rejected the second
  one with a `DomainValidationException`, so the user saw a confusing
  "already Accepted and cannot be changed" error toast on a request that, in
  fact, had just succeeded. Both buttons now disable the instant either is
  tapped, and the dialog dismisses on a successful response instead of
  showing a stale list (the underlying data was already refreshed via
  `render()`; leaving the dialog open just meant the visible copy no longer
  matched it).

### Notifications (`NotificationsActivity`)
Loading, retry, and a genuine empty state are present. Unknown/absent related
IDs are handled by `nav/Wireflow.kt`'s `notificationDestination` (covered by
`WireflowTest.kt`) rather than assumed. **No changes needed.**

### My Rounds (`RoundsActivity` / `RoundsViewModel`)
Loading, retry, and separate empty states for Upcoming vs. History; the
selected tab survives rotation (`onSaveInstanceState`). Server dates are
validated (`roundTimestamp`) before the state is published, so the render
path itself never has to handle a malformed date.

- **Fixed — code-smell tightened, not a reachable crash:** `roundTimestamp`
  ended in `.parse(normalized)!!.time`. In practice `SimpleDateFormat.parse`
  either returns a value or throws `ParseException` (never null on success),
  and that exception was already caught by `RoundsViewModel.load()`'s
  generic `catch (Exception)` — so this was never an exploitable crash. It's
  still exactly the kind of unchecked `!!` a QA pass should flag: the fix
  wraps the parse in a `try/catch` and folds a null result (belt-and-braces)
  into the same `IllegalArgumentException` the regex-mismatch branch already
  throws, so the function has one honest failure contract instead of two.
  Added `RobustnessTest.outOfRangeRoundTimestampsThrowIllegalArgumentWithoutCrashing`
  covering values that pass the shape regex but fail range validation
  (month 13, Feb 30, hour 25) to lock this in.

### Profile & Settings (`ProfileActivity` / `ProfileViewModel` / `ProfilePlayingEditor`)
This is the "settings" form named in the ticket. Load/save states are modelled
explicitly (`ProfileUiState`), with retry that knows whether to retry the load
or resubmit the last failed save (`retryLastAction`). Personal-details name
edit re-validates length; playing-details handicap re-validates the same 0–54,
one-decimal rule as the API and disables Save on invalid pace; the course
picker degrades to an inline error if courses fail to load, without blocking
handicap/pace edits. Language, biometric login, and offline sync rows are
explicitly inert previews (Final POE scope) rather than controls that would
silently do nothing if tapped. **No changes needed.**

### Live Scorecard / Post-Round Summary (`LiveScorecardActivity`, `PostRoundSummaryActivity`)
Both are intentionally scoped as navigation-preview destinations for Part 2
(see `android/NAVIGATION.md`) — "Live scoring, completion, GPS, endorsements,
and offline sync remain deferred." There is no scorecard-entry input form
here yet to validate; the only input is the tee-time-id extra, and both
screens already handle a missing one (disabled button / "round unavailable"
text) instead of crashing on a null. **Scorecard-entry validation lives
server-side today** (`RoundService.PostScorecardAsync`, exercised by
`InputValidationTests.InvalidScorecardDoesNotCreateRound` and
`DuplicateAndEmptyHolesAreRejectedBeforeAnyWrites`) — hole numbers, strokes,
putts, and duplicate holes are all rejected before any write. When the
hole-by-hole entry UI itself lands (Final POE), it should call that
already-validated endpoint and surface `DomainValidationException`'s message
the same way every other screen here does; no server-side gap to close now.

### Scorecard tab landing (`ScorecardActivity`)
Pure navigation stub (routes to My Rounds/History) — no input, nothing to
validate.

## Network-failure states (cross-cutting)

Both HTTP clients in use were reviewed end to end:

- `TeeUpApiClient` (`HttpURLConnection`, legacy screens): `IOException` from
  a dropped/unreachable connection is now caught and turned into
  `ApiException("Could not connect. Check your connection and try again.")`
  rather than propagating a raw `IOException` message to a `Toast`. Non-2xx
  responses map through `httpFailureMessage(code)` (401/403/404/409/429/5xx
  each get their own actionable copy) instead of echoing the server's raw
  error body. The Firebase ID-token fetch has an explicit 15s timeout so a
  hung token refresh can't hang every authenticated call indefinitely.
- `TeeUpRepository` (Retrofit, newer screens): the same shape — `IOException`
  → connectivity message, `HttpException` → status-code message — plus every
  call and its outcome is logged (`Log.d`/`Log.w`) without ever logging the
  bearer token itself.
- Every screen that loads data (Home, Notifications, Rounds, Tee Time Detail,
  Profile) shows a distinct loading state, a distinct error state with retry,
  and only renders content once data actually arrives — none of them assume
  success.

**Fixed on the API side**, found while confirming what an Android screen
would actually receive for a failure it doesn't control: `ExceptionHandlingMiddleware`
only mapped `DomainValidationException` → 400 and `NotFoundException` → 404.
Any other exception — a bug, a database timeout, a genuinely unexpected
`null` — had no handler and would fall through to ASP.NET's default
behaviour, which in Development returns a diagnostic response including the
exception's message and stack trace. The Android client doesn't parse error
bodies (by design — see above), so this was never a client-side crash risk,
but it's a real "graceful handling... without crashing" gap on the server
half of the same requirement, and a stack trace is exactly the kind of
internal detail that shouldn't reach any client. Added a catch-all that logs
the real exception server-side and returns a plain, generic 500 — matching
the message shape (`{status, detail}`) the other two branches already use.
Covered by `ExceptionHandlingMiddlewareTests` (three cases: validation → 400
with its message, not-found → 404 with its message, unexpected → 500 with
the exception's type/message/stack trace confirmed absent from the body).

## Changed files

| File | Change |
| --- | --- |
| `android/.../HomeActivity.kt` | Empty-state string moved to `strings.xml` |
| `android/.../res/values/strings.xml` | Added `home_empty_no_teetimes` |
| `android/.../TeeTimeDetailActivity.kt` | Debounce + dialog dismiss on Accept/Decline |
| `android/.../data/RoundModels.kt` | Removed `!!`, single exception contract |
| `android/.../test/.../RobustnessTest.kt` | +2 tests for `roundTimestamp` edge cases |
| `api/.../Common/ExceptionHandlingMiddleware.cs` | Catch-all → generic 500, logged |
| `api/TeeUp.Api.Tests/ExceptionHandlingMiddlewareTests.cs` | New — 3 tests |

## Verification

The code-only pass above couldn't be compiled or run in the environment it
was written in (no Android/.NET SDK there). Both were subsequently run for
real on Braeden's machine:

- **`dotnet test api/TeeUp.slnx`** — build succeeded, **48 passed, 0 failed,
  1 skipped** (49 total). The skip is `ProfilePersistenceTests` — it needs
  `TEEUP_TEST_DATABASE` pointed at a migrated Postgres instance, which is
  documented as opt-in (see `api/README.md`); not run here, not a failure.
  This covers `ExceptionHandlingMiddlewareTests` (the new catch-all) and
  `InputValidationTests` alongside every pre-existing service test.
- **`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`**
  (from `android/`) — **BUILD SUCCESSFUL**, 54 tasks (23 executed, 31
  up-to-date). `testDebugUnitTest`: **18 tests, 0 failures, 0 errors, 0
  skipped** across `PaceOfPlayTest` (1), `WireflowTest` (2),
  `FormattingTest` (9), `RobustnessTest` (6 — the 2 new cases included).
  `lintDebug` completed without failing the build (full report at
  `android/app/build/reports/lint-results-debug.html` if you want to skim
  warnings). Only warnings surfaced were pre-existing `GoogleSignIn`/
  `GoogleSignInOptions` deprecation notices in `SignInActivity.kt` — unrelated
  to this ticket, not introduced by it.

Both builds are now independently confirmed on a real device/toolchain, not
just reviewed. Only the physical-device checklist below remains.


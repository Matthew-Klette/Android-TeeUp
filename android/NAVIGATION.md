# EME-302 navigation

Home is the task root. Home, My Rounds, Scorecard, and Profile share the bottom
bar. Selecting the current tab does nothing; switching tabs removes the previous
non-Home tab. Back from any other tab returns to Home, then exits the task.
Child screens use the normal Activity stack and their Back button calls finish.

| Entry | Destination | Back returns to |
| --- | --- | --- |
| Home tee time | Tee Time Detail | Home |
| Home notifications | Notifications | Home |
| Booking/request/weather notification | Related Tee Time Detail | Notifications |
| Booking notification without a usable ID | My Rounds | Home |
| Sync notification | Scorecard tab | Home |
| Unknown notification type | Explanation, no navigation | Notifications |
| My Rounds: View tee time | Tee Time Detail | My Rounds |
| My Rounds: past round scorecard | Live Scorecard preview | My Rounds |
| My Rounds: past round summary | Post-Round Summary preview | My Rounds |
| Scorecard tab: View My Rounds | My Rounds, History selected | Home |
| Live Scorecard: Preview round summary | Post-Round Summary preview | Live Scorecard |

`GET /api/rounds/me/schedule` requires Firebase authentication and includes only
hosted tee times and accepted join requests, including bookings without scores.
Upcoming is scheduled at or after now (oldest first); History is before now
(newest first). History means the scheduled start has passed, not that a round
has been completed. The selected filter survives rotation. Returning from a
child reloads the list. Loading, retry, and separate empty states are provided.

Live scoring, completion, GPS, endorsements, and offline sync remain deferred.
The two preview destinations make no score or completion writes.

## Verification

Run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` from
`android`, and `dotnet test api/TeeUp.slnx` from the repository root. Database
persistence tests are opt-in as documented in the API README.

On a signed-in emulator, switch Home → Rounds → Profile → Scorecard, press Back,
and confirm Home appears without replaying old tabs. Check both rounds filters,
rotate, open a tee time and return. With hosted/accepted past tee times, follow
the scorecard → summary → Back path. With booking notifications, confirm that
the related tee-time ID is used and Back returns to Notifications. Deleted IDs
show the detail screen's not-found state; absent/malformed IDs fall back to
My Rounds. Unknown notification types do not open unrelated screens.

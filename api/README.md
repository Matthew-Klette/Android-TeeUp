# TeeUp API

ASP.NET Core Web API for TeeUp, backed by Azure Database for PostgreSQL via
EF Core. Layered as controllers → services → repositories.

## Requirements

- .NET 10 SDK
- PostgreSQL (Azure Database for PostgreSQL in production)

## Configuration

Store the local development connection string in .NET user-secrets, outside
the repository. From the `api` directory:

```text
dotnet user-secrets set "ConnectionStrings:Default" "Host=localhost;Port=5432;Database=teeup;Username=postgres;Password=<local-password>" --project TeeUp.Api
```

User-secrets load automatically in the Development environment. Alternatively,
set the connection string and Firebase project id via environment variables:

```
ConnectionStrings__Default=Host=...;Database=teeup;Username=...;Password=...
Firebase__ProjectId=<firebase-project-id>
```

Push notifications and the optional Firebase settings:

```
Firebase__ServiceAccountJson=<raw service-account JSON on one line>   # enables FCM push
Firebase__DatabaseUrl=https://teeup-881a7-default-rtdb.firebaseio.com  # optional, this is the default
```

`Firebase__ServiceAccountJson` is the app setting name on Azure App Service. If it is
absent the API tries Application Default Credentials (`GOOGLE_APPLICATION_CREDENTIALS`);
with neither, a no-op sender is used, one warning is logged at startup and the app and
tests run normally without push.

Never put database passwords in tracked appsettings files. The existing
`appsettings.Development.json` is tracked despite the ignore rule.

## Build & test

```
dotnet build
dotnet test
```

The profile persistence test is opt-in and uses a migrated local PostgreSQL
database. Set `TEEUP_TEST_DATABASE` to its connection string before running
`dotnet test`; otherwise this test is reported as skipped. It rolls back its
fixture data after execution. Keep credentials out of source control.

Profile endpoint tests use a test-only authentication handler to check identity
isolation, partial updates, and validation. Real Firebase sign-in still needs an
emulator check: edit personal details, playing details, and notification
preferences, then reopen Profile to confirm the saved values load correctly.

## Database migrations

Entities live in `TeeUp.Api/Models`, mapped in `TeeUp.Api/Data/TeeUpDbContext.cs`.

```
dotnet ef migrations add <Name> --project TeeUp.Api --output-dir Data/Migrations
dotnet ef database update --project TeeUp.Api
```

Requires `ConnectionStrings__Default` to point at a reachable PostgreSQL
instance (`dotnet tool install --global dotnet-ef` if the `ef` command isn't
found).

## Run

```
dotnet run --project TeeUp.Api
```

## Push notifications

Every stored notification is also pushed through Firebase Cloud Messaging (a
`PushingNotificationRepository` decorator over `EfNotificationRepository`). The Android app
writes device tokens to Realtime Database at `deviceTokens/{firebaseUid}/{fcmToken}`; the API
reads them over REST with the service-account token and deletes tokens FCM reports as
Unregistered / InvalidArgument / SenderIdMismatch. Pushes use a 5 s timeout and never fail the
request. `JoinRequestReceived`, `RequestAccepted`, `RequestDeclined` and `PrivateGroupInvite`
respect the user's join-request preference; `TeeTimeCancelled` and `TeeTimeReminder` respect
the tee-time-reminder preference. The notification row is stored either way.
FCM payload: title `TeeUp`, channel `teeup_alerts`, data `notificationId`, `type` (ordinal),
`relatedEntityId`.

`NotificationType` ordinals: 0 JoinRequestReceived, 1 RequestAccepted, 2 RequestDeclined,
3 TeeTimeReminder, 4 SyncPending, 5 WeatherAlert, 6 TeeTimeCancelled, 7 PrivateGroupInvite.

## Playing partners and partners-only groups

All endpoints require a bearer token.

| Method | Route | Notes |
| --- | --- | --- |
| GET | `/api/friends` | `FriendDto[]` sorted by display name |
| POST | `/api/friends` | body `{ "friendUserId": "<guid>" }`, returns `FriendDto`; idempotent; 400 self, 404 unknown user |
| DELETE | `/api/friends/{friendUserId}` | 204, idempotent |
| GET | `/api/users/search?query=` | `UserSearchResultDto[]`; case-insensitive, trimmed, under 2 chars returns `[]`, excludes caller, max 20 |

`FriendDto`: `{ userId, displayName, handicapIndex, paceOfPlay }`.
`UserSearchResultDto`: `{ userId, displayName, handicapIndex, paceOfPlay, isPartner }`.

`POST /api/teetimes/groups` accepts an optional `isPrivate` (default false) and
`PATCH /api/teetimes/{id}` an optional `isPrivate` (null/omitted = unchanged); `TeeTimeDto`
returns `isPrivate`. A partners-only group is hidden from `GET /api/teetimes` and its
join-request list/creation returns 403 unless the caller is the host or one of the host's
partners (partners are directional: owner to partner). Creating one, or editing a group to
private, sends each partner a `PrivateGroupInvite` notification. Creating a join request
notifies the host with `JoinRequestReceived`.

## Applying the migration and redeploying

Migration `AddPartnersAndPrivateGroups` adds `TeeTimes.IsPrivate` and the `Friendships` table.
It has not been applied to any database. Run it yourself from the `api` directory:

```
dotnet ef database update --project TeeUp.Api --connection "<Supabase direct connection string>"
```

Use the direct (port 5432) connection string, not the pooled one. Then redeploy the API as
you normally do and, for push, add the `Firebase__ServiceAccountJson` app setting on the Azure
App Service (optionally `Firebase__DatabaseUrl`) and restart it.

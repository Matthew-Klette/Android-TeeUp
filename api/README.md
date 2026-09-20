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

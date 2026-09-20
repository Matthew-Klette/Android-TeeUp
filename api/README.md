# TeeUp API

ASP.NET Core Web API for TeeUp, backed by Azure Database for PostgreSQL via
EF Core. Layered as controllers → services → repositories.

## Requirements

- .NET 10 SDK
- PostgreSQL (Azure Database for PostgreSQL in production)

## Configuration

Set the connection string and Firebase project id via environment variables
or `appsettings.Development.json` (gitignored):

```
ConnectionStrings__Default=Host=...;Database=teeup;Username=...;Password=...
Firebase__ProjectId=<firebase-project-id>
```

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

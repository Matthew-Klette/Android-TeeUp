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

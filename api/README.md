# TeeUp API

ASP.NET Core Web API for TeeUp, backed by Azure Database for PostgreSQL via
EF Core. Layered as controllers → services → repositories.

## Requirements

- .NET 8 SDK
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

## Run

```
dotnet run --project TeeUp.Api
```

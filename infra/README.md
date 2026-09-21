# TeeUp Postgres — Azure deployment (EME-309)

Provisions a real, always-on Azure Database for PostgreSQL Flexible Server
(v16) for the deployed API to use, instead of Brandon's local Homebrew
Postgres.

## What this does

`deploy-postgres.sh` + `postgres.bicep`:

1. Provisions a Flexible Server, Burstable B1ms, PostgreSQL 16, with the
   `teeup` database.
2. Opens the firewall to Azure services (so Brandon's App Service can reach
   it) and to your current IP (for migrations/verification).
3. Creates a dedicated `teeup_api` login (not the server admin) and grants
   it privileges scoped to the `teeup` database — never hand the admin
   account to the API.
4. Runs `dotnet ef database update` against the new server so every
   migration in `api/TeeUp.Api/Data/Migrations/` is applied.
5. Seeds one `Courses` row (`seed-course.sql`) so the API isn't returning
   empty data purely because the DB is unseeded.
6. Prints the connection string for you to hand to Brandon.

## Prerequisites

- Azure CLI (`az login` first, subscription with permission to create
  resources).
- `psql`.
- `dotnet-ef` global tool: `dotnet tool install --global dotnet-ef`.

## Run it

```bash
RESOURCE_GROUP=teeup-poe-rg SERVER_NAME=teeup-poe-pg ./infra/deploy-postgres.sh
```

`SERVER_NAME` must be globally unique across Azure. You'll be prompted
interactively for the admin password and the `teeup_api` password — neither
is ever written to disk, logged, or echoed.

## Handoff

The script prints a connection string in the format:

```
Host=<server>.postgres.database.azure.com;Port=5432;Database=teeup;Username=teeup_api;Password=<password>;SSL Mode=Require;Trust Server Certificate=true
```

Send it to Brandon over a private channel (Discord/WhatsApp/etc.) for his
App Service's connection string app setting. **Never** paste it into a
Linear comment, GitHub issue/PR, or commit — that's what turned into an
incident on EME-301, where a real DB password ended up in
`appsettings.Development.json`.

## Locking down networking further (optional)

The default (`allowAzureServices=true` in `postgres.bicep`) is the simplest
path but allows any Azure service, not just Brandon's App Service. To
restrict it: get the App Service's outbound IPs (Azure Portal → App Service
→ Networking → Outbound IPs), remove the `allowAzureServicesRule` resource
in `postgres.bicep`, and add a firewall rule per outbound IP instead.

## Re-running / idempotency

- The Bicep template is idempotent — re-running `deploy-postgres.sh` against
  the same `SERVER_NAME` updates the existing server rather than duplicating
  it.
- The `teeup_api` role creation is guarded (`IF NOT EXISTS`), so re-running
  is safe.
- `seed-course.sql` only inserts if `Courses` is empty.

## Out of scope

Automated backups are on by default (Azure default retention). No read
replicas, HA, or other production-grade resilience — not needed for this
POE.

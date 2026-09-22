# TeeUp Postgres — Supabase deployment (EME-309)

Provisions a real, always-on Postgres database on Supabase for the deployed
API to use, instead of Brandon's local Homebrew Postgres.

Originally scoped as an Azure Database for PostgreSQL Flexible Server; no
Azure subscription was available, so this moved to Supabase's free tier
instead. Brandon's API still deploys to Azure App Service (EME-310) — only
the database's host changed.

## What this does

`deploy-postgres.sh`:

1. Provisions a Supabase project (Postgres 17 — Supabase doesn't offer a
   version picker, so this isn't v16 like local Homebrew `postgresql@16`).
2. Creates a dedicated `teeup_api` login (not the project's admin `postgres`
   role) and grants it `SELECT`/`INSERT`/`UPDATE`/`DELETE` on all
   current and future tables/sequences in `public` — never hand the admin
   account to the API.
3. Runs `dotnet ef database update` against the new project so every
   migration in `api/TeeUp.Api/Data/Migrations/` is applied.
4. Seeds one `Courses` row (`seed-course.sql`) so the API isn't returning
   empty data purely because the DB is unseeded.
5. Prints both the direct connection string (for migrations/`psql`) and the
   pooler connection string template (for App Service) to hand to Brandon.

Supabase has a single default database per project (`postgres`) rather than
Azure's ability to create additional databases — since this project is
dedicated solely to TeeUp, the schema lives in `postgres`'s `public` schema.
No firewall/IP allowlisting step exists either: Supabase's pooler is reachable
publicly by default, secured by role credentials + SSL (Supabase's optional
Network Restrictions add-on is not used here).

## Prerequisites

- Supabase CLI (`supabase login` first; org membership with permission to
  create projects — check with `supabase orgs list`).
- `psql`.
- `dotnet-ef` global tool: `dotnet tool install --global dotnet-ef`.

## Run it

```bash
ORG_ID=<your-org-id> PROJECT_NAME=teeup-poe REGION=eu-central-1 ./infra/deploy-postgres.sh
```

`PROJECT_NAME` should be unique within your org. You'll be prompted
interactively for the project's `postgres` password and the `teeup_api`
password — neither is ever written to disk, logged, or echoed.

## Handoff

The script prints:

- A **direct connection string** (IPv6, migrations/`psql` only):
  ```
  Host=db.<ref>.supabase.co;Port=5432;Database=postgres;Username=postgres;Password=<password>;SSL Mode=Require;Trust Server Certificate=true
  ```
- A **pooler connection string template** for App Service (IPv4, App Service
  needs the Supavisor session pooler, not the direct connection):
  ```
  Host=aws-<N>-<region>.pooler.supabase.com;Port=5432;Database=postgres;Username=teeup_api.<ref>;Password=<password>;SSL Mode=Require;Trust Server Certificate=true
  ```
  The pooler cluster index (`aws-0`, `aws-1`, ...) is per-project and can't be
  derived from the region — confirm it by testing the connection with `psql`,
  or copy it from **Project → Connect → Session pooler** in the Supabase
  dashboard. A wrong index fails with `FATAL: (ENOTFOUND) tenant/user ... not
  found`, which reads like a bad password but isn't.

Send whichever string you hand off to Brandon over a private channel
(Discord/WhatsApp/etc.) for his App Service's connection string app setting.
**Never** paste it into a Linear comment, GitHub issue/PR, or commit — that's
what turned into an incident on EME-301, where a real DB password ended up in
`appsettings.Development.json`.

## Re-running / idempotency

- `supabase projects create` always creates a new project — it is not
  idempotent the way the old Bicep template was. Re-running this script with
  the same `PROJECT_NAME` creates a second project; there's no "update
  existing project" equivalent via the CLI. Reuse an existing project by
  connecting to it directly instead of re-running this script.
- The `teeup_api` role creation is guarded (`IF NOT EXISTS`), so re-running
  the role/grants/migrations/seed portion against the same project is safe.
- `seed-course.sql` only inserts if `Courses` is empty.

## Known deviations from the original Azure-based plan

- Postgres 17, not 16.
- Single `postgres` database (with a dedicated project), not a separate
  `teeup` database.
- No firewall/IP allowlisting step — not offered on Supabase's free tier by
  default.
- Row Level Security is disabled on all tables (Supabase's advisor flags
  this). Since TeeUp's Android client never talks to Postgres directly — all
  persistence goes through the ASP.NET API via `teeup_api`, not Supabase's
  Data API or client libraries — real-world exposure is likely low, but it's
  an open decision, not yet enforced. See EME-309 for the recommended fix
  (`ALTER ROLE teeup_api WITH BYPASSRLS` + `ENABLE ROW LEVEL SECURITY` on
  each table).

## Out of scope

Automated backups (Supabase enables basic backups by default on paid tiers —
confirm retention if this project is upgraded off the free tier). No read
replicas, HA, or other production-grade resilience — not needed for this POE.

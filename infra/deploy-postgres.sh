#!/usr/bin/env bash
# Provisions the Supabase Postgres project for TeeUp (EME-309), applies EF
# Core migrations, and seeds one Courses row.
#
# Requires: supabase CLI (logged in via `supabase login`), psql, dotnet-ef
# global tool.
#
# Usage:
#   ORG_ID=<your-org-id> PROJECT_NAME=teeup-poe REGION=eu-central-1 ./infra/deploy-postgres.sh
#
# Passwords are read interactively (never echoed, never written to disk) so
# nothing sensitive ends up in shell history or this repo.
set -euo pipefail

ORG_ID="${ORG_ID:?Set ORG_ID (see: supabase orgs list)}"
PROJECT_NAME="${PROJECT_NAME:-teeup-poe}"
REGION="${REGION:-eu-central-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$SCRIPT_DIR/../api/TeeUp.Api"

command -v supabase >/dev/null || { echo "supabase CLI is required." >&2; exit 1; }
command -v psql >/dev/null || { echo "psql is required." >&2; exit 1; }

read -r -s -p "Database password for the project's postgres role (new, not reused from local dev): " DB_PASSWORD
echo
read -r -s -p "Password for the teeup_api application role: " APP_PASSWORD
echo

echo "Creating Supabase project (this can take a minute)..."
CREATE_OUTPUT=$(supabase projects create "$PROJECT_NAME" \
  --org-id "$ORG_ID" \
  --region "$REGION" \
  --db-password "$DB_PASSWORD" \
  --output json)

PROJECT_REF=$(echo "$CREATE_OUTPUT" | grep -o '"id"[^,]*' | head -1 | grep -o '"[a-z0-9]\{20\}"' | tr -d '"')
echo "Project provisioned: $PROJECT_REF"

DIRECT_CONN="Host=db.$PROJECT_REF.supabase.co;Port=5432;Database=postgres;Username=postgres;Password=$DB_PASSWORD;SSL Mode=Require;Trust Server Certificate=true"

echo "Waiting for the database to accept connections..."
until PGPASSWORD="$DB_PASSWORD" psql "host=db.$PROJECT_REF.supabase.co port=5432 dbname=postgres user=postgres sslmode=require" -c "select 1" >/dev/null 2>&1; do
  sleep 5
done

echo "Creating teeup_api role and granting privileges..."
PGPASSWORD="$DB_PASSWORD" psql "host=db.$PROJECT_REF.supabase.co port=5432 dbname=postgres user=postgres sslmode=require" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'teeup_api') THEN
    CREATE ROLE teeup_api WITH LOGIN PASSWORD '$APP_PASSWORD';
  END IF;
END
\$\$;
GRANT CONNECT ON DATABASE postgres TO teeup_api;
GRANT USAGE ON SCHEMA public TO teeup_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO teeup_api;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO teeup_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO teeup_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO teeup_api;
SQL

echo "Applying EF Core migrations..."
(cd "$API_DIR" && dotnet ef database update --connection "$DIRECT_CONN")

echo "Seeding baseline Courses row..."
PGPASSWORD="$DB_PASSWORD" psql "host=db.$PROJECT_REF.supabase.co port=5432 dbname=postgres user=postgres sslmode=require" -f "$SCRIPT_DIR/seed-course.sql"

# App Service and other IPv4-only clients need the Supavisor session pooler,
# not the direct (IPv6-only on the free tier) connection above. The pooler
# host has a per-project cluster index ("aws-0", "aws-1", ...) that can't be
# derived from the region — confirm it by testing the connection, or copy it
# from Project → Connect in the dashboard.
echo
echo "Direct connection (migrations/psql only, IPv6):"
echo "  $DIRECT_CONN"
echo
echo "For App Service (IPv4), use the Supavisor session pooler instead:"
echo "  Host=<aws-N-$REGION>.pooler.supabase.com;Port=5432;Database=postgres;Username=teeup_api.$PROJECT_REF;Password=$APP_PASSWORD;SSL Mode=Require;Trust Server Certificate=true"
echo "  Find <aws-N-$REGION> via Project -> Connect -> Session pooler in the Supabase dashboard,"
echo "  or test aws-0-$REGION / aws-1-$REGION with psql until one authenticates."
echo
echo "Send whichever string you use to Brandon over a private channel (Discord/WhatsApp/etc)."
echo "Do NOT paste it into a Linear comment, GitHub issue, commit, or PR description."

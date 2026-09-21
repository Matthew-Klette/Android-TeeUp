#!/usr/bin/env bash
# Provisions the Azure Postgres Flexible Server for TeeUp (EME-309), applies
# EF Core migrations, and seeds one Courses row.
#
# Requires: az CLI (logged in), psql, dotnet-ef global tool.
#
# Usage:
#   RESOURCE_GROUP=teeup-poe-rg SERVER_NAME=teeup-poe-pg ./infra/deploy-postgres.sh
#
# Passwords are read interactively (never echoed, never written to disk) so
# nothing sensitive ends up in shell history or this repo.
set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:?Set RESOURCE_GROUP}"
LOCATION="${LOCATION:-eastus}"
SERVER_NAME="${SERVER_NAME:?Set SERVER_NAME (must be globally unique)}"
ADMIN_USER="${ADMIN_USER:-teeup_admin}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$SCRIPT_DIR/../api/TeeUp.Api"

command -v az >/dev/null || { echo "az CLI is required." >&2; exit 1; }
command -v psql >/dev/null || { echo "psql is required." >&2; exit 1; }

read -r -s -p "Admin password for $ADMIN_USER (new, not reused from local dev): " ADMIN_PASSWORD
echo
read -r -s -p "Password for the teeup_api application role: " APP_PASSWORD
echo

CALLER_IP="$(curl -s https://ifconfig.me)"
echo "Detected caller IP: $CALLER_IP (added to firewall for migrations/verification)"

az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none

echo "Deploying Postgres Flexible Server (this can take several minutes)..."
DEPLOY_OUTPUT=$(az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --template-file "$SCRIPT_DIR/postgres.bicep" \
  --parameters serverName="$SERVER_NAME" \
               administratorLogin="$ADMIN_USER" \
               administratorLoginPassword="$ADMIN_PASSWORD" \
               callerIpAddress="$CALLER_IP" \
  --query "properties.outputs.serverFqdn.value" -o tsv)

SERVER_FQDN="$DEPLOY_OUTPUT"
echo "Server provisioned: $SERVER_FQDN"

echo "Creating teeup_api role and granting privileges..."
PGPASSWORD="$ADMIN_PASSWORD" psql "host=$SERVER_FQDN port=5432 dbname=teeup user=$ADMIN_USER sslmode=require" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'teeup_api') THEN
    CREATE ROLE teeup_api WITH LOGIN PASSWORD '$APP_PASSWORD';
  END IF;
END
\$\$;
GRANT ALL PRIVILEGES ON DATABASE teeup TO teeup_api;
GRANT ALL ON SCHEMA public TO teeup_api;
SQL

CONN_STRING="Host=$SERVER_FQDN;Port=5432;Database=teeup;Username=teeup_api;Password=$APP_PASSWORD;SSL Mode=Require;Trust Server Certificate=true"

echo "Applying EF Core migrations..."
(cd "$API_DIR" && dotnet ef database update --connection "$CONN_STRING")

echo "Seeding baseline Courses row..."
PGPASSWORD="$APP_PASSWORD" psql "host=$SERVER_FQDN port=5432 dbname=teeup user=teeup_api sslmode=require" -f "$SCRIPT_DIR/seed-course.sql"

cat <<EOF

Done. Server: $SERVER_FQDN

Connection string for Brandon (App Service "TeeUp:ConnectionStrings:Default" app setting):
  $CONN_STRING

Send this to him over a private channel (Discord/WhatsApp/etc). Do NOT paste
it into a Linear comment, GitHub issue, commit, or PR description.
EOF

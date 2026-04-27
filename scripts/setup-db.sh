#!/usr/bin/env bash
set -euo pipefail

DB_USER="${DB_USER:-summerfest}"
DB_NAME="${DB_NAME:-summerfest}"
DB_PASSWORD="${DB_PASSWORD:-summerfest}"
PG_USER="${PG_USER:-postgres}"

echo "Creating PostgreSQL user '$DB_USER' and database '$DB_NAME'..."

psql -U "$PG_USER" -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';" 2>/dev/null || echo "User '$DB_USER' already exists."
psql -U "$PG_USER" -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;" 2>/dev/null || echo "Database '$DB_NAME' already exists."

echo "Done. Connection: postgresql://$DB_USER:$DB_PASSWORD@localhost:5432/$DB_NAME"

echo
echo "Applying migrations..."
"$(dirname "$0")/migrate.sh"

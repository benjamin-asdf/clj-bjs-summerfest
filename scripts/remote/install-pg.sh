#!/bin/sh
# Install + initialize PostgreSQL 15 on Alpine. Idempotent.
# Run on the remote server as root.
set -eu

PG_VERSION=15
DB_USER="${DB_USER:-summerfest}"
DB_NAME="${DB_NAME:-summerfest}"
DB_PASSWORD="${DB_PASSWORD:-summerfest}"

echo "==> Installing postgresql${PG_VERSION}"
# Tolerate cosmetic apk trigger errors (e.g. glibc-bin trigger). We verify the
# binary is available below.
apk add --no-progress \
  "postgresql${PG_VERSION}" \
  "postgresql${PG_VERSION}-client" \
  "postgresql${PG_VERSION}-contrib" \
  "postgresql${PG_VERSION}-openrc" || true

command -v initdb >/dev/null || { echo "initdb missing — install failed"; exit 1; }

PG_DATA=/var/lib/postgresql/${PG_VERSION}/data
mkdir -p "$PG_DATA"
chown -R postgres:postgres /var/lib/postgresql

if [ ! -s "$PG_DATA/PG_VERSION" ]; then
  echo "==> Initializing data dir at $PG_DATA"
  su postgres -c "initdb -D $PG_DATA --auth-local=trust --auth-host=md5 -E UTF8 --locale=C"
else
  echo "==> Data dir already initialized"
fi

# Bind only to localhost; nginx is the public ingress.
PG_CONF="$PG_DATA/postgresql.conf"
if ! grep -q "^listen_addresses *= *'127.0.0.1'" "$PG_CONF"; then
  sed -i "s/^#\?listen_addresses.*/listen_addresses = '127.0.0.1'/" "$PG_CONF"
fi

echo "==> Enabling + starting postgresql service"
rc-update add postgresql default || true
rc-service postgresql start || rc-service postgresql restart

# Wait briefly for the socket to appear.
for _ in 1 2 3 4 5 6 7 8 9 10; do
  su postgres -c "psql -l" >/dev/null 2>&1 && break
  sleep 1
done

echo "==> Ensuring role + database"
su postgres -c "psql -tc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\" | grep -q 1" \
  || su postgres -c "psql -c \"CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';\""

su postgres -c "psql -tc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\" | grep -q 1" \
  || su postgres -c "psql -c \"CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};\""

echo "==> Done."
echo "    Connection: postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:5432/${DB_NAME}"

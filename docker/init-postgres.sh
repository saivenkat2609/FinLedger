#!/bin/bash
# Creates auth_db alongside the default 'ledger' database.
# This script is mounted into /docker-entrypoint-initdb.d/ and runs once on first container start.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE auth_db;
    GRANT ALL PRIVILEGES ON DATABASE auth_db TO $POSTGRES_USER;
EOSQL

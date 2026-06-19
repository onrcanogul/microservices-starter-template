-- DB-per-service: inventory-service owns its own logical database in the shared dev Postgres.
-- Runs once on first volume init (Postgres /docker-entrypoint-initdb.d). If your pg_data volume
-- already exists, create it manually: docker compose exec postgres createdb -U app inventorydb
CREATE DATABASE inventorydb OWNER app;

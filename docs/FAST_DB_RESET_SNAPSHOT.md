# Fast DB Reset via Snapshot & Restore (Environment Reset)

A practical, fast-reset strategy to keep performance/test data under control between Gatling runs. Instead of deleting rows one by one, we reset the environment to a clean baseline by restoring a pre-created database snapshot.

This approach is ideal when:
- The API under test creates/updates lots of data per run
- You need deterministic test starts and consistent metrics
- You can afford resetting the whole test database between runs

> Tip: Use this for dedicated Perf/Staging environments. For shared prod-like environments, prefer tenant-per-run or purge-by-tag patterns.

---

## What you get
- Consistent starting dataset for every run
- O(1) cleanup time regardless of data volume
- Works across many databases (Postgres, MySQL, MongoDB)
- Easy to automate in CI/CD or local scripts

---

## Overview of the workflow
1. Prepare a pristine baseline database with the schema and seed data required for tests
2. Take a baseline snapshot/backup once and store it (artifact or object storage)
3. Before each Gatling run, restore that snapshot to reset the environment fast
4. Run the test
5. Optionally, take a post-run backup for debugging (rare)

---

## Quick start: Postgres (recommended)

### 1) Create a baseline snapshot
Use a throwaway DB (or your test DB after seeding) and generate a compressed dump.

```bash
# Environment variables (adjust accordingly)
export PGHOST=localhost
export PGPORT=5432
export PGUSER=app_user
export PGPASSWORD=app_password
export PGDATABASE=customer_api

# Create a compressed baseline dump (one-time or whenever schema/seed changes)
pg_dump -Fc -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$PGDATABASE" > baseline.dump
```

Store `baseline.dump` in your repo (small DB only), build artifacts, or S3/GCS/Azure Blob. For large DBs, prefer object storage.

### 2) Fast restore before each run

```bash
# Drop and recreate (careful: destructive!)
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$PGDATABASE';"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -c "DROP DATABASE IF EXISTS $PGDATABASE;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -c "CREATE DATABASE $PGDATABASE;"

# Restore from baseline
pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" --no-owner --no-privileges --clean baseline.dump
```

### 3) Optional: even faster patterns
- Create a template database and clone from template:
  ```sql
  -- one-time (after seeding) in psql connected to postgres
  CREATE DATABASE template_perf TEMPLATE customer_api;
  -- per run
  DROP DATABASE IF EXISTS customer_api;
  CREATE DATABASE customer_api TEMPLATE template_perf;
  ```
- Use filesystem-level snapshots (ZFS/Btrfs/LVM) or cloud volume snapshots for near-instant resets.

---

## MySQL/MariaDB

### 1) Snapshot (logical)
```bash
export MYSQL_PWD=app_password
mysqldump -h localhost -P 3306 -u app_user --databases customer_api --single-transaction --quick --routines --triggers > baseline.sql
```

### 2) Restore (before each run)
```bash
export MYSQL_PWD=app_password
mysql -h localhost -P 3306 -u app_user -e "DROP DATABASE IF EXISTS customer_api; CREATE DATABASE customer_api;"
mysql -h localhost -P 3306 -u app_user customer_api < baseline.sql
```

> For large datasets, consider Percona XtraBackup or volume snapshots.

---

## MongoDB

### 1) Snapshot (dump)
```bash
mongodump --host localhost --port 27017 --db customer_api --out mongo-baseline
```

### 2) Restore (before each run)
```bash
# Drop db
echo "db.dropDatabase()" | mongosh "mongodb://localhost:27017/customer_api"
# Restore
mongorestore --host localhost --port 27017 --db customer_api --drop mongo-baseline/customer_api
```

---

## Docker and Docker Compose

If your DB runs via Docker/Compose, run the commands inside the container or bind the client locally.

```bash
# Example: run pg_dump from inside the container
docker exec -e PGPASSWORD=$PGPASSWORD my-postgres \
  pg_dump -Fc -h 127.0.0.1 -U $PGUSER $PGDATABASE > baseline.dump

# Or copy dump into the container and restore inside
docker cp baseline.dump my-postgres:/tmp/baseline.dump
docker exec -e PGPASSWORD=$PGPASSWORD my-postgres \
  pg_restore -h 127.0.0.1 -U $PGUSER -d $PGDATABASE --no-owner --clean /tmp/baseline.dump
```

> For fastest resets, use ephemeral containers and disposable volumes: recreate the DB service with a fresh seeded volume per run.

---

## Kubernetes

- Put baseline backup in a PersistentVolume or object storage
- Use a Kubernetes Job to run the restore pre-step
- Gate your Gatling job/pod on the completion of the restore job

High-level sequence:
1. `restore-db` Job runs, restores from snapshot
2. `gatling-run` Job starts after `restore-db` completes
3. Optionally, a `cleanup` Job can drop schemas or volumes

---

## Integrating with this project

You can plug the reset step before your Gatling Maven call. For example, in a local shell:

```bash
# 1) Reset DB to baseline
./scripts/reset-db-from-snapshot.sh  # <— create this script using the examples above

# 2) Run tests
cd gatling-maven && mvn gatling:test \
  -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
```

Or inline in CI/CD (GitHub Actions sample):

```yaml
- name: Restore DB snapshot
  run: |
    export PGHOST=${{ secrets.PGHOST }}
    export PGPORT=${{ secrets.PGPORT }}
    export PGUSER=${{ secrets.PGUSER }}
    export PGPASSWORD=${{ secrets.PGPASSWORD }}
    export PGDATABASE=${{ secrets.PGDATABASE }}
    pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" --no-owner --clean baseline.dump

- name: Run Gatling
  run: |
    cd gatling-maven
    mvn -q gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
```

---

## Seeding and schema management

Keep your baseline up-to-date by re-generating it when the schema or seed data changes.
- Use migration tools (Liquibase or Flyway) to build the DB, then dump
- Load minimal representative data, not full production volumes
- Version your baseline (e.g., `baseline_v2025-09-27.dump`)

---

## Safety & governance
- Restrict snapshot restore permissions to Perf environments only
- Never restore test backups into production
- Mask/anonymize any sensitive data before creating baselines
- Securely store dumps (encrypt at rest; use secrets for credentials)

---

## Performance tips
- Prefer template DB cloning for Postgres when possible (very fast)
- Keep indexes and statistics updated in the baseline
- Separate storage for WAL/logs to speed restore
- Warm-up runs after restore can stabilize caches before measuring

---

## Troubleshooting
- Restore is slow → Use compressed dumps with `-j` parallel restore (`pg_restore -j 4`)
- Permissions errors → Add `--no-owner --no-privileges` and ensure roles exist
- Locks preventing drop → Terminate sessions before dropping DB (see commands above)
- Data drift between runs → Recreate baseline from the latest schema + seed

---

## Summary
Fast environment resets via snapshot/restore give you:
- Deterministic test starts
- Minimal cleanup time
- Easy automation locally and in CI

Adopt it as the default for this suite, and pair with a periodic baseline refresh to keep parity with your evolving schema and seed data.

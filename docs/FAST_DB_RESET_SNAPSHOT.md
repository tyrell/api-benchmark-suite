# Fast Environment Resets with AWS Snapshots (Recommended)

For large datasets (millions of rows) and complex schemas, the fastest and most reliable way to reset between Gatling runs is to use storage-level snapshots provided by AWS. Instead of deleting rows or replaying logical dumps, we revert the entire database storage to a known-good baseline in seconds to minutes.

This guide prioritizes AWS-native snapshot options first, then lists logical dump approaches as an appendix.

---

## Why snapshots for this project
- O(1) reset time regardless of how much test data was written
- Deterministic starting state for every run → consistent performance metrics
- Scales to multi-million row datasets and complex relational graphs
- Fits dedicated Perf/Staging environments used by this suite

> For shared environments, consider tenant-per-run or purge-by-tag as complementary strategies.

---

## Recommended options on AWS (choose based on your DB type)

1) Aurora MySQL/PostgreSQL – Fast Database Cloning (fastest, copy-on-write)
- Create an Aurora clone in seconds; run tests; drop clone when finished
- Zero data copy upfront (page-level copy-on-write) → ideal for large data

2) Amazon RDS (non‑Aurora) – Restore from DB Snapshot
- Create a baseline DB snapshot once; restore a fresh instance before each run
- Automate endpoint/connection switching via RDS Proxy or Route 53

3) Self‑managed DB on EC2 – EBS Volume Snapshots with Fast Snapshot Restore (FSR)
- Snapshot EBS volumes (data/WAL) once; create new volumes from the snapshot for each run
- Attach, mount, and start DB → environment reset without logical restore

4) Kubernetes on AWS (EKS) – CSI VolumeSnapshot for EBS
- Use VolumeSnapshot/VolumeSnapshotClass with the AWS EBS CSI driver
- Recreate PVCs from snapshots for quick, declarative resets

---

## 1) Aurora fast database cloning (best if you can use Aurora)

Aurora supports near-instant cloning using copy-on-write. You get a full logical copy view without physically duplicating data until pages are modified.

High-level flow:
- Keep a “golden” Aurora cluster up to date (schema + seed data)
- Before each Gatling run, create a clone cluster (seconds)
- Point your app/tests to the clone endpoint
- Drop the clone after the run

Example (Aurora PostgreSQL) via AWS CLI:
```bash
# Variables
SRC_CLUSTER_ARN=arn:aws:rds:us-east-1:123456789012:cluster:customer-api-golden
CLONE_ID=customer-api-clone-$(date +%Y%m%d%H%M)
ENGINE=aurora-postgresql
AZ=us-east-1a

# 1) Create clone cluster
aws rds create-db-cluster \
  --db-cluster-identifier "$CLONE_ID" \
  --source-db-cluster-identifier "$SRC_CLUSTER_ARN" \
  --engine "$ENGINE"

# 2) Add an instance to the clone cluster
aws rds create-db-instance \
  --db-instance-identifier "$CLONE_ID-1" \
  --db-instance-class db.r6g.large \
  --engine "$ENGINE" \
  --db-cluster-identifier "$CLONE_ID" \
  --availability-zone "$AZ"

# Wait for available status, then fetch the reader/writer endpoint
aws rds describe-db-clusters --db-cluster-identifier "$CLONE_ID" \
  --query 'DBClusters[0].Endpoint' --output text

# After tests
aws rds delete-db-instance --db-instance-identifier "$CLONE_ID-1" --skip-final-snapshot
aws rds delete-db-cluster --db-cluster-identifier "$CLONE_ID" --skip-final-snapshot
```

Notes
- Aurora clones are ideal for 3M+ records because they avoid full copies
- You can maintain multiple clones in parallel for concurrent test runs
- Use parameter groups and security groups identical to your golden cluster

---

## 2) RDS snapshots and restore (PostgreSQL/MySQL on RDS)

If you’re on RDS but not using Aurora, rely on DB snapshots.

Baseline snapshot (one-time or whenever schema/seed changes):
```bash
aws rds create-db-snapshot \
  --db-instance-identifier customer-api-perf \
  --db-snapshot-identifier customer-api-baseline
```

Fast reset before each run:
```bash
RUN_ID=$(date +%Y%m%d%H%M)
NEW_ID=customer-api-restore-$RUN_ID

# 1) Restore new instance from baseline snapshot
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier "$NEW_ID" \
  --db-snapshot-identifier customer-api-baseline \
  --db-instance-class db.m6g.large \
  --multi-az \
  --publicly-accessible false

# 2) Wait for available, then fetch endpoint
aws rds describe-db-instances --db-instance-identifier "$NEW_ID" \
  --query 'DBInstances[0].Endpoint.Address' --output text

# 3) Point Gatling/app to the new endpoint for the run
# 4) After tests, delete the instance
aws rds delete-db-instance --db-instance-identifier "$NEW_ID" --skip-final-snapshot
```

Endpoint management strategies
- RDS Proxy: point tests to a stable proxy endpoint; flip target group to the restored instance
- Route 53: use a CNAME that you update to the restored endpoint per run
- Config vars: pass the restored endpoint to Gatling via `-Dapi.base.url` or DB connection env

---

## 3) EC2 self-managed DB with EBS Snapshots + Fast Snapshot Restore

For PostgreSQL/MySQL running on EC2 with EBS, create crash- or application‑consistent EBS snapshots once, then rehydrate new volumes per run.

Snapshot creation (one-time baseline)
1) Make the filesystem/application consistent:
- Postgres (simple): stop the service (`systemctl stop postgresql`) or use `pg_basebackup`/`pg_start_backup` advanced flow
- MySQL: `FLUSH TABLES WITH READ LOCK` (or stop service) to quiesce writes
- Filesystem: optionally `fsfreeze` if needed
2) Create multi-volume snapshots if you separate data and WAL/redo logs.

Using AWS CLI (multi-volume, crash-consistent):
```bash
# Create snapshots for all EBS volumes attached to an instance
aws ec2 create-snapshots \
  --instance-specification InstanceId=i-0123456789abcdef0,ExcludeBootVolume=true \
  --description "customer-api-baseline" \
  --tag-specifications 'ResourceType=snapshot,Tags=[{Key=Name,Value=customer-api-baseline}]'
```

Enable Fast Snapshot Restore (FSR) in the AZs you’ll use:
```bash
aws ec2 enable-fast-snapshot-restores \
  --availability-zones us-east-1a us-east-1b \
  --snapshot-ids snap-aaa snap-bbb
```

Reset before each run (rehydrate volumes):
```bash
# Variables
AZ=us-east-1a
INSTANCE_ID=i-0123456789abcdef0
DEVICE_NAME=/dev/xvdf            # mount point for DB data volume
SNAP_ID=snap-aaa                  # the baseline snapshot for data volume
VOL_TYPE=gp3                      # choose gp3/io2 as needed

# 1) Create a fresh volume from the snapshot
VOL_ID=$(aws ec2 create-volume \
  --availability-zone "$AZ" \
  --snapshot-id "$SNAP_ID" \
  --volume-type "$VOL_TYPE" \
  --query 'VolumeId' --output text)

# 2) Wait for volume to be available, then attach
aws ec2 wait volume-available --volume-ids "$VOL_ID"
aws ec2 attach-volume --volume-id "$VOL_ID" --instance-id "$INSTANCE_ID" --device "$DEVICE_NAME"

# 3) SSH to the instance, mount volume (ensure /etc/fstab is correct), and start DB
#    sudo mount /dev/xvdf /var/lib/postgresql/data
#    sudo systemctl start postgresql
```

If you have separate volumes for WAL/redo logs, repeat the steps per snapshot and attach to their respective devices. With FSR enabled, initialization wait time is minimized.

Cleanup after run:
- Stop DB, detach and delete volumes created for the run
- Or terminate the whole EC2 test instance if ephemeral

Notes
- Prefer gp3/io2 for predictable performance
- Tag all snapshots and volumes (Name=customer-api-baseline, RunId, etc.)
- Consider Ansible/Terraform to automate volume swap and service start/stop

---

## 4) EKS (Kubernetes) with AWS EBS CSI VolumeSnapshot

Define a VolumeSnapshotClass for the AWS EBS CSI driver:
```yaml
apiVersion: snapshot.storage.k8s.io/v1
kind: VolumeSnapshotClass
metadata:
  name: ebs-csi-snapclass
driver: ebs.csi.aws.com
deletionPolicy: Delete
```

Create a snapshot:
```yaml
apiVersion: snapshot.storage.k8s.io/v1
kind: VolumeSnapshot
metadata:
  name: customer-api-baseline
spec:
  volumeSnapshotClassName: ebs-csi-snapclass
  source:
    persistentVolumeClaim:
      name: customer-api-pvc
```

Restore PVC from snapshot before a run:
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: customer-api-pvc-restore
spec:
  storageClassName: gp3
  dataSource:
    name: customer-api-baseline
    kind: VolumeSnapshot
    apiGroup: snapshot.storage.k8s.io
  accessModes: [ "ReadWriteOnce" ]
  resources:
    requests:
      storage: 200Gi
```

Then redeploy your DB StatefulSet to use the restored PVC. Automate this with a pre-run Job and gating your Gatling job on its completion.

---

## Integrating with this project’s Gatling runs

- Run the snapshot-based reset as a pre-step, then execute Maven Gatling tests.
- Pass the fresh DB endpoint/connection in via configuration (system properties or env vars).

Example local sequence:
```bash
# 1) Reset using your chosen AWS method (Aurora clone / RDS restore / EBS volumes)
./scripts/aws-reset-db.sh   # implement calling the AWS CLI flows shown above

# 2) Run tests
cd gatling-maven
mvn gatling:test -Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth
```

In CI/CD, add a pre-run stage that performs the reset and exports the DB endpoint/connection string for the test stage.

---

## Governance, safety, and cost
- Restrict who can create/restore snapshots and clones (IAM least privilege)
- Never point production traffic to clones/restored instances
- Encrypt snapshots/volumes; manage KMS keys and access
- Consider Fast Snapshot Restore costs per AZ; disable when not needed
- Clean up clones, restored instances, and volumes after runs

---

## Performance tips
- Aurora clones scale best for very large datasets (minimal copy upfront)
- Enable Fast Snapshot Restore in the AZs used for EC2/EBS workflows
- Use appropriate EBS volume types (gp3/io2) and tune IOPS/throughput
- Warm-up after reset to stabilize caches before measurements

---

## Troubleshooting
- Slow initialization of volumes → enable FSR or pre-warm by scanning device
- Inconsistent snapshots → ensure app/filesystem quiescence before taking baseline
- Endpoint switching issues → standardize on RDS Proxy or Route 53 CNAME
- Permission errors → verify IAM policies for EBS/RDS/Aurora

---

## Appendix: Logical dump/restore (fallback)

If snapshots aren’t available, use logical backups. These are slower on large datasets but portable.

Postgres
```bash
pg_dump -Fc -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$PGDATABASE" > baseline.dump
pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" --no-owner --no-privileges --clean baseline.dump
```

MySQL/MariaDB
```bash
mysqldump -h localhost -P 3306 -u app_user --databases customer_api --single-transaction --quick --routines --triggers > baseline.sql
mysql -h localhost -P 3306 -u app_user customer_api < baseline.sql
```

MongoDB
```bash
mongodump --host localhost --port 27017 --db customer_api --out mongo-baseline
mongorestore --host localhost --port 27017 --db customer_api --drop mongo-baseline/customer_api
```

These approaches remain useful for developer laptops and small CI runs but are not recommended for 3M+ rows.

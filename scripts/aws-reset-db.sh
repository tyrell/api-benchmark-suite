#!/usr/bin/env bash
# Fast AWS-based DB reset helper for Gatling runs
# Supports: aurora-clone, aurora-clean, rds-restore, rds-clean, ebs-restore, ebs-clean, eks-restore, eks-clean
# Outputs a small JSON summary on success to stdout.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
AWS_REGION_DEFAULT="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
AWS_PROFILE_DEFAULT="${AWS_PROFILE:-}"
OUTPUT_DIR="${SCRIPT_DIR}/.aws-reset"
mkdir -p "$OUTPUT_DIR"

log() { echo "[aws-reset-db] $*" >&2; }
fail() { echo "[aws-reset-db][ERROR] $*" >&2; exit 1; }
need_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }
json_escape() { jq -Rn --arg s "$1" '$s'; }

usage() {
  cat <<'USAGE'
Usage:
  aws-reset-db.sh <command> [options]

Commands:
  aurora-clone     Create a fast Aurora clone from a golden cluster
  aurora-clean     Delete an Aurora clone (instance + cluster)
  rds-restore      Restore an RDS instance from a baseline snapshot
  rds-clean        Delete a restored RDS instance
  ebs-restore      Create & attach EBS volume(s) from snapshot(s) to an EC2 instance
  ebs-clean        Detach & delete EBS volume(s)
  eks-restore      Create PVC from an EBS VolumeSnapshot (EKS)
  eks-clean        Delete a PVC created for restore (EKS)

Global options (env or flags):
  --region REGION        AWS region (defaults to AWS_REGION/AWS_DEFAULT_REGION)
  --profile PROFILE      AWS CLI profile (defaults to AWS_PROFILE)
  --run-id RUN_ID        Identifier to tag resources (defaults to timestamp)

Aurora clone:
  --src-cluster-arn ARN  Source (golden) cluster ARN (required)
  --engine ENGINE        aurora-postgresql | aurora-mysql (default: aurora-postgresql)
  --instance-class CLS   e.g., db.r6g.large (default)
  --az AZ                e.g., us-east-1a (default: from --region + 'a')
  --clone-id ID          Optional. Defaults to customer-api-clone-$RUN_ID

Aurora clean:
  --clone-id ID          Required. The clone identifier to delete

RDS restore:
  --snapshot-id ID       Baseline DB snapshot identifier (required)
  --instance-class CLS   e.g., db.m6g.large
  --multi-az             Create Multi-AZ instance (flag)
  --public               Make instance publicly accessible (flag)
  --new-id ID            Optional. Defaults to customer-api-restore-$RUN_ID

RDS clean:
  --instance-id ID       Required. DB instance identifier to delete

EBS restore:
  --snapshot-id ID       Snapshot ID for data volume (required; repeatable)
  --instance-id ID       EC2 instance to attach to (required)
  --device DEV           Linux device (e.g., /dev/xvdf). Repeat per snapshot
  --az AZ                Availability zone for volumes (required)
  --type TYPE            Volume type (default: gp3)

EBS clean:
  --volume-id ID         Volume ID to delete (repeatable)
  --instance-id ID       Detach from instance (optional; if attached)

EKS restore:
  --namespace NS         Kubernetes namespace (default: default)
  --snapshot-name NAME   VolumeSnapshot name (required)
  --pvc-name NAME        PVC name to create (required)
  --storageclass NAME    StorageClass (default: gp3)
  --size SIZE            PVC size (e.g., 200Gi) (required)

EKS clean:
  --namespace NS         Kubernetes namespace (default: default)
  --pvc-name NAME        PVC name to delete (required)

Examples:
  aws-reset-db.sh aurora-clone --src-cluster-arn arn:aws:rds:...:cluster:golden --region us-east-1
  aws-reset-db.sh rds-restore --snapshot-id customer-api-baseline --region us-east-1
  aws-reset-db.sh ebs-restore --snapshot-id snap-aaa --instance-id i-123 --device /dev/xvdf --az us-east-1a
  aws-reset-db.sh eks-restore --namespace perf --snapshot-name customer-api-baseline --pvc-name customer-api-pvc-restore --size 200Gi
USAGE
}

parse_global() {
  REGION="$AWS_REGION_DEFAULT"; PROFILE="$AWS_PROFILE_DEFAULT";
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --region) REGION="$2"; shift 2 ;;
      --profile) PROFILE="$2"; shift 2 ;;
      --run-id) RUN_ID="$2"; shift 2 ;;
      *) ARGS+=("$1"); shift ;;
    esac
  done
}

aws_cli() {
  local svc_args=("$@")
  [[ -n "${REGION:-}" ]] && svc_args=("--region" "$REGION" "${svc_args[@]}")
  [[ -n "${PROFILE:-}" ]] && svc_args=("--profile" "$PROFILE" "${svc_args[@]}")
  aws "${svc_args[@]}"
}

cmd_aurora_clone() {
  local SRC_ARN="" ENGINE="aurora-postgresql" INSTANCE_CLASS="db.r6g.large" AZ="" CLONE_ID="customer-api-clone-$RUN_ID"
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --src-cluster-arn) SRC_ARN="${args[1]}"; args=("${args[@]:2}") ;;
      --engine) ENGINE="${args[1]}"; args=("${args[@]:2}") ;;
      --instance-class) INSTANCE_CLASS="${args[1]}"; args=("${args[@]:2}") ;;
      --az) AZ="${args[1]}"; args=("${args[@]:2}") ;;
      --clone-id) CLONE_ID="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for aurora-clone: ${args[0]}" ;;
    esac
  done
  [[ -z "$SRC_ARN" ]] && fail "--src-cluster-arn is required"
  [[ -z "$REGION" ]] && fail "--region or AWS_REGION must be set"
  [[ -z "$AZ" ]] && AZ="${REGION}a"
  need_cmd aws; need_cmd jq

  log "Creating Aurora clone cluster $CLONE_ID from $SRC_ARN"
  aws_cli rds create-db-cluster \
    --db-cluster-identifier "$CLONE_ID" \
    --source-db-cluster-identifier "$SRC_ARN" \
    --engine "$ENGINE" >/dev/null

  log "Creating instance ${CLONE_ID}-1 ($INSTANCE_CLASS, $AZ)"
  aws_cli rds create-db-instance \
    --db-instance-identifier "${CLONE_ID}-1" \
    --db-instance-class "$INSTANCE_CLASS" \
    --engine "$ENGINE" \
    --db-cluster-identifier "$CLONE_ID" \
    --availability-zone "$AZ" >/dev/null

  log "Waiting for instance available..."
  aws_cli rds wait db-instance-available --db-instance-identifier "${CLONE_ID}-1"

  local ENDPOINT
  ENDPOINT=$(aws_cli rds describe-db-clusters --db-cluster-identifier "$CLONE_ID" \
    --query 'DBClusters[0].Endpoint' --output text)

  local out="$OUTPUT_DIR/aurora-clone-$RUN_ID.json"
  jq -n --arg method aurora-clone --arg runId "$RUN_ID" --arg cloneId "$CLONE_ID" --arg endpoint "$ENDPOINT" '{method:$method,runId:$runId,cloneId:$cloneId,endpoint:$endpoint}' | tee "$out"
}

cmd_aurora_clean() {
  local CLONE_ID=""
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --clone-id) CLONE_ID="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for aurora-clean: ${args[0]}" ;;
    esac
  done
  [[ -z "$CLONE_ID" ]] && fail "--clone-id is required"
  need_cmd aws
  log "Deleting instance ${CLONE_ID}-1"
  aws_cli rds delete-db-instance --db-instance-identifier "${CLONE_ID}-1" --skip-final-snapshot >/dev/null || true
  log "Deleting cluster $CLONE_ID"
  aws_cli rds delete-db-cluster --db-cluster-identifier "$CLONE_ID" --skip-final-snapshot >/dev/null || true
  jq -n --arg method aurora-clean --arg cloneId "$CLONE_ID" '{method:$method,cloneId:$cloneId,deleted:true}'
}

cmd_rds_restore() {
  local SNAPSHOT_ID="" INSTANCE_CLASS="db.m6g.large" NEW_ID="customer-api-restore-$RUN_ID" MULTI_AZ=false PUBLIC=false
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --snapshot-id) SNAPSHOT_ID="${args[1]}"; args=("${args[@]:2}") ;;
      --instance-class) INSTANCE_CLASS="${args[1]}"; args=("${args[@]:2}") ;;
      --new-id) NEW_ID="${args[1]}"; args=("${args[@]:2}") ;;
      --multi-az) MULTI_AZ=true; args=("${args[@]:1}") ;;
      --public) PUBLIC=true; args=("${args[@]:1}") ;;
      *) fail "Unknown option for rds-restore: ${args[0]}" ;;
    esac
  done
  [[ -z "$SNAPSHOT_ID" ]] && fail "--snapshot-id is required"
  need_cmd aws; need_cmd jq
  log "Restoring RDS instance $NEW_ID from snapshot $SNAPSHOT_ID"
  local extra=( )
  $MULTI_AZ && extra+=(--multi-az)
  $PUBLIC && extra+=(--publicly-accessible)
  aws_cli rds restore-db-instance-from-db-snapshot \
    --db-instance-identifier "$NEW_ID" \
    --db-snapshot-identifier "$SNAPSHOT_ID" \
    --db-instance-class "$INSTANCE_CLASS" \
    "${extra[@]}" >/dev/null

  log "Waiting for instance available..."
  aws_cli rds wait db-instance-available --db-instance-identifier "$NEW_ID"
  local ENDPOINT
  ENDPOINT=$(aws_cli rds describe-db-instances --db-instance-identifier "$NEW_ID" --query 'DBInstances[0].Endpoint.Address' --output text)
  jq -n --arg method rds-restore --arg runId "$RUN_ID" --arg instanceId "$NEW_ID" --arg endpoint "$ENDPOINT" '{method:$method,runId:$runId,instanceId:$instanceId,endpoint:$endpoint}' | tee "$OUTPUT_DIR/rds-restore-$RUN_ID.json"
}

cmd_rds_clean() {
  local INSTANCE_ID=""
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --instance-id) INSTANCE_ID="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for rds-clean: ${args[0]}" ;;
    esac
  done
  [[ -z "$INSTANCE_ID" ]] && fail "--instance-id is required"
  need_cmd aws
  log "Deleting RDS instance $INSTANCE_ID"
  aws_cli rds delete-db-instance --db-instance-identifier "$INSTANCE_ID" --skip-final-snapshot >/dev/null || true
  jq -n --arg method rds-clean --arg instanceId "$INSTANCE_ID" '{method:$method,instanceId:$instanceId,deleted:true}'
}

cmd_ebs_restore() {
  local AZ="" INSTANCE_ID="" VOL_TYPE="gp3" SNAP_IDS=() DEVICES=()
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --snapshot-id) SNAP_IDS+=("${args[1]}"); args=("${args[@]:2}") ;;
      --instance-id) INSTANCE_ID="${args[1]}"; args=("${args[@]:2}") ;;
      --device) DEVICES+=("${args[1]}"); args=("${args[@]:2}") ;;
      --az) AZ="${args[1]}"; args=("${args[@]:2}") ;;
      --type) VOL_TYPE="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for ebs-restore: ${args[0]}" ;;
    esac
  done
  [[ -z "$AZ" ]] && fail "--az is required"
  [[ -z "$INSTANCE_ID" ]] && fail "--instance-id is required"
  [[ ${#SNAP_IDS[@]} -eq 0 ]] && fail "At least one --snapshot-id is required"
  [[ ${#DEVICES[@]} -ne ${#SNAP_IDS[@]} ]] && fail "Provide one --device per --snapshot-id"
  need_cmd aws; need_cmd jq

  local VOL_IDS=()
  for i in "${!SNAP_IDS[@]}"; do
    local snap="${SNAP_IDS[$i]}" dev="${DEVICES[$i]}"
    log "Creating volume from snapshot $snap in $AZ"
    local vol
    vol=$(aws_cli ec2 create-volume --availability-zone "$AZ" --snapshot-id "$snap" --volume-type "$VOL_TYPE" --query 'VolumeId' --output text)
    VOL_IDS+=("$vol")
    log "Waiting for $vol available..."
    aws_cli ec2 wait volume-available --volume-ids "$vol"
    log "Attaching $vol to $INSTANCE_ID at $dev"
    aws_cli ec2 attach-volume --volume-id "$vol" --instance-id "$INSTANCE_ID" --device "$dev" >/dev/null
  done
  jq -n --arg method ebs-restore --arg runId "$RUN_ID" --arg instanceId "$INSTANCE_ID" --argjson volumes "$(printf '%s\n' "${VOL_IDS[@]}" | jq -R . | jq -s .)" '{method:$method,runId:$runId,instanceId:$instanceId,volumes:$volumes}' | tee "$OUTPUT_DIR/ebs-restore-$RUN_ID.json"
}

cmd_ebs_clean() {
  local VOL_IDS=() INSTANCE_ID=""
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --volume-id) VOL_IDS+=("${args[1]}"); args=("${args[@]:2}") ;;
      --instance-id) INSTANCE_ID="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for ebs-clean: ${args[0]}" ;;
    esac
  done
  [[ ${#VOL_IDS[@]} -eq 0 ]] && fail "Provide at least one --volume-id"
  need_cmd aws
  for v in "${VOL_IDS[@]}"; do
    if [[ -n "$INSTANCE_ID" ]]; then
      log "Detaching $v from $INSTANCE_ID"
      aws_cli ec2 detach-volume --volume-id "$v" --instance-id "$INSTANCE_ID" >/dev/null || true
      aws_cli ec2 wait volume-available --volume-ids "$v" || true
    fi
    log "Deleting volume $v"
    aws_cli ec2 delete-volume --volume-id "$v" >/dev/null || true
  done
  jq -n --arg method ebs-clean --argjson volumes "$(printf '%s\n' "${VOL_IDS[@]}" | jq -R . | jq -s .)" '{method:$method,volumes:$volumes,deleted:true}'
}

cmd_eks_restore() {
  local NAMESPACE="default" SNAP_NAME="" PVC_NAME="" STORAGECLASS="gp3" SIZE=""
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --namespace) NAMESPACE="${args[1]}"; args=("${args[@]:2}") ;;
      --snapshot-name) SNAP_NAME="${args[1]}"; args=("${args[@]:2}") ;;
      --pvc-name) PVC_NAME="${args[1]}"; args=("${args[@]:2}") ;;
      --storageclass) STORAGECLASS="${args[1]}"; args=("${args[@]:2}") ;;
      --size) SIZE="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for eks-restore: ${args[0]}" ;;
    esac
  done
  [[ -z "$SNAP_NAME" ]] && fail "--snapshot-name is required"
  [[ -z "$PVC_NAME" ]] && fail "--pvc-name is required"
  [[ -z "$SIZE" ]] && fail "--size is required"
  need_cmd kubectl; need_cmd jq

  local manifest
  manifest=$(mktemp)
  cat >"$manifest" <<YAML
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: $PVC_NAME
  namespace: $NAMESPACE
spec:
  storageClassName: $STORAGECLASS
  dataSource:
    name: $SNAP_NAME
    kind: VolumeSnapshot
    apiGroup: snapshot.storage.k8s.io
  accessModes: [ "ReadWriteOnce" ]
  resources:
    requests:
      storage: $SIZE
YAML

  kubectl apply -f "$manifest" >/dev/null
  rm -f "$manifest"

  log "Waiting for PVC bound..."
  for i in {1..60}; do
    phase=$(kubectl get pvc "$PVC_NAME" -n "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || echo "")
    [[ "$phase" == "Bound" ]] && break
    sleep 5
  done

  phase=$(kubectl get pvc "$PVC_NAME" -n "$NAMESPACE" -o jsonpath='{.status.phase}')
  [[ "$phase" == "Bound" ]] || fail "PVC did not reach Bound state"

  jq -n --arg method eks-restore --arg namespace "$NAMESPACE" --arg pvc "$PVC_NAME" '{method:$method,namespace:$namespace,pvc:$pvc,status:"Bound"}' | tee "$OUTPUT_DIR/eks-restore-$RUN_ID.json"
}

cmd_eks_clean() {
  local NAMESPACE="default" PVC_NAME=""
  local args=("${ARGS[@]}")
  while [[ ${#args[@]} -gt 0 ]]; do
    case "${args[0]}" in
      --namespace) NAMESPACE="${args[1]}"; args=("${args[@]:2}") ;;
      --pvc-name) PVC_NAME="${args[1]}"; args=("${args[@]:2}") ;;
      *) fail "Unknown option for eks-clean: ${args[0]}" ;;
    esac
  done
  [[ -z "$PVC_NAME" ]] && fail "--pvc-name is required"
  need_cmd kubectl
  kubectl delete pvc "$PVC_NAME" -n "$NAMESPACE" --ignore-not-found
  jq -n --arg method eks-clean --arg namespace "$NAMESPACE" --arg pvc "$PVC_NAME" '{method:$method,namespace:$namespace,pvc:$pvc,deleted:true}'
}

main() {
  [[ $# -lt 1 ]] && { usage; exit 1; }
  local CMD="$1"; shift || true
  ARGS=()
  parse_global "$@"

  case "$CMD" in
    aurora-clone)  cmd_aurora_clone ;;
    aurora-clean)  cmd_aurora_clean ;;
    rds-restore)   cmd_rds_restore  ;;
    rds-clean)     cmd_rds_clean    ;;
    ebs-restore)   cmd_ebs_restore  ;;
    ebs-clean)     cmd_ebs_clean    ;;
    eks-restore)   cmd_eks_restore  ;;
    eks-clean)     cmd_eks_clean    ;;
    -h|--help|help) usage; exit 0 ;;
    *) usage; fail "Unknown command: $CMD" ;;
  esac
}

main "$@"

#!/usr/bin/env bash
# Orchestrate: AWS DB reset -> Gatling run -> Cleanup
# Requires: scripts/aws-reset-db.sh, jq, (kubectl for eks), mvn

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESET_SCRIPT="$SCRIPT_DIR/aws-reset-db.sh"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 2; }; }
need jq

METHOD=""
RESET_ARGS=()
GATLING_CMD=("mvn" "-q" "gatling:test" "-Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth")
SEEN_SEP=false

usage() {
  cat <<'USAGE'
Usage:
  run-gatling-with-aws-reset.sh --method {aurora|rds|ebs|eks} [reset-args ...] -- [gatling command]

Examples:
  # Aurora clone -> run default Gatling -> cleanup
  ./scripts/run-gatling-with-aws-reset.sh --method aurora \
    -- -- -Dgatling.users=50 -Dload.ramp.up.duration=30

  # RDS restore from snapshot with explicit region
  ./scripts/run-gatling-with-aws-reset.sh --method rds \
    --snapshot-id customer-api-baseline --region us-east-1 \
    -- -- -Dgatling.users=100 -Dperformance.success.rate.threshold=95.0

  # EBS volumes from snapshots (two volumes -> two devices)
  ./scripts/run-gatling-with-aws-reset.sh --method ebs \
    --snapshot-id snap-aaa --device /dev/xvdf \
    --snapshot-id snap-bbb --device /dev/xvdg \
    --instance-id i-0123456789abcdef0 --az us-east-1a \
    -- -- -Dgatling.users=200

  # EKS PVC from VolumeSnapshot
  ./scripts/run-gatling-with-aws-reset.sh --method eks \
    --namespace perf --snapshot-name customer-api-baseline \
    --pvc-name customer-api-pvc-restore --size 200Gi \
    -- -- -Dgatling.users=50

Notes:
- The arguments before -- go to the reset flow; after -- go to the Gatling Maven cmd.
- Defaults to ApiBenchmarkSimulationWithOAuth if no Gatling cmd provided.
USAGE
}

# Parse CLI
if [[ $# -eq 0 ]]; then usage; exit 1; fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    --method)
      METHOD="$2"; shift 2 ;;
    --)
      SEEN_SEP=true; shift
      # Capture remaining args for Gatling; decide later whether to prepend mvn
      GATLING_CMD=( "$@" )
      break ;;
    -h|--help|help)
      usage; exit 0 ;;
    *)
      RESET_ARGS+=("$1"); shift ;;
  esac
done

[[ -z "$METHOD" ]] && { echo "--method is required" >&2; usage; exit 1; }
[[ -x "$RESET_SCRIPT" ]] || { echo "Reset script not found: $RESET_SCRIPT" >&2; exit 1; }

CLEANUP_CMD=()
JSON_OUT=""

run_reset() {
  case "$METHOD" in
    aurora)
      JSON_OUT=$("$RESET_SCRIPT" aurora-clone "${RESET_ARGS[@]}" | tee /dev/stderr)
      CLONE_ID=$(echo "$JSON_OUT" | jq -r '.cloneId')
      [[ -z "$CLONE_ID" || "$CLONE_ID" == "null" ]] && { echo "Failed to get cloneId" >&2; exit 1; }
      CLEANUP_CMD=("$RESET_SCRIPT" aurora-clean --clone-id "$CLONE_ID")
      ;;
    rds)
      JSON_OUT=$("$RESET_SCRIPT" rds-restore "${RESET_ARGS[@]}" | tee /dev/stderr)
      INSTANCE_ID=$(echo "$JSON_OUT" | jq -r '.instanceId')
      [[ -z "$INSTANCE_ID" || "$INSTANCE_ID" == "null" ]] && { echo "Failed to get instanceId" >&2; exit 1; }
      CLEANUP_CMD=("$RESET_SCRIPT" rds-clean --instance-id "$INSTANCE_ID")
      ;;
    ebs)
      JSON_OUT=$("$RESET_SCRIPT" ebs-restore "${RESET_ARGS[@]}" | tee /dev/stderr)
      INSTANCE_ID=$(echo "$JSON_OUT" | jq -r '.instanceId // ""')
      VOL_IDS=$(echo "$JSON_OUT" | jq -r '.volumes[]?')
      [[ -z "$VOL_IDS" ]] && { echo "No volume IDs returned" >&2; exit 1; }
      CLEANUP_CMD=("$RESET_SCRIPT" ebs-clean)
      for v in $VOL_IDS; do CLEANUP_CMD+=(--volume-id "$v"); done
      [[ -n "$INSTANCE_ID" ]] && CLEANUP_CMD+=(--instance-id "$INSTANCE_ID")
      ;;
    eks)
      JSON_OUT=$("$RESET_SCRIPT" eks-restore "${RESET_ARGS[@]}" | tee /dev/stderr)
      PVC_NAME=$(echo "$JSON_OUT" | jq -r '.pvc')
      NAMESPACE=$(echo "$JSON_OUT" | jq -r '.namespace')
      [[ -z "$PVC_NAME" || "$PVC_NAME" == "null" ]] && { echo "Failed to get PVC name" >&2; exit 1; }
      CLEANUP_CMD=("$RESET_SCRIPT" eks-clean --namespace "$NAMESPACE" --pvc-name "$PVC_NAME")
      ;;
    *)
      echo "Unknown method: $METHOD" >&2; exit 1 ;;
  esac
}

cleanup() {
  if [[ ${#CLEANUP_CMD[@]} -gt 0 ]]; then
    echo "[run-with-reset] Cleaning up: ${CLEANUP_CMD[*]}" >&2
    "${CLEANUP_CMD[@]}" || echo "[run-with-reset] Cleanup command failed (ignored)" >&2
  fi
}

trap cleanup EXIT

run_reset

# Optionally export endpoint for downstream usage
ENDPOINT=$(echo "$JSON_OUT" | jq -r '.endpoint // empty')
if [[ -n "$ENDPOINT" && "$ENDPOINT" != "null" ]]; then
  echo "[run-with-reset] Endpoint: $ENDPOINT" >&2
  export RESET_DB_ENDPOINT="$ENDPOINT"
fi

# Change to Gatling project dir if command starts with mvn and not specifying -f
cd "$ROOT_DIR/gatling-maven"

# If user provided only flags after --, prepend default mvn command
if [[ ${#GATLING_CMD[@]} -gt 0 ]]; then
  if [[ ${GATLING_CMD[0]} == -* ]]; then
    GATLING_CMD=("mvn" "-q" "gatling:test" "-Dgatling.simulationClass=co.tyrell.gatling.simulation.ApiBenchmarkSimulationWithOAuth" "${GATLING_CMD[@]}")
  fi
fi

echo "[run-with-reset] Running: ${GATLING_CMD[*]}" >&2
"${GATLING_CMD[@]}"

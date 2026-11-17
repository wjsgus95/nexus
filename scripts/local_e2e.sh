#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT="${PORT:-18080}"
JAR_PATH="build/libs/nexus-0.1.0-all.jar"
PROFILE="configs/catalog/kr/profile.local.yaml"
REPORT_BASE="reports"

# Ensure Gradle has a compatible JDK (17+) to run the build
if [[ -z "${JAVA_HOME:-}" ]] && [[ -d "/opt/homebrew/opt/openjdk@17" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo ">>> Building fatJar..."
./gradlew fatJar --console=plain -q

# Resolve Java 21 binary for running the JAR
JAVA_BIN="${JAVA_21_BIN:-}"
if [[ -z "$JAVA_BIN" ]]; then
  TOOLCHAIN_JAVA="$(find "$HOME/.gradle/jdks" -path "*/jdk-21*/Contents/Home/bin/java" -o -path "*/jdk-21*/bin/java" 2>/dev/null | head -n 1 || true)"
  if [[ -n "$TOOLCHAIN_JAVA" ]]; then
    JAVA_BIN="$TOOLCHAIN_JAVA"
  else
    JAVA_BIN="java"
  fi
fi

python3 scripts/local_server.py "$PORT" >/dev/null 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true; wait "$SERVER_PID" 2>/dev/null || true' EXIT

for _ in {1..50}; do
  if curl -sf "http://127.0.0.1:${PORT}/target/actuator/health" >/dev/null; then
    break
  fi
  sleep 0.1
done

if ! curl -sf "http://127.0.0.1:${PORT}/target/actuator/health" >/dev/null; then
  echo "ERROR: Local mock server failed to start on port ${PORT}" >&2
  exit 1
fi

run_nexus() {
  echo ""
  echo ">>> nexus $*"
  "$JAVA_BIN" -jar "$JAR_PATH" "$@"
}

run_nexus doctor --offline -p "$PROFILE"
run_nexus smoke -p "$PROFILE" --output-dir "${REPORT_BASE}/smoke"
run_nexus regression -p "$PROFILE" --output-dir "${REPORT_BASE}/regression"

for stage in smoke regression; do
  stage_upper="$(printf '%s' "$stage" | tr '[:lower:]' '[:upper:]')"
  echo ""
  echo "=== ${stage_upper} report.json ==="
  cat "${REPORT_BASE}/${stage}/report.json"
  echo ""
done

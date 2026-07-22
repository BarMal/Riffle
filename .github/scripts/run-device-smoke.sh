#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: run-device-smoke.sh --serial SERIAL --candidate-sha SHA --build-identity ID \
  --validator NAME --scenario ID --result pass|fail|blocked --form-factor VALUE \
  --window-mode VALUE --install-type VALUE --output PATH [--orientation portrait|landscape] \
  [--posture cover|flat|book|tabletop] [--apk PATH] [--known-limitation TEXT]

Records only public Android build metadata and supplied candidate/test metadata. It never records
the device serial, accounts, application lists, notification contents, screenshots, or logs.
EOF
}

serial=""; candidate_sha=""; build_identity=""; validator=""; scenario=""; result=""
form_factor=""; window_mode=""; orientation=""; posture=""; install_type=""; output=""; apk=""; known_limitation=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="$2"; shift 2 ;;
    --candidate-sha) candidate_sha="$2"; shift 2 ;;
    --build-identity) build_identity="$2"; shift 2 ;;
    --validator) validator="$2"; shift 2 ;;
    --scenario) scenario="$2"; shift 2 ;;
    --result) result="$2"; shift 2 ;;
    --form-factor) form_factor="$2"; shift 2 ;;
    --window-mode) window_mode="$2"; shift 2 ;;
    --orientation) orientation="$2"; shift 2 ;;
    --posture) posture="$2"; shift 2 ;;
    --install-type) install_type="$2"; shift 2 ;;
    --output) output="$2"; shift 2 ;;
    --apk) apk="$2"; shift 2 ;;
    --known-limitation) known_limitation="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

for value in serial candidate_sha build_identity validator scenario result form_factor window_mode install_type output; do
  [[ -n "${!value}" ]] || { echo "Missing --${value//_/-}" >&2; exit 2; }
done
[[ "$candidate_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "Candidate SHA must be lowercase and 40 characters." >&2; exit 2; }
[[ "$result" =~ ^(pass|fail|blocked)$ ]] || { echo "Result must be pass, fail, or blocked." >&2; exit 2; }
[[ -z "$orientation" || "$orientation" =~ ^(portrait|landscape)$ ]] || { echo "Orientation must be portrait or landscape." >&2; exit 2; }
[[ -z "$posture" || "$posture" =~ ^(cover|flat|book|tabletop)$ ]] || { echo "Posture must be cover, flat, book, or tabletop." >&2; exit 2; }
command -v adb >/dev/null || { echo "adb is required." >&2; exit 2; }
command -v jq >/dev/null || { echo "jq is required." >&2; exit 2; }

adb -s "$serial" wait-for-device
if [[ -n "$apk" ]]; then
  adb -s "$serial" install -r "$apk" >/dev/null
fi
adb -s "$serial" shell pm path com.riffle.app >/dev/null

getprop() { adb -s "$serial" shell getprop "$1" | tr -d '\r'; }
manufacturer="$(getprop ro.product.manufacturer)"
model="$(getprop ro.product.model)"
api="$(getprop ro.build.version.sdk)"
build="$(getprop ro.build.version.incremental)"
timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "$(dirname "$output")"
jq -n \
  --arg sha "$candidate_sha" \
  --arg buildIdentity "$build_identity" \
  --arg timestamp "$timestamp" \
  --arg scenario "$scenario" \
  --arg result "$result" \
  --arg validator "$validator" \
  --arg limitation "$known_limitation" \
  --arg manufacturer "$manufacturer" \
  --arg model "$model" \
  --argjson api "$api" \
  --arg build "$build" \
  --arg formFactor "$form_factor" \
  --arg windowMode "$window_mode" \
  --arg orientation "$orientation" \
  --arg posture "$posture" \
  --arg installType "$install_type" \
  '{
    schemaVersion: 1,
    candidate: {commitSha: $sha, buildIdentity: $buildIdentity, generatedAt: $timestamp},
    runs: [{
      scenarioId: $scenario,
      evidenceType: "physical-device",
      result: $result,
      validator: $validator,
      timestamp: $timestamp,
      knownLimitation: $limitation,
      device: {
        manufacturer: $manufacturer,
        model: $model,
        androidApi: $api,
        build: $build,
        formFactor: $formFactor,
        windowMode: $windowMode,
        installType: $installType
      } +
        (if $orientation == "" then {} else {orientation: $orientation} end) +
        (if $posture == "" then {} else {posture: $posture} end)
    }]
  }' > "$output"

echo "Wrote privacy-safe device evidence to $output"

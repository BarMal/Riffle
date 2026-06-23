#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_TOOLS_DIR/bin/sdkmanager"

mkdir -p "$ANDROID_SDK_ROOT" /home/vscode/.npm-global /home/vscode/.local/bin

# Keep globally installed npm tools writable by the non-root vscode user.
npm config set prefix /home/vscode/.npm-global

sudo apt-get update
sudo apt-get install -y --no-install-recommends \
  unzip \
  zip \
  curl \
  ca-certificates \
  jq
sudo rm -rf /var/lib/apt/lists/*

if [ ! -x "$SDKMANAGER" ]; then
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$tmp_dir/android-commandlinetools.zip"
  unzip -q "$tmp_dir/android-commandlinetools.zip" -d "$tmp_dir"
  sudo mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  sudo rm -rf "$CMDLINE_TOOLS_DIR"
  sudo mv "$tmp_dir/cmdline-tools" "$CMDLINE_TOOLS_DIR"
  sudo chown -R vscode:vscode "$ANDROID_SDK_ROOT"
fi

yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "cmdline-tools;latest"

# Install the official OpenAI Codex CLI. The package name is intentionally scoped
# to reduce the chance of installing a similarly named third-party package.
if ! command -v codex >/dev/null 2>&1; then
  npm install -g @openai/codex
fi

# Prime Gradle dependencies without failing Codespace creation if remote repos are temporarily unavailable.
./gradlew --version
./gradlew help --no-daemon || true

cat <<'EOF'

Riffle Codespace is ready.

Useful commands:
  ./gradlew verify
  gh issue list --state open --limit 50
  codex

Before handing an issue to Codex, authenticate GitHub CLI and Codex if needed:
  gh auth status
  codex
EOF

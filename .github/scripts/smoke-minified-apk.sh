#!/usr/bin/env bash
# Emulator smoke test for the R8-minified release APK. Not a substitute for the owner's on-device
# checklist (docs/release/r8-minification.md): it only proves the minified build installs, starts as
# Home, survives a short seeded monkey run, and logs no crash. Fails on the first crash signal.
set -euo pipefail

apk="${1:?Usage: smoke-minified-apk.sh PATH_TO_APK}"
package="com.riffle.app"
monkey_log="$(mktemp)"

adb install -r "$apk"
adb logcat -c

adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME \
  -n "$package/.MainActivity"
sleep 10
adb shell pidof "$package" >/dev/null || { echo "Process is not running after launch." >&2; exit 1; }

# Seeded so a failure is reproducible. Ignoring timeouts/security exceptions keeps the run about
# crashes in app code, which is what a missing keep rule produces.
adb shell monkey -p "$package" -s 1406 --throttle 250 --ignore-timeouts --ignore-security-exceptions \
  --pct-syskeys 0 -v 400 | tee "$monkey_log"
if grep -q "// CRASH:" "$monkey_log"; then
  echo "Monkey reported a crash." >&2
  adb logcat -d -b crash | tail -n 80 >&2
  exit 1
fi

if adb logcat -d -b crash | grep -E "FATAL EXCEPTION|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError"; then
  adb logcat -d -b crash | tail -n 80 >&2
  echo "Crash buffer is not empty." >&2
  exit 1
fi
adb shell pidof "$package" >/dev/null || { echo "Process died during the monkey run." >&2; exit 1; }
echo "Smoke passed."

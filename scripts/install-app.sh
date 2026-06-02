#!/usr/bin/env bash
# Builds app/debug APK and installs it via the pinned adb wrapper.
# Replaces `./gradlew :app:installDebug` which installs to every connected device.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.hluhovskyi.zero.debug"

echo "▶ assembleDebug..."
./gradlew :app:assembleDebug

if [ ! -f "$APK" ]; then
  echo "Build succeeded but $APK not found." >&2
  exit 1
fi

echo "▶ installing $APK to this worktree's emulator..."
"$HERE/ui/adb" install -r "$APK"

# Print the launch command so callers don't have to guess the activity name.
LAUNCH=$("$HERE/ui/adb" shell cmd package resolve-activity --brief "$PKG" | tr -d '\r' | tail -n1)
if [ -n "$LAUNCH" ]; then
  echo "▶ launch: ./scripts/ui/adb shell am start -n $LAUNCH"
fi

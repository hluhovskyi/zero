#!/usr/bin/env bash
# Builds app/debug APK and installs it via the pinned adb wrapper.
# Replaces `./gradlew :app:installDebug` which installs to every connected device.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "▶ assembleDebug..."
./gradlew :app:assembleDebug

if [ ! -f "$APK" ]; then
  echo "Build succeeded but $APK not found." >&2
  exit 1
fi

echo "▶ installing $APK to this worktree's emulator..."
"$HERE/ui/adb" install -r "$APK"

#!/usr/bin/env bash
# Runs Android instrumented tests against this worktree's pinned emulator.
# Reads .emulator-serial, exports ANDROID_SERIAL, then invokes
# `./gradlew :app:connectedDebugAndroidTest` with any args you pass through.
#
# Usage:
#   ./scripts/run-android-tests.sh
#   ./scripts/run-android-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.hluhovskyi.zero.ZeroE2eTest
set -euo pipefail

if [ ! -f .emulator-serial ]; then
  echo "No emulator claimed for this worktree (no .emulator-serial)." >&2
  echo "Run: ./scripts/emulator/acquire" >&2
  exit 1
fi

SERIAL=$(tr -d '[:space:]' < .emulator-serial)
export ANDROID_SERIAL="$SERIAL"

echo "▶ Running :app:connectedDebugAndroidTest against $SERIAL..."
./gradlew :app:connectedDebugAndroidTest "$@"

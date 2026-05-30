#!/usr/bin/env bash
# Runs Android instrumented tests against this worktree's pinned emulator.
# Reads .emulator-serial, exports ANDROID_SERIAL.
#
# Default (full): builds + installs + runs + uninstalls via Gradle.
#   ./scripts/run-android-tests.sh
#   ./scripts/run-android-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.hluhovskyi.zero.ZeroE2eTest
#
# --fast: skips the Gradle build and runs the ALREADY-INSTALLED test APKs
# directly via `am instrument` (seconds, vs minutes for connectedDebugAndroidTest).
# Use it to iterate on a test without rebuilding when the APKs are current.
# Requires both APKs installed first (the script tells you how if they aren't):
#   ./scripts/run-android-tests.sh --fast
#   ./scripts/run-android-tests.sh --fast com.hluhovskyi.zero.ZeroE2eTest#batchSelectRemovesSelectedTransactions
set -euo pipefail

# Sentinel last-line verdict so stress loops can `grep STATUS:` instead of trusting
# `$?` through a pipe — pipelines surface tail's exit (always 0), not ours.
trap 'rc=$?; if [ "$rc" -eq 0 ]; then echo "STATUS: PASS"; else echo "STATUS: FAIL (exit $rc)"; fi' EXIT

if [ ! -f .emulator-serial ]; then
  echo "No emulator claimed for this worktree (no .emulator-serial)." >&2
  echo "Run: ./scripts/emulator/acquire" >&2
  exit 1
fi

SERIAL=$(tr -d '[:space:]' < .emulator-serial)
export ANDROID_SERIAL="$SERIAL"

FAST=0
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    *) ARGS+=("$arg") ;;
  esac
done

if [ "$FAST" -eq 0 ]; then
  echo "▶ Running :app:connectedDebugAndroidTest against $SERIAL..."
  # No exec — let the EXIT trap fire so STATUS: lands on the last line.
  ./gradlew :app:connectedDebugAndroidTest ${ARGS[@]+"${ARGS[@]}"}
  exit $?
fi

RUNNER="com.hluhovskyi.zero.test/androidx.test.runner.AndroidJUnitRunner"

# The fast path runs what's installed — it does NOT build or install anything.
if ! adb shell pm list instrumentation 2>/dev/null | grep -q "com.hluhovskyi.zero.test"; then
  echo "✗ Test instrumentation not installed on $SERIAL — --fast can't build or install for you." >&2
  echo "  Install both APKs first:" >&2
  echo "    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest" >&2
  echo "    adb install -r -t app/build/outputs/apk/debug/app-debug.apk" >&2
  echo "    adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >&2
  exit 1
fi

class_args=()
if [ "${#ARGS[@]}" -gt 0 ]; then
  class_args=(-e class "${ARGS[0]}")
fi

echo "▶ Fast e2e via am instrument on $SERIAL (no Gradle build)..."
# am instrument exits 0 even when tests fail, so derive the exit code from its output.
out=$(adb shell am instrument -w ${class_args[@]+"${class_args[@]}"} "$RUNNER" 2>&1)
echo "$out"
if echo "$out" | grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|crashed"; then
  exit 1
fi
echo "$out" | grep -q "OK (" || exit 1

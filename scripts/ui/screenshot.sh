#!/bin/bash
# Captures a screenshot from this worktree's emulator to a host path — in ONE
# allowlisted call, so callers never need compound `until/sleep` wait-loops
# (which don't match the adb allowlist entry and trigger a prompt every time).
#
# Usage:
#   ./scripts/ui/screenshot.sh [output-path]
#   ./scripts/ui/screenshot.sh [output-path] --relaunch <pkg/activity>
#
#   output-path    where to write the PNG on the host (default: /tmp/screen.png)
#   --relaunch C   force-stop the package (derived from the part before "/")
#                  and start activity C, then wait for its window before capture
#
# Examples:
#   ./scripts/ui/screenshot.sh
#   ./scripts/ui/screenshot.sh /tmp/feedback.png --relaunch com.hluhovskyi.zero.debug/com.hluhovskyi.zero.activity.MainActivity
set -uo pipefail

ADB="$(dirname "$0")/adb"

OUT="/tmp/screen.png"
RELAUNCH=""
while [ $# -gt 0 ]; do
  case "$1" in
    --relaunch) RELAUNCH="${2:-}"; shift 2 ;;
    *) OUT="$1"; shift ;;
  esac
done

if [ -n "$RELAUNCH" ]; then
  PKG="${RELAUNCH%%/*}"
  "$ADB" shell am force-stop "$PKG" >/dev/null 2>&1
  "$ADB" shell am start -n "$RELAUNCH" >/dev/null 2>&1
  # Wait (up to ~15s) for the package's window to gain focus.
  for _ in $(seq 1 30); do
    if "$ADB" shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus.*$PKG"; then
      break
    fi
    sleep 0.5
  done
  # Small settle for first-frame compose layout.
  sleep 0.5
fi

"$ADB" shell screencap -p /sdcard/_screenshot.png >/dev/null 2>&1
if ! "$ADB" pull /sdcard/_screenshot.png "$OUT" >/dev/null 2>&1; then
  echo "Failed to capture/pull screenshot. Is the emulator live (.emulator-serial)?" >&2
  exit 1
fi
echo "$OUT"

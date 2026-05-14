#!/bin/bash
# Starts an unrun AVD in the background on the next free port.
# Usage: ./scripts/start-emulator.sh [<avd_name>]
#
# Defaults: picks the first AVD that is NOT currently running. Uses -read-only
# so a second concurrent instance of the same AVD is possible if needed.
# Waits for boot, then exits — leaves the emulator running.
#
# Why -read-only by default: multiple worktrees may want concurrent emulators.
# An AVD can only be started a second time if all instances use -read-only.
set -uo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR="$SDK/emulator/emulator"

if [ ! -x "$EMULATOR" ]; then
  echo "Emulator binary not found at $EMULATOR" >&2
  echo "Set ANDROID_HOME to your SDK root." >&2
  exit 1
fi

# Discover all AVDs and which are currently running by booting AVD name.
AVAILABLE=()
while IFS= read -r AVD; do
  [ -n "$AVD" ] && AVAILABLE+=("$AVD")
done < <("$EMULATOR" -list-avds 2>/dev/null)

if [ ${#AVAILABLE[@]} -eq 0 ]; then
  echo "No AVDs configured. Create one in Android Studio first." >&2
  exit 1
fi

# Map serial -> AVD name so we know which AVDs are taken.
RUNNING_AVDS=()
while IFS= read -r SERIAL; do
  [[ "$SERIAL" != emulator-* ]] && continue
  NAME=$(adb -s "$SERIAL" emu avd name 2>/dev/null | head -1 | tr -d '\r')
  [ -n "$NAME" ] && RUNNING_AVDS+=("$NAME")
done < <(adb devices 2>/dev/null | awk 'NR>1 && $1 ~ /^emulator-/ {print $1}')

# Pick AVD: argument > first AVD not in RUNNING_AVDS > fall back to first AVD with -read-only.
PICKED="${1:-}"
if [ -z "$PICKED" ]; then
  for AVD in "${AVAILABLE[@]}"; do
    SKIP=false
    if [ ${#RUNNING_AVDS[@]} -gt 0 ]; then
      for R in "${RUNNING_AVDS[@]}"; do
        [[ "$R" == "$AVD" ]] && SKIP=true && break
      done
    fi
    if ! $SKIP; then
      PICKED="$AVD"
      break
    fi
  done
fi

if [ -z "$PICKED" ]; then
  # All AVDs are already running — fall back to a 2nd instance of the first one.
  PICKED="${AVAILABLE[0]}"
  echo "All AVDs already running; starting a 2nd -read-only instance of $PICKED"
fi

# Find a free even-numbered port starting from 5554.
# Use lsof instead of netstat: macOS netstat output format varies and the
# "\.PORT .*LISTEN" grep pattern does not reliably match, causing the script
# to always pick port 5554 even when it's occupied.
PORT=5554
while lsof -iTCP:"${PORT}" -sTCP:LISTEN -t &>/dev/null 2>&1; do
  PORT=$((PORT + 2))
  if [ "$PORT" -gt 5680 ]; then
    echo "No free emulator ports between 5554 and 5680." >&2
    exit 1
  fi
done

SERIAL="emulator-$PORT"
LOG="/tmp/${SERIAL}.log"

echo "▶ Starting AVD '$PICKED' on $SERIAL (log: $LOG)"
nohup "$EMULATOR" -avd "$PICKED" -port "$PORT" -read-only -no-snapshot-load -no-boot-anim \
  > "$LOG" 2>&1 &
disown

# Wait for boot.
echo "  waiting for boot..."
for _ in $(seq 1 60); do
  if adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then
    echo "  ✓ $SERIAL booted"
    echo "$SERIAL"
    exit 0
  fi
  sleep 3
done

echo "Timed out waiting for $SERIAL to boot. Check $LOG." >&2
exit 1

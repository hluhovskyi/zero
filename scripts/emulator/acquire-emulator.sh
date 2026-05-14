#!/bin/bash
# Assigns an unclaimed running emulator to this worktree.
# Scans all active worktrees for .emulator-serial to build the claimed set,
# then picks the first running emulator not in that set.
# Writes the assigned serial to .emulator-serial in the current directory.
#
# If all running emulators are claimed, auto-invokes start-emulator.sh to add
# capacity, then claims the new instance.
# Pass --no-auto-start to disable auto-start and fail instead.
#
# Concurrent-session safe: uses a mkdir-based lock so two sessions running
# simultaneously can't claim the same emulator. The lock is also held while
# start-emulator.sh runs so two sessions can't race to start on the same port.

AUTO_START=true
for arg in "$@"; do
  [[ "$arg" == "--no-auto-start" ]] && AUTO_START=false
done

# ── Exclusive lock ────────────────────────────────────────────────────────────
# mkdir is atomic on POSIX/APFS — only one process can create the directory.
# No external tools (flock, lockfile) required.
LOCK_DIR="/tmp/zero-emulator-acquire.lock.d"

_release_lock() { rmdir "$LOCK_DIR" 2>/dev/null || true; }
trap _release_lock EXIT INT TERM HUP

# Remove a stale lock left by a crashed process (> 5 minutes old).
find "$LOCK_DIR" -maxdepth 0 -mmin +5 -exec rmdir {} \; 2>/dev/null || true

_retries=0
until mkdir "$LOCK_DIR" 2>/dev/null; do
  _retries=$((_retries + 1))
  [ $_retries -eq 1 ] && echo "Waiting for emulator claim lock (another session is claiming)..." >&2
  if [ $_retries -ge 300 ]; then
    echo "ERROR: emulator lock held >60s — remove $LOCK_DIR if stale." >&2
    exit 1
  fi
  sleep 0.2
done
# ── Lock acquired; everything below runs exclusively ──────────────────────────

claim_unused() {
  # Collect claimed serials from all active worktrees
  local CLAIMED=()
  while IFS= read -r WORKTREE_PATH; do
      local SERIAL_FILE="$WORKTREE_PATH/.emulator-serial"
      if [ -f "$SERIAL_FILE" ]; then
          local SERIAL
          SERIAL=$(cat "$SERIAL_FILE")
          [ -n "$SERIAL" ] && CLAIMED+=("$SERIAL")
      fi
  done < <(git worktree list --porcelain | grep "^worktree " | sed 's/^worktree //')

  # Get all running emulators
  local RUNNING=()
  while IFS= read -r line; do
      local SERIAL
      SERIAL=$(echo "$line" | awk '{print $1}')
      [[ "$SERIAL" == emulator-* ]] && RUNNING+=("$SERIAL")
  done < <(adb devices | tail -n +2)

  if [ ${#RUNNING[@]} -eq 0 ]; then
      echo "__NONE_RUNNING__"
      return 1
  fi

  for SERIAL in "${RUNNING[@]}"; do
      local CLAIMED_FLAG=false
      for C in "${CLAIMED[@]}"; do
          [[ "$C" == "$SERIAL" ]] && CLAIMED_FLAG=true && break
      done
      if ! $CLAIMED_FLAG; then
          echo "$SERIAL" > .emulator-serial
          echo "Assigned $SERIAL to this worktree"
          return 0
      fi
  done

  echo "__ALL_CLAIMED__ ${RUNNING[*]}"
  return 1
}

OUTPUT=$(claim_unused)
RC=$?

if [ $RC -eq 0 ]; then
  echo "$OUTPUT"
  exit 0
fi

# Auto-start if allowed.
# start-emulator.sh runs while we hold the lock so two sessions can't race to
# start on the same port.
if $AUTO_START; then
  echo "No free running emulator — starting a new one (use --no-auto-start to disable)..."
  if "$(dirname "$0")/start-emulator.sh" > /tmp/start-emulator.out 2>&1; then
    SERIAL=$(tail -1 /tmp/start-emulator.out)
    if [[ "$SERIAL" == emulator-* ]]; then
      echo "$SERIAL" > .emulator-serial
      echo "Started and assigned $SERIAL to this worktree"
      exit 0
    fi
  fi
  echo "start-emulator.sh failed. Output:"
  cat /tmp/start-emulator.out
  exit 1
fi

# Auto-start disabled — print the diagnostic and suggested fix.
case "$OUTPUT" in
  __NONE_RUNNING__*)
    echo "No running emulators. Run: ./scripts/start-emulator.sh"
    ;;
  __ALL_CLAIMED__*)
    echo "All running emulators are claimed by other worktrees."
    echo "Run: ./scripts/start-emulator.sh"
    ;;
esac
exit 1

#!/bin/bash
# Assigns an unclaimed running emulator to this worktree.
# Scans all active worktrees for .emulator-serial to build the claimed set,
# then picks the first running emulator not in that set.
# Writes the assigned serial to .emulator-serial in the current directory.
#
# If all running emulators are claimed, auto-invokes start-emulator.sh to add
# capacity, then claims the new instance.
# Pass --no-auto-start to disable auto-start and fail instead.

AUTO_START=true
for arg in "$@"; do
  [[ "$arg" == "--no-auto-start" ]] && AUTO_START=false
done

claim_unused() {
  # Collect claimed serials from all active worktrees
  local CLAIMED=()
  while IFS= read -r WORKTREE_PATH; do
      local SERIAL_FILE="$WORKTREE_PATH/.emulator-serial"
      if [ -f "$SERIAL_FILE" ]; then
          local SERIAL=$(cat "$SERIAL_FILE")
          [ -n "$SERIAL" ] && CLAIMED+=("$SERIAL")
      fi
  done < <(git worktree list --porcelain | grep "^worktree " | sed 's/^worktree //')

  # Get all running emulators
  local RUNNING=()
  while IFS= read -r line; do
      local SERIAL=$(echo "$line" | awk '{print $1}')
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

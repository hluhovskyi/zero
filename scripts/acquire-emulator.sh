#!/bin/bash
# Assigns an unclaimed running emulator to this worktree.
# Scans all active worktrees for .emulator-serial to build the claimed set,
# then picks the first running emulator not in that set.
# Writes the assigned serial to .emulator-serial in the current directory.

# Collect claimed serials from all active worktrees
CLAIMED=()
while IFS= read -r WORKTREE_PATH; do
    SERIAL_FILE="$WORKTREE_PATH/.emulator-serial"
    if [ -f "$SERIAL_FILE" ]; then
        SERIAL=$(cat "$SERIAL_FILE")
        [ -n "$SERIAL" ] && CLAIMED+=("$SERIAL")
    fi
done < <(git worktree list --porcelain | grep "^worktree " | sed 's/^worktree //')

# Get all running emulators
RUNNING=()
while IFS= read -r line; do
    SERIAL=$(echo "$line" | awk '{print $1}')
    [[ "$SERIAL" == emulator-* ]] && RUNNING+=("$SERIAL")
done < <(adb devices | tail -n +2)

if [ ${#RUNNING[@]} -eq 0 ]; then
    echo "No running emulators found. Start an AVD first."
    exit 1
fi

# Assign the first unclaimed emulator
for SERIAL in "${RUNNING[@]}"; do
    CLAIMED_FLAG=false
    for C in "${CLAIMED[@]}"; do
        [[ "$C" == "$SERIAL" ]] && CLAIMED_FLAG=true && break
    done
    if ! $CLAIMED_FLAG; then
        echo "$SERIAL" > .emulator-serial
        echo "Assigned $SERIAL to this worktree"
        exit 0
    fi
done

echo "All running emulators are claimed by other worktrees."
echo "Running:  ${RUNNING[*]}"
echo "Claimed:  ${CLAIMED[*]}"
echo "Start another AVD to unblock this session."
exit 1

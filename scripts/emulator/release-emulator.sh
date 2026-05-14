#!/bin/bash
# Releases this worktree's emulator claim.
#
# Call this when you're done with UI testing in a session so the emulator
# becomes available to other worktrees without them having to start a new
# instance.  Not calling this is safe — the emulator just stays "claimed"
# until acquire-emulator.sh's stale-claim pruner finds it or you delete
# .emulator-serial manually.

SERIAL_FILE=".emulator-serial"

if [ ! -f "$SERIAL_FILE" ]; then
    echo "No emulator claimed by this worktree (no $SERIAL_FILE)."
    exit 0
fi

SERIAL=$(tr -d '[:space:]' < "$SERIAL_FILE")
rm -f "$SERIAL_FILE"
echo "Released $SERIAL (removed $SERIAL_FILE)"

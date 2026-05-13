#!/bin/bash
# Wrapper around adb that auto-pins to this worktree's emulator.
# Reads .emulator-serial in the current working directory and sets
# ANDROID_SERIAL before exec'ing adb. Pass any adb args through.
#
# Usage: ./scripts/adb.sh shell input tap 100 200
#        ./scripts/adb.sh devices
#        ./scripts/adb.sh install -r app.apk
#
# If .emulator-serial is missing, adb runs without ANDROID_SERIAL (works fine
# for global commands like `./scripts/adb.sh devices`).

if [ -f ".emulator-serial" ]; then
  ANDROID_SERIAL=$(tr -d '[:space:]' < .emulator-serial)
  if [ -n "$ANDROID_SERIAL" ]; then
    export ANDROID_SERIAL
  fi
fi
exec adb "$@"

#!/bin/bash
# Blocks until the on-screen UI is idle, so a following screencap captures a
# settled frame (post-navigation, post-AnimatedVisibility) instead of a
# mid-transition / mid-animation one. Run it right before a screenshot.
#
# How: a single `uiautomator dump` — which internally waits for the accessibility
# event stream to quiesce before writing — IS the settle. Its own idle timeout
# bounds the wait, so a never-quiet screen (e.g. a live-wallpaper launcher) just
# returns after that and the caller proceeds. Best-effort, never fails the caller.
#
# One allowlisted call, so callers never hand-roll an until/sleep wait loop.
#
# Usage: ./scripts/ui/wait-stable.sh
set -uo pipefail

ADB="$(dirname "$0")/adb"

"$ADB" shell uiautomator dump /data/local/tmp/_stable.xml >/dev/null 2>&1 || true
exit 0

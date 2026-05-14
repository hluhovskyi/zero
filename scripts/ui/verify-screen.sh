#!/bin/bash
# Verifies the current screen contains an expected text or content-desc landmark.
# Usage: ./scripts/ui/verify-screen.sh <expected-text>
#
# Refreshes the UI dump and exits 0 if the landmark is found, 1 if not.
# Use after any navigation action to confirm the expected screen is active.

ADB="$(dirname "$0")/adb.sh"

LANDMARK="${1:-}"
if [ -z "$LANDMARK" ]; then
    echo "Usage: $0 <expected-text>"
    exit 1
fi

"$ADB" shell uiautomator dump /data/local/tmp/window_dump.xml >/dev/null 2>&1

FOUND=$("$ADB" shell cat /data/local/tmp/window_dump.xml | python3 -c "
import sys, xml.etree.ElementTree as ET
landmark = sys.argv[1]
root = ET.fromstring(sys.stdin.read())
for node in root.iter('node'):
    t = node.get('text', '').strip()
    d = node.get('content-desc', '').strip()
    if landmark in t or landmark in d:
        print('found')
        break
" "$LANDMARK" 2>/dev/null)

if [ "$FOUND" = "found" ]; then
    echo "✓ Screen landmark found: '$LANDMARK'"
    exit 0
else
    echo "✗ '$LANDMARK' not found — wrong screen or element missing"
    exit 1
fi

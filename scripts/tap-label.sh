#!/bin/bash
# Finds a UI element by text or content-desc and taps its center.
# Usage: ./scripts/tap-label.sh <label> [--screenshot]
#
# Arguments:
#   <label>        Exact text or content-desc of the element to tap
#   --screenshot   After tapping, capture screenshot to /tmp/screen.png
#
# Always dumps a fresh UI hierarchy before searching.
# Exits 1 if the label is not found.

LABEL="${1:-}"
SCREENSHOT=false
for arg in "$@"; do
    [[ "$arg" == "--screenshot" ]] && SCREENSHOT=true
done

if [ -z "$LABEL" ]; then
    echo "Usage: $0 <label> [--screenshot]"
    exit 1
fi

adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1

BOUNDS=$(adb shell cat /sdcard/window_dump.xml | python3 -c "
import sys, xml.etree.ElementTree as ET
label = sys.argv[1]
root = ET.fromstring(sys.stdin.read())
for node in root.iter('node'):
    t = node.get('text', '').strip()
    d = node.get('content-desc', '').strip()
    if t == label or d == label:
        print(node.get('bounds', ''))
        break
" "$LABEL" 2>/dev/null)

if [ -z "$BOUNDS" ]; then
    echo "✗ Not found: '$LABEL'"
    exit 1
fi

read x1 y1 x2 y2 <<<"$(echo "$BOUNDS" | sed 's/\[/ /g; s/\]/ /g; s/,/ /g')"
CX=$(((x1 + x2) / 2))
CY=$(((y1 + y2) / 2))
echo "✓ Tapping '$LABEL' at ($CX,$CY)  bounds=$BOUNDS"
adb shell input tap "$CX" "$CY"

if $SCREENSHOT; then
    adb shell screencap -p /sdcard/screen.png
    adb pull /sdcard/screen.png /tmp/screen.png >/dev/null 2>&1
    echo "📸 /tmp/screen.png"
fi

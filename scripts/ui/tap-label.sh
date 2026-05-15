#!/bin/bash
# Finds a UI element by text or content-desc and taps its center.
# Usage: ./scripts/ui/tap-label.sh <label> [--screenshot] [--verify <landmark>]
#
# Arguments:
#   <label>              Exact text or content-desc of the element to tap
#   --screenshot         After tapping, capture screenshot to /tmp/screen.png
#   --verify <landmark>  After tapping, assert a text/content-desc landmark is visible.
#                        Use to confirm navigation landed on the expected screen.
#                        Exits 1 if the landmark is not found.
#
# Always dumps a fresh UI hierarchy before searching.
# Exits 1 if the label is not found.

ADB="$(dirname "$0")/adb"

LABEL="${1:-}"
SCREENSHOT=false
VERIFY=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
    [[ "${args[$i]}" == "--screenshot" ]] && SCREENSHOT=true
    [[ "${args[$i]}" == "--verify" ]] && VERIFY="${args[$((i + 1))]}"
done

if [ -z "$LABEL" ]; then
    echo "Usage: $0 <label> [--screenshot] [--verify <landmark>]"
    exit 1
fi

"$ADB" shell uiautomator dump /data/local/tmp/window_dump.xml >/dev/null 2>&1

BOUNDS=$("$ADB" shell cat /data/local/tmp/window_dump.xml | python3 -c "
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
"$ADB" shell input tap "$CX" "$CY"

if $SCREENSHOT; then
    "$ADB" shell screencap -p /data/local/tmp/screen.png
    "$ADB" pull /data/local/tmp/screen.png /tmp/screen.png >/dev/null 2>&1
    echo "📸 /tmp/screen.png"
fi

if [ -n "$VERIFY" ]; then
    sleep 0.5
    "$(dirname "$0")/verify-screen.sh" "$VERIFY"
fi

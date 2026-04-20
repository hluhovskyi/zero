#!/bin/bash
# Opens the app and navigates to a named screen.
# Usage: ./scripts/open-screen.sh <screen>
#
# Supported screens:
#   import      — Settings → Import Data (bottom sheet)
#   settings    — Settings tab
#   accounts    — Accounts tab
#   categories  — Categories tab
#
# After navigating, dumps the UI and takes a screenshot to /tmp/screen.png.

SCREEN="${1:-}"
PACKAGE="com.hluhovskyi.zero"
ACTIVITY=".activity.MainActivity"
DUMP_SCRIPT="$(dirname "$0")/dump-ui.sh"

if [ -z "$SCREEN" ]; then
  echo "Usage: $0 <screen>"
  echo "Supported: import, settings, accounts, categories"
  exit 1
fi

# ── helpers ────────────────────────────────────────────────────────────────────

# Tap the center of the first node whose text or content-desc matches $1
tap_label() {
  local label="$1"
  local xml
  xml=$(adb shell cat /sdcard/window_dump.xml 2>/dev/null)

  local bounds
  bounds=$(echo "$xml" | python3 -c "
import sys, xml.etree.ElementTree as ET, re
label = sys.argv[1]
root = ET.fromstring(sys.stdin.read())
for node in root.iter('node'):
    t = node.get('text','').strip()
    d = node.get('content-desc','').strip()
    if t == label or d == label:
        print(node.get('bounds',''))
        break
" "$label" 2>/dev/null)

  if [ -z "$bounds" ]; then
    echo "  ✗ Could not find: '$label'"
    return 1
  fi

  local x1 y1 x2 y2
  read x1 y1 x2 y2 <<< $(echo "$bounds" | sed 's/\[/ /g; s/\]/ /g; s/,/ /g')
  local cx=$(( (x1 + x2) / 2 ))
  local cy=$(( (y1 + y2) / 2 ))
  echo "  ✓ Tapping '$label' at ($cx,$cy)"
  adb shell input tap "$cx" "$cy"
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window_dump.xml > /dev/null 2>&1
}

screenshot() {
  adb exec-out screencap -p > /tmp/screen.png 2>/dev/null
  echo "  📸 Screenshot saved to /tmp/screen.png"
}

# ── launch ─────────────────────────────────────────────────────────────────────

echo "▶ Launching $PACKAGE..."
adb shell am start -n "$PACKAGE/$ACTIVITY" > /dev/null 2>&1
sleep 2
dump_ui

# ── navigate ───────────────────────────────────────────────────────────────────

case "$SCREEN" in
  import)
    echo "▶ Navigating to Settings..."
    tap_label "Settings"
    sleep 1
    dump_ui
    echo "▶ Opening Import Data..."
    tap_label "Import Data"
    sleep 1
    dump_ui
    ;;
  settings)
    echo "▶ Navigating to Settings..."
    tap_label "Settings"
    sleep 1
    dump_ui
    ;;
  accounts)
    echo "▶ Navigating to Accounts..."
    tap_label "Accounts"
    sleep 1
    dump_ui
    ;;
  categories)
    echo "▶ Navigating to Categories..."
    tap_label "Categories"
    sleep 1
    dump_ui
    ;;
  *)
    echo "Unknown screen: '$SCREEN'"
    echo "Supported: import, settings, accounts, categories"
    exit 1
    ;;
esac

# ── output ─────────────────────────────────────────────────────────────────────

echo ""
echo "── UI Hierarchy ──────────────────────────────────────────────"
"$DUMP_SCRIPT"
echo ""
screenshot

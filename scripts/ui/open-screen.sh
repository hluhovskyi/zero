#!/bin/bash
# Opens the app and navigates to a named screen via a pre-baked tap chain.
# Usage: ./scripts/ui/open-screen.sh <screen>
#
# Supported screens:
#   import                  — Settings → Import Data (bottom sheet)
#   settings                — Settings tab
#   accounts                — Accounts tab
#   categories              — Categories tab
#   icon-picker-expense     — Categories → Add category → icon (expense default)
#   icon-picker-income      — Categories → Add category → Income tab → icon
#   icon-picker-account     — Accounts → Add Account → icon
#
# After navigating, dumps the UI and takes a screenshot to /tmp/screen.png.
# Note: the app has no external deep-link handler, so these are click chains —
# brittle if labels change. If you need a new shortcut often, add a case here.

SCREEN="${1:-}"
PACKAGE="com.hluhovskyi.zero"
ACTIVITY=".activity.MainActivity"

DIR="$(dirname "$0")"
ADB="$DIR/adb"
TAP="$DIR/tap-label.sh"
DUMP="$DIR/dump-ui.sh"

if [ -z "$SCREEN" ]; then
  echo "Usage: $0 <screen>"
  echo "Supported: import, settings, accounts, categories, icon-picker-expense, icon-picker-income, icon-picker-account"
  exit 1
fi

# Tap the leftmost clickable icon on the same row as a labelled field — used to
# reach the icon picker, which exposes only a question-mark icon adjacent to the
# name field. The tap-label.sh helper can't do this since the icon has no
# accessible text/content-desc.
tap_icon_next_to() {
  local label="$1"
  "$ADB" shell uiautomator dump /data/local/tmp/window_dump.xml > /dev/null 2>&1
  local xml
  xml=$("$ADB" shell cat /data/local/tmp/window_dump.xml 2>/dev/null)
  echo "$xml" | python3 -c "
import sys, xml.etree.ElementTree as ET
label = sys.argv[1]
root = ET.fromstring(sys.stdin.read())
fields = []
for node in root.iter('node'):
    if node.get('text','').strip() == label:
        b = node.get('bounds','')
        if b: fields.append(b)
if not fields: sys.exit(1)
def parse(b):
    b = b.strip('[]').replace('][', ',').split(',')
    return [int(x) for x in b]
fx1, fy1, fx2, fy2 = parse(fields[0])
fy_center = (fy1 + fy2) // 2
best = None
best_x = 1e9
for node in root.iter('node'):
    if node.get('clickable') != 'true': continue
    b = node.get('bounds','')
    if not b: continue
    x1, y1, x2, y2 = parse(b)
    cy = (y1 + y2) // 2
    if x2 < fx1 and abs(cy - fy_center) < 80 and x1 < best_x:
        best = (x1, y1, x2, y2)
        best_x = x1
if not best: sys.exit(1)
x1, y1, x2, y2 = best
print((x1 + x2) // 2, (y1 + y2) // 2)
" "$label" 2>/dev/null | while read cx cy; do
    if [ -n "$cx" ]; then
      echo "  ✓ Tapping icon next to '$label' at ($cx,$cy)"
      "$ADB" shell input tap "$cx" "$cy"
    fi
  done
}

# ── launch ─────────────────────────────────────────────────────────────────────

echo "▶ Launching $PACKAGE..."
"$ADB" shell am start -n "$PACKAGE/$ACTIVITY" > /dev/null 2>&1
sleep 2

# ── navigate ───────────────────────────────────────────────────────────────────

case "$SCREEN" in
  import)
    "$TAP" "Settings"; sleep 1
    "$TAP" "Import Data"; sleep 1
    ;;
  settings)
    "$TAP" "Settings"; sleep 1
    ;;
  accounts)
    "$TAP" "Accounts"; sleep 1
    ;;
  categories)
    "$TAP" "Categories"; sleep 1
    ;;
  icon-picker-expense)
    "$TAP" "Categories"; sleep 1
    "$TAP" "Add category"; sleep 1
    tap_icon_next_to "e.g. Groceries"; sleep 1
    ;;
  icon-picker-income)
    "$TAP" "Categories"; sleep 1
    "$TAP" "Add category"; sleep 1
    "$TAP" "Income"; sleep 1
    tap_icon_next_to "e.g. Groceries"; sleep 1
    ;;
  icon-picker-account)
    "$TAP" "Accounts"; sleep 1
    "$TAP" "Add Account"; sleep 1
    tap_icon_next_to "e.g. Savings"; sleep 1
    ;;
  *)
    echo "Unknown screen: '$SCREEN'"
    echo "Supported: import, settings, accounts, categories, icon-picker-expense, icon-picker-income, icon-picker-account"
    exit 1
    ;;
esac

# ── output ─────────────────────────────────────────────────────────────────────

echo ""
echo "── UI Hierarchy ──────────────────────────────────────────────"
"$DUMP"
echo ""
"$ADB" shell screencap -p /data/local/tmp/screen.png 2>/dev/null
"$ADB" pull /data/local/tmp/screen.png /tmp/screen.png > /dev/null 2>&1
echo "  📸 Screenshot saved to /tmp/screen.png"

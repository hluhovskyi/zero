#!/usr/bin/env bash
# Drives the running debug app into a budget state for UI verification, then leaves
# it on the Budget screen. Both states seed a 100 Food & Drink expense and differ
# only in the budget set against it:
#   over   (default) — budget 50  -> 50 over  -> the over-budget tab dot shows
#   normal           — budget 200 -> 100 left -> within budget, no dot
#
# Why: verifying a state-dependent widget used to mean hand-driving a 12-tap
# onboard -> add-expense -> set-budget flow every time. This encodes it once.
#
# Prereqs: an emulator claimed (.emulator-serial) and the debug app installed
# (./scripts/install-app.sh).
# Usage: ./scripts/ui/seed-budget.sh [over|normal]
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

MODE="${1:-over}"
case "$MODE" in
  over)   BUDGET="50";  DONE_PAT="over budget" ;;   # summary: "1 category over budget"
  normal) BUDGET="200"; DONE_PAT="left" ;;          # category card: "100.00 left"
  *) echo "Usage: $0 [over|normal]" >&2; exit 1 ;;
esac

PKG="com.hluhovskyi.zero.debug"
ADB="./scripts/ui/adb"
TAP_LABEL="./scripts/ui/tap-label.sh"

# The in-app numpads are Compose-drawn with NO uiautomator semantics — their digit
# keys expose neither text nor content-desc, so they can only be hit by coordinate,
# and the add-transaction and budget-edit numpads sit at different positions. Taps
# are screen-relative percentages (tuned on 1080x2400); re-tune if the layout shifts.
read -r W H < <("$ADB" shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
tap_pct() { "$ADB" shell input tap "$(( W * $1 / 100 ))" "$(( H * $2 / 100 ))"; sleep 0.3; }

# Digit keypads: $1 = digit. Columns/rows differ between the two numpads.
tap_txn_digit() {  # add-transaction numpad (bottom third)
  case "$1" in 1)tap_pct 13 72;;2)tap_pct 38 72;;3)tap_pct 62 72;;4)tap_pct 13 78;;5)tap_pct 38 78;;
               6)tap_pct 62 78;;7)tap_pct 13 85;;8)tap_pct 38 85;;9)tap_pct 62 85;;0)tap_pct 38 91;;esac
}
tap_budget_digit() {  # budget-edit sheet numpad (lower half)
  case "$1" in 1)tap_pct 17 60;;2)tap_pct 50 60;;3)tap_pct 83 60;;4)tap_pct 17 67;;5)tap_pct 50 67;;
               6)tap_pct 83 67;;7)tap_pct 17 73;;8)tap_pct 50 73;;9)tap_pct 83 73;;0)tap_pct 50 79;;esac
}

wait_for() { # wait_for "<grep-pattern>" — block until a node matching it appears in the dump
  local label="$1" i=0
  until "$ADB" shell uiautomator dump /sdcard/_seed.xml >/dev/null 2>&1 &&
        "$ADB" shell cat /sdcard/_seed.xml 2>/dev/null | grep -q "$label"; do
    i=$((i + 1)); [ "$i" -gt 40 ] && { echo "✗ timed out waiting for '$label'" >&2; exit 1; }
    sleep 0.5
  done
}

echo "▶ Resetting app data + launching ${PKG}…"
if ! "$ADB" shell pm list packages | grep -q "$PKG"; then
  echo "✗ $PKG not installed. Run ./scripts/install-app.sh first." >&2
  exit 1
fi
"$ADB" shell pm clear "$PKG" >/dev/null   # deterministic clean slate
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_for "Add transaction"

echo "▶ Adding a 100 Food & Drink expense…"
"$TAP_LABEL" "Add transaction" >/dev/null
wait_for "AMOUNT"
tap_pct 80 29                          # focus amount -> reveal numpad (no dump signal for it)
sleep 0.5
tap_txn_digit 1; tap_txn_digit 0; tap_txn_digit 0
"$TAP_LABEL" "Food & Drink" >/dev/null
"$TAP_LABEL" "Save Transaction" >/dev/null
wait_for "Add transaction"             # back on the transactions screen

echo "▶ Setting Food & Drink budget to ${BUDGET} (spend is 100)…"
"$TAP_LABEL" "Budget" >/dev/null
wait_for "Food"                        # XML escapes & as &amp;, so match on "Food"
"$TAP_LABEL" "Food & Drink" >/dev/null
wait_for "Delete"                      # numpad's backspace key exposes content-desc="Delete"
i=0; while [ "$i" -lt "${#BUDGET}" ]; do tap_budget_digit "${BUDGET:$i:1}"; i=$((i + 1)); done
tap_pct 50 84                          # "Set … — Next" commit (em-dash label, no clean match)
sleep 0.5
"$ADB" shell input keyevent 4          # dismiss the auto-advance (next-category) numpad sheet
wait_for "$DONE_PAT"                    # over: "1 category over budget"; normal: "100.00 left"

echo "✓ Seeded '${MODE}' budget state — Food & Drink 100 spent / ${BUDGET} budget."

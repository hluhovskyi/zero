#!/bin/bash
# Dumps the current Android UI hierarchy.
# Default: readable summary (text + bounds per node).
# Pass --raw to get the full XML instead.
set -uo pipefail

ADB="$(dirname "$0")/adb.sh"

RAW=false
for arg in "$@"; do
  [[ "$arg" == "--raw" ]] && RAW=true
done

if ! DUMP_OUT=$("$ADB" shell uiautomator dump /data/local/tmp/window_dump.xml 2>&1); then
  echo "Failed to dump UI hierarchy. adb said:" >&2
  echo "$DUMP_OUT" >&2
  echo "Is the emulator/device connected? Is .emulator-serial pointing to a live serial?" >&2
  exit 1
fi

XML=$("$ADB" shell cat /data/local/tmp/window_dump.xml 2>&1)
if [ -z "$XML" ] || [ "${XML:0:1}" != "<" ]; then
  echo "Dump file empty or unreadable. adb cat returned:" >&2
  printf '%s\n' "$XML" >&2
  echo "Common causes: another adb session running uiautomator, or device just unlocked but window not yet ready." >&2
  exit 1
fi

if $RAW; then
  echo "$XML"
else
  echo "$XML" | python3 -c '
import sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    text        = node.get("text", "").strip()
    desc        = node.get("content-desc", "").strip()
    bounds      = node.get("bounds", "")
    resource_id = node.get("resource-id", "").split("/")[-1]
    label       = text or desc or resource_id
    if label:
        print(f"{bounds:30s}  {label}")
'
fi

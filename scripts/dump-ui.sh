#!/bin/bash
# Dumps the current Android UI hierarchy.
# Default: readable summary (text + bounds per node).
# Pass --raw to get the full XML instead.

RAW=false
for arg in "$@"; do
  [[ "$arg" == "--raw" ]] && RAW=true
done

adb shell uiautomator dump /sdcard/window_dump.xml > /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "Failed to dump UI hierarchy. Is an emulator/device connected?"
  exit 1
fi

XML=$(adb shell cat /sdcard/window_dump.xml)

if $RAW; then
  echo "$XML"
else
  echo "$XML" | python3 - << 'EOF'
import sys, xml.etree.ElementTree as ET

raw = sys.stdin.read()
root = ET.fromstring(raw)

for node in root.iter('node'):
    text        = node.get('text', '').strip()
    desc        = node.get('content-desc', '').strip()
    bounds      = node.get('bounds', '')
    resource_id = node.get('resource-id', '').split('/')[-1]  # last segment only
    label       = text or desc or resource_id
    if label:
        print(f"{bounds:30s}  {label}")
EOF
fi

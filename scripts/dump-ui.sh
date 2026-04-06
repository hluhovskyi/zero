#!/bin/bash
# Dumps the current Android UI hierarchy to stdout
adb shell uiautomator dump /sdcard/window_dump.xml > /dev/null 2>&1
if [ $? -eq 0 ]; then
  adb shell cat /sdcard/window_dump.xml
else
  echo "Failed to dump UI hierarchy. Is an emulator running?"
  exit 1
fi

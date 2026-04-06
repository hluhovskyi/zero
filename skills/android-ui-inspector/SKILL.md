---
name: android-ui-inspector
description: Use when debugging Android UI layouts, view bounds, or when you need to know exact coordinates to tap on the screen.
---

# Android UI Inspector

When you need to interact with the emulator, verify layout boundaries, or confirm that a UI component is rendering correctly, do not guess or work blind. This skill is generic and applicable whether you are operating as Claude Code, Gemini CLI, or Copilot CLI.

## Workflow

1. **Prerequisites:** Ensure an emulator or device is connected (using your shell/bash tool to run `adb devices`) and the app is installed/running.
2. **Dump the Screen:** Run the UI dump script via your shell/bash tool to get the current XML view hierarchy:
   ```bash
   ./scripts/dump-ui.sh
   ```
3. **Analyze the Layout:** Parse the XML output to find the nodes you care about. Pay close attention to the `bounds="[x1,y1][x2,y2]"` attribute to verify spacing, alignment, and sizing issues (like a bottom sheet filling the whole screen `[0,0][1080,2040]` vs a partial overlay).
4. **Interact via ADB (If needed):** If you need to navigate to another screen to test something, use the bounds from step 3 to send tap events via your shell/bash tool:
   ```bash
   adb shell input tap <x> <y>
   ```
   *Note: Pick an (x,y) coordinate safely inside the bounds box.*
5. **Verify Fixes:** After applying UI or navigation fixes, rebuild the app, navigate back to the screen via ADB, and re-run `./scripts/dump-ui.sh` to conclusively verify your changes worked without relying on guesswork.

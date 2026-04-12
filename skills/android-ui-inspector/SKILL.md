---
name: android-ui-inspector
description: Use when debugging Android UI layouts, view bounds, or when you need to know exact coordinates to tap on the screen.
---

# Android UI Inspector

When you need to interact with the emulator, verify layout boundaries, or confirm that a UI component is rendering correctly, do not guess or work blind. This skill is generic and applicable whether you are operating as Claude Code, Gemini CLI, or Copilot CLI.

## When to Use

Use this skill **before committing any UI change** — not only when something looks broken. Compilation passing does not mean layout is correct. Invoke proactively after implementing or modifying any Composable.

## Workflow

1. **Prerequisites:** Ensure an emulator or device is connected (using your shell/bash tool to run `adb devices`) and the app is installed/running.
2. **Dump the Screen:** Run the UI dump script via your shell/bash tool to get the current XML view hierarchy:
   ```bash
   ./scripts/dump-ui.sh
   ```
3. **Take a Screenshot:** Capture a visual snapshot alongside the XML — bounds tell you structure, the screenshot catches rendering artifacts (color, overflow, clipping):
   ```bash
   adb exec-out screencap -p > /tmp/screen.png
   ```
   Then read `/tmp/screen.png` with your image tool to visually inspect the result.
4. **Analyze the Layout:** Parse the XML output to find the nodes you care about. Check:
   - `bounds="[x1,y1][x2,y2]"` — verify expected size and position; a zero-width/height bound means the view is invisible
   - No node is clipped by a parent with smaller bounds
   - Expected nodes are present (missing node = component not rendered at all)
   - Bottom sheets: bounds should be a partial overlay, not `[0,0][1080,2040]` (full screen = not a sheet)
5. **Interact via ADB (If needed):** If you need to navigate to another screen to test something, use the bounds from step 4 to send tap events via your shell/bash tool:
   ```bash
   adb shell input tap <x> <y>
   ```
   *Note: Pick an (x,y) coordinate safely inside the bounds box.*

   **Dismiss the soft keyboard before screenshotting** if the screen under test contains a text field — the keyboard covers the bottom half of the screen and hides bottom sheets entirely:
   ```bash
   adb shell input keyevent 111   # KEYCODE_ESCAPE — dismisses keyboard without closing the screen
   ```
6. **Verify Fixes:** After applying UI or navigation fixes, rebuild the app, navigate back to the screen via ADB, and re-run `./scripts/dump-ui.sh` to conclusively verify your changes worked without relying on guesswork. **Anti-Hallucination Rule:** When verifying a UI state change (like focus, visibility, or bounds), you MUST copy-paste the exact XML node from the `dump-ui.sh` output into your thought process or response before claiming success. Never summarize a verification result without quoting the raw evidence.

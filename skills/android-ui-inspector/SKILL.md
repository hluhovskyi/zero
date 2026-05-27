---
name: android-ui-inspector
description: Use when debugging Android UI layouts, view bounds, or when you need to know exact coordinates to tap on the screen.
---

# Android UI Inspector

When you need to interact with the emulator, verify layout boundaries, or confirm that a UI component is rendering correctly, do not guess or work blind. This skill is generic and applicable whether you are operating as Claude Code, Gemini CLI, or Copilot CLI.

## When to Use

Use this skill **before committing any UI change** — not only when something looks broken. Compilation passing does not mean layout is correct. Invoke proactively after implementing or modifying any Composable.

## Workflow

1. **Prerequisites:** Ensure `.emulator-serial` exists in the worktree root — it pins this session to a specific emulator so parallel sessions don't interfere. If it's missing, run:
   ```bash
   ./scripts/emulator/acquire
   ```
   This claims an unclaimed running emulator, and auto-invokes `./scripts/emulator/start` to launch a new one (with `-read-only` on a free port) if all running emulators are claimed. Pass `--no-auto-start` to suppress that fallback. Once acquired, confirm the app is installed and running via `./scripts/install-app.sh` (not `./gradlew installDebug`, which would clobber sibling worktrees — the PreToolUse hook will deny it).

2. **Dump the Screen:** Run the UI dump script to get the current hierarchy:
   ```bash
   ./scripts/ui/dump-ui.sh
   ```
   Parse the output to check:
   - `bounds="[x1,y1][x2,y2]"` — zero-width/height = invisible
   - Expected nodes are present (missing = component not rendered)
   - No node is clipped by a parent with smaller bounds
   - Bottom sheets: bounds should be partial overlay, not full screen `[0,0][1080,2040]`
   - **Focus state** — a screenshot cannot capture a blinking cursor reliably. For focus fixes, grep the raw dump: `./scripts/ui/dump-ui.sh --raw | grep 'focused="true"'`

3. **Verify Navigation After Any Interaction:** This app uses single-Activity Compose navigation. After tapping anything that should keep you on the current screen (or navigate to a specific screen), immediately verify by grepping for a text landmark unique to the expected screen:
   ```bash
   ./scripts/ui/verify-screen.sh "Account name"   # confirms account edit screen is active
   ```
   Or combine with the tap in one call:
   ```bash
   ./scripts/ui/tap-label.sh "Bank" --verify "Account name"
   ```
   **Do not skip this step** after interactions that affect navigation — a screenshot alone cannot tell you which screen you're on, and `adb dumpsys activity` won't help with Compose navigation.

4. **Interact via ADB (If needed):** To tap a UI element by its visible label or content-desc:
   ```bash
   ./scripts/ui/tap-label.sh "Food"                              # tap and continue
   ./scripts/ui/tap-label.sh "Add transaction" --verify "Name"  # tap then assert screen
   ./scripts/ui/tap-label.sh "Save" --screenshot                 # tap then capture screenshot
   ```
   For elements with no text/content-desc, fall back to manual coordinates from the dump:
   ```bash
   adb shell input tap <x> <y>   # (x,y) must be safely inside the bounds box
   ```

   **Dismiss the soft keyboard before dumping** if the screen contains a text field — the keyboard covers the bottom half and hides bottom sheets entirely:
   ```bash
   adb shell input keyevent 111   # KEYCODE_ESCAPE — dismisses keyboard without closing the screen
   ```

   **Bottom-sheet scroll hazard:** swiping past the last item of a bottom sheet dismisses it. Dump after each swipe to confirm the sheet is still mounted, or use a smaller swipe delta. For long sheets like the icon picker, use `./scripts/ui/open-screen.sh icon-picker-expense` (or `-income` / `-account`) to land directly on the sheet without rebuilding the tap chain each iteration.

   **Seed state with a script, don't hand-drive multi-step setup.** To verify a state-dependent widget, reach its precondition via a seed script rather than re-typing the flow each iteration — e.g. `./scripts/ui/seed-budget-over.sh` lands on the Budget screen in the over-budget state (drives the tab dot). Extend that pattern for new stateful UI instead of repeating onboard → create → configure taps by hand.

5. **Screenshot (Only When Needed):** The XML dump covers structure, bounds, and navigation. Take a screenshot only when you suspect a **visual rendering artifact** — wrong color, clipping, overflow, or a composable that the dump shows as present but looks broken:
   ```bash
   adb shell screencap -p /sdcard/screen.png
   adb pull /sdcard/screen.png /tmp/screen.png
   ```
   Then read `/tmp/screen.png` to visually inspect.

   > **Do not use `adb exec-out screencap -p > /tmp/screen.png`** — it triggers permission prompts on this device.

6. **Verify Fixes:** After applying UI or navigation fixes, rebuild, navigate back to the screen, and re-run the dump and `verify-screen.sh` to confirm. **Anti-Hallucination Rule:** When verifying any state change (focus, visibility, bounds, active screen), copy-paste the raw evidence from the dump before claiming success — never summarize without quoting it.

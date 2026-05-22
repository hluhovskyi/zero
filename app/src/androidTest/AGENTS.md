# app/src/androidTest — Agent Guide

End-to-end instrumented tests. Activity launches via `createAndroidComposeRule<MainActivity>`, DB is cleared between tests by `BaseE2eTest.clearDataRule`, and tests interact through the per-screen Robots under `robots/`.

## Rules

1. **Run via `./scripts/run-android-tests.sh`** — it pins `ANDROID_SERIAL` to this worktree's emulator so concurrent sessions don't fight over the device pool. `./gradlew :app:connectedDebugAndroidTest` raw hits every connected AVD.
2. **Don't bump timeouts to "fix" a flake** — a `timeoutMillis = N + something` patch is a smell. Diagnose first: `adb logcat`, UI dump, manual repro of the same steps. Bumping hides what's actually slow and will rot.
3. **AndroidX Test Orchestrator is required for process isolation** — Application singletons (Room flows, scoped coroutines) leak across tests in the same process and cause cross-test flakes. Don't add `clearPackageData: true` on top — it `pm clear`s before the first test and races the Activity's `setContent`, breaking the first test on slower devices.
4. **Robot per screen, fluent return type** — `TransactionsRobot.tapAddTransaction()` returns `TransactionEditRobot`; `apply()` returns back to `TransactionsRobot`. Don't reach into composeRule directly from a test — extend the robot.
5. **Wait on what you assert, not what you tap** — robots wait for the target element via `composeRule.waitUntil { onAllNodesWithText(...).isNotEmpty() }` before clicking. Bare `onNodeWithText(...).performClick()` will throw on a frame where the node hasn't entered composition yet.

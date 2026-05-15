# scripts/ — Agent Tooling

Helper scripts for AI agents working in this repo. Allowlisted in `.claude/settings.json` so they don't trigger permission prompts.

## Routing & Enforcement (read first)

Every worktree has at most one `.emulator-serial` file naming the emulator that worktree owns. All adb traffic from this worktree must go through `./scripts/ui/adb` so it inherits `ANDROID_SERIAL`. Two safeguards enforce this:

1. **The wrapper itself.** `./scripts/ui/adb` reads `.emulator-serial`, exports `ANDROID_SERIAL`, then exec's adb. If the file is missing or the named emulator isn't running, it fails loudly with a remediation message — it never falls back to the default device.
2. **PreToolUse hook (`scripts/guard-adb.sh`).** Denies bare `adb …` and `./gradlew installDebug` with a message pointing at the wrappers. Bare `adb devices`, `adb start-server`, `adb kill-server`, and `adb version` are allow-listed because they don't target a specific device.

Result: a session in worktree A cannot accidentally talk to or install onto worktree B's emulator.

## emulator/ — Capacity management

- `emulator/acquire [--no-auto-start]` — claim an unclaimed running emulator. If all are claimed and `--no-auto-start` is not set, spawn a new one via `emulator/start`. Writes `.emulator-serial`. Run this **explicitly** when entering UI verification, not at session start.
- `emulator/release [--kill]` — delete `.emulator-serial`. With `--kill`, also send `adb emu kill` to terminate the emulator process.
- `emulator/start [<avd>]` — boot an unrun AVD on a free port with `-read-only` so a 2nd instance of the same AVD is possible. Prints the serial. Holds the repo lock from port selection through boot so concurrent sessions can't race.

All three coordinate via a shared `flock` on `/tmp/zero-emulator-claim.<repo-hash>.lock`.

## ui/ — On-device interaction & inspection

All UI scripts pin to this worktree's emulator via `.emulator-serial`. Never call bare `adb` in scripts — go through `ui/adb` so the serial flows through.

- `ui/adb <adb-args>` — adb wrapper. Reads `.emulator-serial`, sets `ANDROID_SERIAL`, exec's adb. Fails loud if `.emulator-serial` is missing or names an emulator that isn't connected. Server-level subcommands (`devices`, `start-server`, `kill-server`, `version`) work without a serial so `acquire` can use them.
- `ui/dump-ui.sh [--raw]` — current screen hierarchy. Default: `bounds  label` per node. `--raw`: full XML.
- `ui/tap-label.sh <label> [--screenshot] [--verify <landmark>]` — tap a node by exact text/content-desc.
- `ui/verify-screen.sh <landmark>` — re-dump and check a landmark is visible. Exit 0 if found.
- `ui/open-screen.sh <name>` — pre-baked tap chains to common screens.

## install-app.sh — APK install (replaces `installDebug`)

- `install-app.sh` — builds `assembleDebug` and installs the APK via `ui/adb install -r`. Use this instead of `./gradlew :app:installDebug`, which installs to every connected device.

## github/ — PR helpers

- `github/wait-for-ci.sh <pr_number> [repo]` — block until CI completes. Exits 1 with remediation on BEHIND / DIRTY / BLOCKED.

## Other

- `detect-worktree.sh` — emits `IS_WORKTREE=yes|no`, branch info. Used by `lets-do` skill.
- `fetch-design.sh` — fetches Claude Design HTML for the `fetch-design` skill.
- `guard-branch-switch.sh` — PreToolUse hook that blocks branch switches on master.
- `guard-adb.sh` — PreToolUse hook that blocks bare `adb` / `installDebug`.
- `lib/emulator.py` — shared helpers for the Python orchestrator scripts (lock, adb-devices, worktree scan).

## Rules

- **Don't bypass `ui/adb`.** Direct `adb` calls hit whichever device is first in `adb devices` and clobber sibling worktrees. The hook will deny them.
- **Don't use `./gradlew installDebug`.** Use `./scripts/install-app.sh` instead. The hook will deny `installDebug`.
- **Fail loud.** Scripts that swallow stderr/exit codes turn into silent no-ops; surface the actual adb / curl / gradle error.
- **One responsibility per script.** If you reach for `&&`/`;` to chain unrelated work, write a second script instead.

# scripts/ — Agent Tooling

Helper scripts for AI agents working in this repo. Allowlisted in user settings — invoke directly, don't shell out to raw `adb`/`emulator`.

## ui/ — On-device interaction & inspection

All UI scripts pin to this worktree's emulator via `.emulator-serial`. Never call bare `adb` in scripts — go through `ui/adb.sh` so the serial flows through.

- `ui/adb.sh <adb-args>` — adb wrapper. Reads `.emulator-serial`, sets `ANDROID_SERIAL`, exec's adb. Use this for any one-off adb call.
- `ui/dump-ui.sh [--raw]` — current screen hierarchy. Fails loud if the dump is empty or the device is unreachable. Default: `bounds  label` per node. `--raw`: full XML.
- `ui/tap-label.sh <label> [--screenshot] [--verify <landmark>]` — tap a node by exact text/content-desc. `--verify` re-dumps and asserts a landmark afterward.
- `ui/verify-screen.sh <landmark>` — re-dump and check a text/content-desc is visible. Exit 0 if found.
- `ui/open-screen.sh <name>` — pre-baked tap chains to common screens: `import`, `settings`, `accounts`, `categories`, `icon-picker-expense`, `icon-picker-income`, `icon-picker-account`. Add a new case when you hit a screen often.

## emulator/ — Capacity management

- `emulator/start-emulator.sh [<avd>]` — boot an unrun AVD on a free port with `-read-only` (so a 2nd instance of the same AVD is possible). Prints the serial on success.
- `emulator/acquire-emulator.sh [--no-auto-start]` — claim an unclaimed running emulator. If all are claimed, auto-invokes `start-emulator.sh` (suppress with the flag). Writes `.emulator-serial`.

## github/ — PR helpers

- `github/wait-for-ci.sh <pr_number> [repo]` — block until merged. Exits 1 with a clear remediation on BEHIND / DIRTY / BLOCKED (without auto-merge) — does NOT silently poll forever.

## Other

- `detect-worktree.sh` — emits `IS_WORKTREE=yes|no`, branch info. Used by `lets-do` skill for Step 0 detection.
- `fetch-design.sh` — fetches Claude Design HTML for the `fetch-design` skill.
- `guard-branch-switch.sh` — pre-commit guard that refuses commits on master in the main workspace.

## Rules

- **Don't bypass `ui/adb.sh`.** Direct `adb` calls in scripts duplicate the `.emulator-serial` sourcing and drift when the resolution logic changes.
- **Fail loud.** Scripts swallowing stderr/exit codes turn into silent no-ops that waste turns; surface the actual adb / curl / gradle error.
- **One responsibility per script.** If you reach for `&&`/`;` to chain unrelated work, write a second script instead.

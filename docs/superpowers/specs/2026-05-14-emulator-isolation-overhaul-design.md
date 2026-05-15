# Emulator Isolation Overhaul — Design

## Problem

When multiple Claude Code sessions run in parallel (each in its own worktree), commands
issued from one session reach into another session's emulator. Symptoms reported in
practice include:

- `./gradlew :app:installDebug` installs the APK to **every** connected device, clobbering
  whichever APKs the sibling sessions had deployed.
- A raw `adb shell …` from one session targets the "default" device (first one listed),
  which may belong to another session.
- `./scripts/ui/adb.sh` silently falls back to default-device behaviour when
  `.emulator-serial` is missing, so the safety mechanism degrades quietly instead of
  failing.
- Concurrent `start-emulator.sh` invocations can race on port selection.

The user-visible bug: **two parallel sessions cannot run UI verification reliably**.
A workaround memory exists telling agents to skip UI inspection when concurrent — that
should not be necessary.

## Goal

Make sibling-session interference structurally impossible. After this change:

- Every `adb` and `gradle install*` command in any session is pinned to that worktree's
  one assigned emulator.
- Touching another worktree's emulator from this session requires deliberate action.
- The agent does not need to remember new entry points — raw `adb …` and
  `./gradlew installDebug` keep working from its perspective; they are silently routed
  through the safe path.

## Non-goals

- True OS-level isolation (network namespaces, Docker per emulator). Out of scope —
  overkill for the threat model.
- Per-session ADB-server-port separation. Every adb server scans the same hardcoded
  port range (5555–5681) for emulators, so daemon split doesn't actually hide anything.
- Changing the `-read-only` AVD strategy. It works; keep it.

## Design

### State

- **`.emulator-serial`** — per-worktree file at the worktree root containing exactly one
  emulator serial (e.g. `emulator-5554`). Kept; semantics unchanged. Untracked.

### Wrappers (the named, safe entry points)

| Script | Purpose | Language |
|---|---|---|
| `scripts/emulator/acquire` | Claim or start an emulator; write `.emulator-serial` | Python |
| `scripts/emulator/release` | Delete `.emulator-serial`; `--kill` also terminates the emulator process | Python |
| `scripts/emulator/start` | Boot an unrun AVD on a free port; print serial | Python |
| `scripts/ui/adb` | Reads `.emulator-serial`, exports `ANDROID_SERIAL`, exec's real adb. **Fails loudly** when serial is missing or the named emulator isn't running | Python |
| `scripts/install-app.sh` | Build `assembleDebug` then install via `scripts/ui/adb` | Bash |
| `scripts/ui/dump-ui.sh` | Unchanged behaviour; routes through new `scripts/ui/adb` | Bash |
| `scripts/ui/tap-label.sh` | Unchanged behaviour; routes through new `scripts/ui/adb` | Bash |
| `scripts/ui/verify-screen.sh` | Unchanged behaviour; routes through new `scripts/ui/adb` | Bash |
| `scripts/ui/open-screen.sh` | Unchanged behaviour; routes through new `scripts/ui/adb` | Bash |

Python is the right choice for the orchestrator pieces (acquire/release/start/adb) where
correctness under concurrency matters: `fcntl.flock` advisory locks, structured argument
parsing, clean error handling. Bash stays for the UI scripts where the work is mostly
calling adb and parsing XML — rewriting them adds churn without payoff.

### Locking

- One advisory exclusive lock per repository: `/tmp/zero-emulator-claim.<repo-hash>.lock`.
- Acquired with `fcntl.flock(fd, LOCK_EX)`. Released on context-manager exit.
- Stale-lock recovery: rely on `flock` releasing on process death. (Belt-and-suspenders
  PID + age check kept for legacy file remnants only — flock itself is self-cleaning.)

### Hook (the invisible router)

A single PreToolUse Bash hook at `scripts/guard-adb.sh`:

| Input command (regex match) | Rewritten to |
|---|---|
| `adb <args>` (bare, not via wrapper) | `./scripts/ui/adb <args>` |
| `./gradlew … installDebug …` | `./scripts/install-app.sh` |
| `./gradlew … :app:installDebug …` | `./scripts/install-app.sh` |
| anything else | passed through |

The hook never rejects — it transforms. If the resulting wrapper rejects (no serial /
emulator dead), that's the wrapper's job and it has the remediation message.

The existing `scripts/guard-branch-switch.sh` is also a PreToolUse Bash hook; both hooks
co-exist. Hook configuration lives in `.claude/settings.json`.

### Wrapper failure modes

`scripts/ui/adb` (the Python wrapper) handles three states:

1. **`.emulator-serial` missing** — print "No emulator claimed for this worktree. Run
   `./scripts/emulator/acquire` when you reach UI verification." Exit 2.
2. **`.emulator-serial` exists but the named emulator isn't in `adb devices`** — print
   "Claimed emulator `<serial>` is not running. Either start it or re-acquire." Exit 3.
3. **Healthy** — `export ANDROID_SERIAL=<serial>`; `os.execvp("adb", ["adb", *args])`.

Plain `adb` commands that don't need a specific device (`adb devices`, `adb start-server`,
`adb kill-server`) — the wrapper sets `ANDROID_SERIAL` if present but does not gate on it.
These need to keep working for `acquire` itself.

### Acquire algorithm (unchanged, ported to Python)

```
with file_lock(repo_lock_path):
    running = [serial for serial in adb_devices() if serial.startswith("emulator-")]
    if not running:
        if auto_start:
            return start_new_and_claim()
        die("No running emulators")

    claimed = set()
    for wt in git_worktree_list():
        serial_file = wt / ".emulator-serial"
        if not serial_file.is_file():
            continue
        serial = serial_file.read_text().strip()
        if serial in running:
            claimed.add(serial)
        else:
            serial_file.unlink()                    # prune stale claim

    for serial in running:
        if serial not in claimed:
            write_serial(serial)
            return success(serial)

    if auto_start:
        return start_new_and_claim()
    die("All running emulators claimed")
```

### Release algorithm

```
serial = read_serial_or_exit_quietly()
unlink(".emulator-serial")
if args.kill:
    adb_emu_kill(serial)        # console: `adb -s <serial> emu kill`
print(f"Released {serial}")
```

### Start algorithm (ported with port-race fix)

The current bash version uses `lsof` to find a free port, then starts the emulator with
that port. Between `lsof` returning and `nohup emulator -port` actually binding, another
session can grab the port. Mitigation: hold the repo lock from port-pick through
"emulator process started successfully," so concurrent starts serialise on the same lock
that `acquire` uses.

### Repository layout after the change

```
scripts/
├── emulator/
│   ├── acquire           # Python; +x
│   ├── release           # Python; +x
│   └── start             # Python; +x
├── ui/
│   ├── adb               # Python; +x
│   ├── dump-ui.sh
│   ├── tap-label.sh
│   ├── verify-screen.sh
│   └── open-screen.sh
├── install-app.sh        # Bash
├── guard-adb.sh          # Bash; hook entry point
├── guard-branch-switch.sh
├── detect-worktree.sh
└── lib/
    └── emulator.py       # Shared module: lock, adb-devices, worktree-scan, etc.
```

Old `.sh` files (`acquire-emulator.sh`, `release-emulator.sh`, `start-emulator.sh`,
`scripts/ui/adb.sh`) are deleted, not kept as wrappers — clean break.

### Documentation

Update **in the same PR**:

- `scripts/AGENTS.md` — replace existing tooling section with the new layout. Concise
  reference for the agent on what each script does and why the routing exists. **This is
  the doc the user requested as part of the deliverable.**
- `.claude/plugins/zero-project/skills/android-ui-inspector/SKILL.md` — replace the
  `acquire-emulator.sh` reference with `scripts/emulator/acquire`; explain the wrapper
  fail-loud behaviour.
- `.claude/plugins/zero-project/skills/lets-do/SKILL.md` — replace
  `acquire-emulator.sh` reference with `scripts/emulator/acquire`.
- `docs/agents/execution-workflow.md` — replace `./gradlew installDebug && ./scripts/ui/open-screen.sh <screen>`
  with `./scripts/install-app.sh && ./scripts/ui/open-screen.sh <screen>`.
- `AGENTS.md` (root) — no changes needed; it doesn't name these scripts directly.

### Hook wiring

`.claude/settings.json` gains an entry under `hooks.PreToolUse[*matcher=Bash]`:

```json
{
  "type": "command",
  "command": "./scripts/guard-adb.sh"
}
```

Sits alongside the existing `guard-branch-switch.sh` entry. The hook reads the tool input
on stdin, decides whether to rewrite, and either passes through silently or emits a
`hookSpecificOutput.modifiedCommand` payload.

(Note: the Claude Code hook interface for *rewriting* a command — vs. denying it — is
verified during implementation. If rewriting isn't supported, the hook falls back to
**denying** the bare-adb / bare-install command with a remediation message pointing at
the wrapper; the wrapper-first model still holds because the user picked
"agent-facing wrappers are documented; raw calls are caught.")

### Testing approach

Pure-script changes; no app code. Verification is:

1. **Unit-ish:** run each Python script through `python3 -m py_compile`. Confirm syntax.
2. **Single-session smoke:** in this worktree, run `scripts/emulator/acquire` →
   `scripts/ui/adb devices` → `scripts/emulator/release`. Verify each step prints what
   the old script printed.
3. **Concurrent simulation:** open two worktrees side by side, run `acquire` in both
   simultaneously, confirm each gets a different serial and the lock didn't deadlock.
   (Manual — script in the plan's verification section.)
4. **Hook smoke:** in a Bash session within a worktree without `.emulator-serial`,
   type `adb devices` — confirm the hook routes through `scripts/ui/adb`, which rejects
   with the remediation message.
5. **Hook smoke 2:** type `./gradlew :app:installDebug` — confirm the hook routes to
   `./scripts/install-app.sh`.
6. **Test suite + lint** — `./gradlew testDebugUnitTest`, `./gradlew lintDebug`.
   No app code changed, both should pass unchanged.

This change is purely infrastructural — `zero-project:android-ui-inspector` itself isn't
run as part of verification because the change has no Android UI surface. (Confirming the
inspector still *works* after the rename is part of step 2.)

## Acceptance criteria

- All `scripts/emulator/*.sh` and `scripts/ui/adb.sh` are deleted; replaced by the new
  Python files.
- `scripts/guard-adb.sh` exists and is wired in `.claude/settings.json`.
- `scripts/install-app.sh` exists.
- `scripts/AGENTS.md` reflects the new layout and explains the routing model.
- Inspector skill SKILL.md and lets-do SKILL.md point at the new scripts.
- `./gradlew testDebugUnitTest` and `./gradlew lintDebug` pass.
- Manual smoke: `acquire` → `adb devices` → `release` works in this worktree.
- Manual smoke: typing `adb devices` from a fresh worktree (no `.emulator-serial`)
  produces the wrapper's remediation error, not a default-device hit.

## Open items resolved during brainstorming

- **Language:** Python for the orchestrator pieces; bash kept for UI scripts.
- **Isolation strength:** `ANDROID_SERIAL` everywhere is the real fence; per-session adb
  server gives no extra isolation, dropped.
- **Routing:** auto-rewriting hook (transparent to agent) over reject-only hook.
- **No-serial path:** wrapper rejects with remediation; acquire is explicit and called at
  the UI verification step, not on every adb invocation.

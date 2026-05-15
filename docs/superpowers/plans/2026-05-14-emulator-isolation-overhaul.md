# Emulator Isolation Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bash emulator-claim stack with a Python orchestrator + a deny-with-remediation hook so parallel Claude Code sessions cannot touch each other's emulators.

**Architecture:** Python (`scripts/emulator/{acquire,release,start}` + `scripts/ui/adb` + `scripts/lib/emulator.py`) for the orchestrator pieces that need correctness under concurrency. Bash kept for the UI scripts and the new `scripts/install-app.sh`. A PreToolUse Bash hook (`scripts/guard-adb.sh`) denies bare `adb …` and `./gradlew install*` with remediation pointing at the wrappers.

**Tech Stack:** Python 3 (stdlib only — `fcntl`, `pathlib`, `subprocess`, `argparse`), bash, `jq`, Claude Code hook protocol.

**Spec:** `docs/superpowers/specs/2026-05-14-emulator-isolation-overhaul-design.md`

---

## Conventions for this plan

- Every Python script uses `#!/usr/bin/env python3`, `from __future__ import annotations`, type hints, and `if __name__ == "__main__": main()`.
- Every new script has its existing-script analog named in the task header so the executor can pattern-match without re-derivation.
- File deletions happen in a dedicated task at the end so earlier tasks can reference the old files for diffing.
- "Smoke" steps run inside this worktree. They need a real emulator only where explicitly noted — most steps verify behaviour in dry runs.

---

### Task 1: Shared library `scripts/lib/emulator.py`

**Analog:** lock helpers in `scripts/emulator/acquire-emulator.sh` lines 29–70; device-scan in `acquire-emulator.sh` lines 76–113.

**Files:**
- Create: `scripts/lib/__init__.py` (empty)
- Create: `scripts/lib/emulator.py`

- [ ] **Step 1: Create empty `__init__.py`**

```bash
mkdir -p scripts/lib
touch scripts/lib/__init__.py
```

- [ ] **Step 2: Write `scripts/lib/emulator.py`**

```python
#!/usr/bin/env python3
"""Shared helpers for emulator orchestration scripts.

Concurrency model: one repo-wide flock at REPO_LOCK_PATH. Acquire/start
hold it across the full claim-or-spawn flow so concurrent sessions
serialise without racing on ports or serial files.
"""
from __future__ import annotations

import contextlib
import fcntl
import hashlib
import os
import subprocess
from pathlib import Path
from typing import Iterator


def repo_root() -> Path:
    """Return the repo's common git dir's parent (works inside worktrees)."""
    common = subprocess.check_output(
        ["git", "rev-parse", "--git-common-dir"], text=True
    ).strip()
    return Path(common).resolve().parent


def worktree_root() -> Path:
    """Return the current worktree root."""
    out = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    ).strip()
    return Path(out).resolve()


def repo_lock_path() -> Path:
    """One lock file per repo. Hash the path so different repos don't collide."""
    digest = hashlib.sha1(str(repo_root()).encode()).hexdigest()[:12]
    return Path(f"/tmp/zero-emulator-claim.{digest}.lock")


@contextlib.contextmanager
def repo_lock(timeout_seconds: int = 300) -> Iterator[None]:
    """Acquire an exclusive flock on the repo lock file.

    flock self-releases on process death; no stale-lock recovery needed.
    """
    path = repo_lock_path()
    path.touch(exist_ok=True)
    fd = os.open(path, os.O_RDWR)
    try:
        import signal

        def _timeout(_sig, _frame):
            raise TimeoutError(f"timed out waiting for {path} after {timeout_seconds}s")

        prev = signal.signal(signal.SIGALRM, _timeout)
        signal.alarm(timeout_seconds)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
        finally:
            signal.alarm(0)
            signal.signal(signal.SIGALRM, prev)
        yield
    finally:
        try:
            fcntl.flock(fd, fcntl.LOCK_UN)
        finally:
            os.close(fd)


def adb_devices() -> list[str]:
    """Return list of currently-attached emulator serials."""
    out = subprocess.run(
        ["adb", "devices"], capture_output=True, text=True, check=False
    ).stdout
    serials: list[str] = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[0].startswith("emulator-") and parts[1] == "device":
            serials.append(parts[0])
    return serials


def git_worktrees() -> list[Path]:
    """Return paths of all worktrees of this repo."""
    out = subprocess.check_output(
        ["git", "worktree", "list", "--porcelain"], text=True
    )
    paths: list[Path] = []
    for line in out.splitlines():
        if line.startswith("worktree "):
            paths.append(Path(line[len("worktree "):]).resolve())
    return paths


def read_serial_file(worktree: Path) -> str | None:
    f = worktree / ".emulator-serial"
    if not f.is_file():
        return None
    return f.read_text().strip() or None


def write_serial_file(worktree: Path, serial: str) -> None:
    (worktree / ".emulator-serial").write_text(serial + "\n")


def remove_serial_file(worktree: Path) -> None:
    f = worktree / ".emulator-serial"
    if f.exists():
        f.unlink()
```

- [ ] **Step 3: Verify it imports cleanly**

```bash
python3 -c "import sys; sys.path.insert(0, 'scripts'); from lib import emulator; print(emulator.repo_root())"
```

Expected: prints `/Users/.../Projects/zero` (or the common-dir parent of this worktree).

- [ ] **Step 4: Commit**

```bash
git add scripts/lib/__init__.py scripts/lib/emulator.py
git commit -m "feat(scripts): shared emulator orchestration library"
```

---

### Task 2: `scripts/emulator/start` (new Python)

**Analog:** `scripts/emulator/start-emulator.sh`. Port-race fix: hold the repo lock from port pick through boot.

**Files:**
- Create: `scripts/emulator/start`

- [ ] **Step 1: Write `scripts/emulator/start`**

```python
#!/usr/bin/env python3
"""Start an unrun AVD on a free port. Prints the serial on success.

Holds the repo lock during port selection + emulator spawn so concurrent
sessions don't collide.
"""
from __future__ import annotations

import argparse
import os
import socket
import subprocess
import sys
import time
from pathlib import Path

# Import shared lib by absolute path so this script is callable from any cwd.
_HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(_HERE.parent))
from lib.emulator import adb_devices, repo_lock  # noqa: E402

SDK = Path(os.environ.get("ANDROID_HOME") or Path.home() / "Library/Android/sdk")
EMULATOR_BIN = SDK / "emulator" / "emulator"
PORT_MIN, PORT_MAX = 5554, 5680
BOOT_TIMEOUT_SECONDS = 180


def list_avds() -> list[str]:
    out = subprocess.run(
        [str(EMULATOR_BIN), "-list-avds"], capture_output=True, text=True, check=False
    ).stdout
    return [name for name in (line.strip() for line in out.splitlines()) if name]


def running_avd_names() -> set[str]:
    names: set[str] = set()
    for serial in adb_devices():
        out = subprocess.run(
            ["adb", "-s", serial, "emu", "avd", "name"],
            capture_output=True, text=True, check=False,
        ).stdout
        first = (out.splitlines() or [""])[0].strip()
        if first:
            names.add(first)
    return names


def pick_avd(preferred: str | None) -> str:
    available = list_avds()
    if not available:
        sys.exit("No AVDs configured. Create one in Android Studio first.")
    if preferred:
        if preferred not in available:
            sys.exit(f"AVD '{preferred}' not found. Available: {available}")
        return preferred
    running = running_avd_names()
    for avd in available:
        if avd not in running:
            return avd
    print(f"All AVDs already running; will start a 2nd -read-only instance of {available[0]}", file=sys.stderr)
    return available[0]


def find_free_port() -> int:
    for port in range(PORT_MIN, PORT_MAX + 1, 2):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            try:
                s.bind(("127.0.0.1", port))
                return port
            except OSError:
                continue
    sys.exit(f"No free emulator ports between {PORT_MIN} and {PORT_MAX}.")


def spawn_emulator(avd: str, port: int) -> str:
    serial = f"emulator-{port}"
    log = Path(f"/tmp/{serial}.log")
    cmd = [
        str(EMULATOR_BIN), "-avd", avd, "-port", str(port),
        "-read-only", "-no-snapshot-load", "-no-boot-anim",
    ]
    print(f"▶ Starting AVD '{avd}' on {serial} (log: {log})", file=sys.stderr)
    with open(log, "wb") as f:
        subprocess.Popen(
            cmd, stdout=f, stderr=subprocess.STDOUT,
            start_new_session=True, close_fds=True,
        )
    return serial


def wait_for_boot(serial: str) -> None:
    print("  waiting for boot...", file=sys.stderr)
    deadline = time.time() + BOOT_TIMEOUT_SECONDS
    while time.time() < deadline:
        out = subprocess.run(
            ["adb", "-s", serial, "shell", "getprop", "sys.boot_completed"],
            capture_output=True, text=True, check=False,
        ).stdout.strip()
        if out == "1":
            print(f"  ✓ {serial} booted", file=sys.stderr)
            return
        time.sleep(3)
    sys.exit(f"Timed out waiting for {serial} to boot. Check /tmp/{serial}.log")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("avd", nargs="?", help="AVD name (default: first non-running)")
    parser.add_argument("--no-lock", action="store_true",
                        help="Skip the repo lock — only safe if caller holds it")
    args = parser.parse_args()

    if not EMULATOR_BIN.is_file() or not os.access(EMULATOR_BIN, os.X_OK):
        sys.exit(f"Emulator binary not found at {EMULATOR_BIN}. Set ANDROID_HOME.")

    def _run() -> None:
        avd = pick_avd(args.avd)
        port = find_free_port()
        serial = spawn_emulator(avd, port)
        wait_for_boot(serial)
        print(serial)

    if args.no_lock:
        _run()
    else:
        with repo_lock():
            _run()


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Make executable**

```bash
chmod +x scripts/emulator/start
```

- [ ] **Step 3: Syntax check**

```bash
python3 -m py_compile scripts/emulator/start
```

Expected: no output.

- [ ] **Step 4: Help-text smoke (does not start an emulator)**

```bash
./scripts/emulator/start --help
```

Expected: argparse help text mentioning `avd` and `--no-lock`.

- [ ] **Step 5: Commit**

```bash
git add scripts/emulator/start
git commit -m "feat(scripts): port start-emulator to Python with locked port pick"
```

---

### Task 3: `scripts/emulator/acquire` (new Python)

**Analog:** `scripts/emulator/acquire-emulator.sh`. Uses the shared lock + lib helpers.

**Files:**
- Create: `scripts/emulator/acquire`

- [ ] **Step 1: Write `scripts/emulator/acquire`**

```python
#!/usr/bin/env python3
"""Assign an unclaimed running emulator to this worktree.

Scans all worktrees for .emulator-serial to build the claimed set, picks the
first running emulator not in that set, writes .emulator-serial in this
worktree. If all are claimed, auto-invokes scripts/emulator/start (suppress
with --no-auto-start).
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(_HERE.parent))
from lib.emulator import (  # noqa: E402
    adb_devices,
    git_worktrees,
    read_serial_file,
    remove_serial_file,
    repo_lock,
    worktree_root,
    write_serial_file,
)


def claim_unused(this_worktree: Path) -> str | None:
    """Return a serial to claim, or None if all running are claimed."""
    running = adb_devices()
    if not running:
        return None

    claimed: set[str] = set()
    for wt in git_worktrees():
        serial = read_serial_file(wt)
        if not serial:
            continue
        if serial in running:
            claimed.add(serial)
        else:
            remove_serial_file(wt)
            print(
                f"  Pruned stale claim: {serial} from {wt.name} (emulator not running)",
                file=sys.stderr,
            )

    for serial in running:
        if serial not in claimed:
            write_serial_file(this_worktree, serial)
            return serial
    return "__ALL_CLAIMED__"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-auto-start", action="store_true",
                        help="Don't spawn a new emulator if all running are claimed")
    args = parser.parse_args()

    this = worktree_root()
    with repo_lock():
        result = claim_unused(this)

        if result and result != "__ALL_CLAIMED__":
            print(f"Assigned {result} to this worktree")
            return

        if args.no_auto_start:
            if result is None:
                sys.exit("No running emulators. Run: ./scripts/emulator/start")
            sys.exit("All running emulators are claimed by other worktrees. "
                     "Run: ./scripts/emulator/start")

        # Spawn a new emulator while holding the lock (--no-lock avoids deadlock).
        print("No free running emulator — starting a new one "
              "(use --no-auto-start to disable)...", file=sys.stderr)
        start_script = _HERE / "start"
        proc = subprocess.run(
            [str(start_script), "--no-lock"], capture_output=True, text=True, check=False,
        )
        sys.stderr.write(proc.stderr)
        if proc.returncode != 0:
            sys.exit(f"scripts/emulator/start failed (exit {proc.returncode})")
        serial = proc.stdout.strip().splitlines()[-1]
        if not serial.startswith("emulator-"):
            sys.exit(f"start did not print a serial. Got: {serial!r}")
        write_serial_file(this, serial)
        print(f"Started and assigned {serial} to this worktree")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Make executable and syntax-check**

```bash
chmod +x scripts/emulator/acquire
python3 -m py_compile scripts/emulator/acquire
```

- [ ] **Step 3: Help-text smoke**

```bash
./scripts/emulator/acquire --help
```

Expected: argparse help mentioning `--no-auto-start`.

- [ ] **Step 4: Dry-run smoke (no emulator required if none running)**

```bash
./scripts/emulator/acquire --no-auto-start
```

Expected (if no emulators running): `No running emulators. Run: ./scripts/emulator/start` + exit 1.
This proves the script's first code path works without actually spawning anything.

- [ ] **Step 5: Commit**

```bash
git add scripts/emulator/acquire
git commit -m "feat(scripts): port acquire-emulator to Python with flock"
```

---

### Task 4: `scripts/emulator/release` (new Python)

**Analog:** `scripts/emulator/release-emulator.sh`. Adds `--kill` flag.

**Files:**
- Create: `scripts/emulator/release`

- [ ] **Step 1: Write the script**

```python
#!/usr/bin/env python3
"""Release this worktree's emulator claim. Optionally kill the emulator too."""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(_HERE.parent))
from lib.emulator import read_serial_file, remove_serial_file, worktree_root  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kill", action="store_true",
                        help="Also send `adb emu kill` to terminate the emulator process")
    args = parser.parse_args()

    this = worktree_root()
    serial = read_serial_file(this)
    if not serial:
        print(f"No emulator claimed by this worktree (no .emulator-serial).")
        return

    remove_serial_file(this)
    print(f"Released {serial} (removed .emulator-serial)")

    if args.kill:
        result = subprocess.run(
            ["adb", "-s", serial, "emu", "kill"],
            capture_output=True, text=True, check=False,
        )
        if result.returncode == 0:
            print(f"Sent emu kill to {serial}")
        else:
            sys.stderr.write(result.stderr)
            print(f"Could not kill {serial} (may already be down)", file=sys.stderr)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Make executable and syntax-check**

```bash
chmod +x scripts/emulator/release
python3 -m py_compile scripts/emulator/release
```

- [ ] **Step 3: Smoke**

```bash
./scripts/emulator/release
```

Expected (no `.emulator-serial` in this worktree): `No emulator claimed by this worktree (no .emulator-serial).`

- [ ] **Step 4: Commit**

```bash
git add scripts/emulator/release
git commit -m "feat(scripts): port release-emulator to Python with --kill flag"
```

---

### Task 5: `scripts/ui/adb` (new Python)

**Analog:** `scripts/ui/adb.sh`. Critical change: **fails loud** when `.emulator-serial` is missing or its emulator isn't connected, instead of silently falling back to default device.

**Files:**
- Create: `scripts/ui/adb`

- [ ] **Step 1: Write the wrapper**

```python
#!/usr/bin/env python3
"""adb wrapper pinned to this worktree's .emulator-serial.

Behaviour:
- If invoked with one of {devices, start-server, kill-server, version, help},
  exec adb directly without requiring a serial. These are server-level commands
  that don't need pinning and are required for acquire to work.
- Else require .emulator-serial; export ANDROID_SERIAL; verify the emulator is
  connected; exec adb. Fail with a clear remediation if any check fails.
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(_HERE.parent))
from lib.emulator import adb_devices, read_serial_file, worktree_root  # noqa: E402

SERVER_LEVEL_COMMANDS = {"devices", "start-server", "kill-server", "version", "help", "--version", "--help"}


def main() -> None:
    args = sys.argv[1:]
    first = args[0] if args else ""

    if first in SERVER_LEVEL_COMMANDS:
        os.execvp("adb", ["adb", *args])
        return  # unreachable

    serial = read_serial_file(worktree_root())
    if not serial:
        sys.exit(
            "No emulator claimed for this worktree (no .emulator-serial).\n"
            "Run: ./scripts/emulator/acquire when you reach UI verification."
        )

    if serial not in adb_devices():
        sys.exit(
            f"Claimed emulator '{serial}' is not running.\n"
            f"Either start it (./scripts/emulator/start) or re-acquire "
            f"(./scripts/emulator/release && ./scripts/emulator/acquire)."
        )

    os.environ["ANDROID_SERIAL"] = serial
    os.execvp("adb", ["adb", *args])


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Make executable and syntax-check**

```bash
chmod +x scripts/ui/adb
python3 -m py_compile scripts/ui/adb
```

- [ ] **Step 3: Smoke — server-level command works without serial**

```bash
./scripts/ui/adb devices
```

Expected: the list of attached devices (or none), no error about `.emulator-serial`.

- [ ] **Step 4: Smoke — pinned command fails without serial**

```bash
rm -f .emulator-serial
./scripts/ui/adb shell echo hello 2>&1 | head -3
```

Expected: error mentioning "No emulator claimed for this worktree" and the remediation.
Exit code should be non-zero (`echo $?` after).

- [ ] **Step 5: Commit**

```bash
git add scripts/ui/adb
git commit -m "feat(scripts): port adb wrapper to Python with fail-loud behaviour"
```

---

### Task 6: `scripts/install-app.sh`

**Analog:** the `./gradlew :app:installDebug` lines in `docs/agents/execution-workflow.md` and the warning in `scripts/AGENTS.md`. New script that does the safe equivalent.

**Files:**
- Create: `scripts/install-app.sh`

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
# Builds app/debug APK and installs it via the pinned adb wrapper.
# Replaces `./gradlew :app:installDebug` which installs to every connected device.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "▶ assembleDebug..."
./gradlew :app:assembleDebug

if [ ! -f "$APK" ]; then
  echo "Build succeeded but $APK not found." >&2
  exit 1
fi

echo "▶ installing $APK to this worktree's emulator..."
"$HERE/ui/adb" install -r "$APK"
```

- [ ] **Step 2: Make executable**

```bash
chmod +x scripts/install-app.sh
```

- [ ] **Step 3: Syntax check (don't run — gradle build is slow)**

```bash
bash -n scripts/install-app.sh
```

Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add scripts/install-app.sh
git commit -m "feat(scripts): install-app.sh replaces installDebug to avoid clobbering siblings"
```

---

### Task 7: `scripts/guard-adb.sh` (PreToolUse hook)

**Analog:** `scripts/guard-branch-switch.sh`. Same protocol: read JSON on stdin, exit 0 silently to allow, or print a deny JSON.

**Files:**
- Create: `scripts/guard-adb.sh`

- [ ] **Step 1: Write the hook**

```bash
#!/usr/bin/env bash
# PreToolUse hook for Bash commands. Denies bare `adb` and `gradle install*`
# invocations with a remediation message pointing at the worktree-safe wrappers.
# Called by .claude/settings.json hooks.PreToolUse[matcher=Bash].
set -uo pipefail

input=$(cat)
cmd=$(jq -r '.tool_input.command // ""' <<< "$input")

deny() {
    local reason="$1"
    printf '%s\n' "{\"continue\":false,\"stopReason\":${reason}}"
    exit 0
}

# ── adb: bare invocation NOT through the wrapper ────────────────────────────
# Allow:
#   ./scripts/ui/adb …               (the wrapper itself)
#   adb devices / start-server / kill-server / version  (server-level, allow-listed)
# Deny everything else that calls `adb` at the start or after a pipe/semicolon.
if echo "$cmd" | grep -qE '(^|[|;&]|[[:space:]])adb([[:space:]]|$)'; then
    # If it's the wrapper path, allow.
    if echo "$cmd" | grep -qE '\./scripts/ui/adb([[:space:]]|$)'; then
        :  # wrapper path, fine
    else
        # Check the immediate next token of any bare `adb` invocation.
        # Extract the first token after `adb ` and see if it's allow-listed.
        first_arg=$(echo "$cmd" \
            | grep -oE '(^|[|;&]|[[:space:]])adb([[:space:]]+[^[:space:]|;&]+)?' \
            | head -1 \
            | sed -E 's/.*adb[[:space:]]+([^[:space:]|;&]+).*/\1/')
        case "$first_arg" in
            devices|start-server|kill-server|version|--version|help|--help|adb)
                # bare `adb` with allow-listed first arg, or alone, or no first arg parsed: allow
                ;;
            *)
                deny '"Bare `adb` command would target the default device and can clobber sibling worktrees.\n\nRun via the worktree-pinned wrapper instead:\n  ./scripts/ui/adb '"$first_arg"' …\n\nThe wrapper reads .emulator-serial and pins ANDROID_SERIAL. If no emulator is claimed yet, run ./scripts/emulator/acquire first."'
                ;;
        esac
    fi
fi

# ── gradle installDebug: installs to every device ──────────────────────────
if echo "$cmd" | grep -qE 'gradlew[^|;&]*\binstall(Debug|Release)\b'; then
    deny '"`./gradlew installDebug` installs to EVERY connected device and clobbers sibling worktrees.\n\nUse the worktree-pinned installer:\n  ./scripts/install-app.sh\n\nIt builds assembleDebug and installs only to this worktree'\''s emulator."'
fi

exit 0
```

- [ ] **Step 2: Make executable**

```bash
chmod +x scripts/guard-adb.sh
```

- [ ] **Step 3: Smoke — allow-list works**

```bash
printf '{"tool_input":{"command":"adb devices"}}' | ./scripts/guard-adb.sh; echo "exit=$?"
```

Expected: no output, `exit=0`.

```bash
printf '{"tool_input":{"command":"./scripts/ui/adb shell echo hi"}}' | ./scripts/guard-adb.sh; echo "exit=$?"
```

Expected: no output, `exit=0`.

- [ ] **Step 4: Smoke — bare adb denied**

```bash
printf '{"tool_input":{"command":"adb shell input tap 100 200"}}' | ./scripts/guard-adb.sh
```

Expected: JSON line with `"continue":false` and the remediation in `stopReason`.

- [ ] **Step 5: Smoke — installDebug denied**

```bash
printf '{"tool_input":{"command":"./gradlew :app:installDebug"}}' | ./scripts/guard-adb.sh
```

Expected: JSON line with `"continue":false` mentioning `scripts/install-app.sh`.

- [ ] **Step 6: Smoke — assembleDebug passes through**

```bash
printf '{"tool_input":{"command":"./gradlew :app:assembleDebug"}}' | ./scripts/guard-adb.sh; echo "exit=$?"
```

Expected: no output, `exit=0`.

- [ ] **Step 7: Commit**

```bash
git add scripts/guard-adb.sh
git commit -m "feat(scripts): guard hook denies bare adb and installDebug with remediation"
```

---

### Task 8: Wire the hook in `.claude/settings.json`

**Files:**
- Modify: `.claude/settings.json` — add a new entry in `hooks.PreToolUse` and one new allow-list permission.

- [ ] **Step 1: Add the hook entry and permissions**

In the `hooks.PreToolUse` array, add a new object alongside the existing two:

```json
{
  "matcher": "Bash",
  "hooks": [
    {
      "type": "command",
      "command": "./scripts/guard-adb.sh"
    }
  ]
}
```

In the `permissions.allow` array, replace the existing emulator entry:

- Remove: `"Bash(./scripts/emulator/acquire-emulator.sh *)"`
- Add: `"Bash(./scripts/emulator/acquire *)"`
- Add: `"Bash(./scripts/emulator/acquire)"` (allow without trailing arg)
- Add: `"Bash(./scripts/emulator/release *)"`
- Add: `"Bash(./scripts/emulator/release)"`
- Add: `"Bash(./scripts/ui/adb *)"`
- Add: `"Bash(./scripts/install-app.sh)"`

- [ ] **Step 2: JSON-validate the file**

```bash
jq . .claude/settings.json > /dev/null && echo OK
```

Expected: `OK`.

- [ ] **Step 3: Smoke — verify hook is reachable**

Restart Claude Code is not feasible mid-task; instead simulate the hook invocation manually with the same command Claude would issue and ensure it returns properly. Step 3–5 of Task 7 already cover this.

- [ ] **Step 4: Commit**

```bash
git add .claude/settings.json
git commit -m "chore(settings): wire guard-adb.sh hook and rename allow-listed scripts"
```

---

### Task 9: Update UI scripts to call the new `scripts/ui/adb`

**Analog:** the existing `ADB="$(dirname "$0")/adb.sh"` lines in `dump-ui.sh`, `tap-label.sh`, `verify-screen.sh`, `open-screen.sh`.

**Files:**
- Modify: `scripts/ui/dump-ui.sh:7` — change `ADB="$(dirname "$0")/adb.sh"` to `ADB="$(dirname "$0")/adb"`
- Modify: `scripts/ui/tap-label.sh:15` — same
- Modify: `scripts/ui/verify-screen.sh:8` — same
- Modify: `scripts/ui/open-screen.sh:23` — same

- [ ] **Step 1: Apply the four edits**

In each file replace `adb.sh` with `adb` in the `ADB=...` assignment line. The replacement is one character per file (drop `.sh`). Do not change anything else.

- [ ] **Step 2: Smoke — UI scripts call the new wrapper**

```bash
grep -n 'ADB=' scripts/ui/*.sh
```

Expected: all four lines end with `/adb"` not `/adb.sh"`.

- [ ] **Step 3: Commit**

```bash
git add scripts/ui/dump-ui.sh scripts/ui/tap-label.sh scripts/ui/verify-screen.sh scripts/ui/open-screen.sh
git commit -m "refactor(scripts): UI scripts now call scripts/ui/adb (the Python wrapper)"
```

---

### Task 10: Delete the old `.sh` scripts

**Files:**
- Delete: `scripts/emulator/acquire-emulator.sh`
- Delete: `scripts/emulator/release-emulator.sh`
- Delete: `scripts/emulator/start-emulator.sh`
- Delete: `scripts/ui/adb.sh`

- [ ] **Step 1: Delete the old scripts**

```bash
git rm scripts/emulator/acquire-emulator.sh \
       scripts/emulator/release-emulator.sh \
       scripts/emulator/start-emulator.sh \
       scripts/ui/adb.sh
```

- [ ] **Step 2: Confirm no references remain**

```bash
grep -rn 'acquire-emulator.sh\|release-emulator.sh\|start-emulator.sh\|scripts/ui/adb\.sh' \
  --include='*.sh' --include='*.md' --include='*.json' --include='*.py'
```

Expected: zero hits in code; doc hits are expected in plan docs that are historical (we don't rewrite history). Acceptable to see hits in `docs/superpowers/plans/2026-05-*.md` and similar past plans. **Not acceptable**: hits in `scripts/`, `.claude/settings.json`, `docs/agents/`, or live `SKILL.md` files. Task 11 + 12 fix any live-doc references.

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(scripts): remove legacy bash emulator scripts (replaced by Python)"
```

---

### Task 11: Update `scripts/AGENTS.md` with the new layout

**Analog:** the existing `scripts/AGENTS.md`.

**Files:**
- Modify: `scripts/AGENTS.md` — replace `## ui/` and `## emulator/` sections; add a new `## Routing & Enforcement` section. Keep `## github/` and `## Other` and `## Rules` largely intact (update bullets that name old scripts).

- [ ] **Step 1: Rewrite `scripts/AGENTS.md`**

Replace the existing file with:

```markdown
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
```

- [ ] **Step 2: Sanity check the file**

```bash
wc -l scripts/AGENTS.md
```

Expected: under 80 lines (concise, as the user requested).

- [ ] **Step 3: Commit**

```bash
git add scripts/AGENTS.md
git commit -m "docs(scripts): rewrite AGENTS.md for new isolation model"
```

---

### Task 12: Update remaining live docs

**Files:**
- Modify: `.claude/plugins/zero-project/skills/android-ui-inspector/SKILL.md`
- Modify: `.claude/plugins/zero-project/skills/lets-do/SKILL.md`
- Modify: `docs/agents/execution-workflow.md`

Note: these are symlinks per `docs/agents/skills.md`. Edit them like any file — the symlink target updates.

- [ ] **Step 1: Update inspector SKILL.md**

In `.claude/plugins/zero-project/skills/android-ui-inspector/SKILL.md` (around line 17–20), replace:

```
   ```bash
   ./scripts/emulator/acquire-emulator.sh
   ```
   This claims an unclaimed running emulator, and auto-invokes `./scripts/emulator/start-emulator.sh` to launch a new one (with `-read-only` on a free port) if all running emulators are claimed. Pass `--no-auto-start` to suppress that fallback. Once acquired, confirm the app is installed and running on that emulator.
```

with:

```
   ```bash
   ./scripts/emulator/acquire
   ```
   This claims an unclaimed running emulator, and auto-invokes `./scripts/emulator/start` to launch a new one (with `-read-only` on a free port) if all running emulators are claimed. Pass `--no-auto-start` to suppress that fallback. Once acquired, confirm the app is installed and running via `./scripts/install-app.sh` (not `./gradlew installDebug`, which would clobber sibling worktrees).
```

- [ ] **Step 2: Update lets-do SKILL.md**

In `.claude/plugins/zero-project/skills/lets-do/SKILL.md`, replace every occurrence of:

```
./scripts/emulator/acquire-emulator.sh
```

with:

```
./scripts/emulator/acquire
```

and replace:

```
./scripts/emulator/start-emulator.sh
```

with:

```
./scripts/emulator/start
```

- [ ] **Step 3: Update `docs/agents/execution-workflow.md`**

Replace the line:

```
./gradlew installDebug && ./scripts/ui/open-screen.sh <screen>
```

with:

```
./scripts/install-app.sh && ./scripts/ui/open-screen.sh <screen>
```

- [ ] **Step 4: Verify no live doc still references old script names**

```bash
grep -rn 'acquire-emulator\.sh\|release-emulator\.sh\|start-emulator\.sh\|scripts/ui/adb\.sh\|gradlew[^|;&]*installDebug' \
  --include='*.md' \
  docs/agents/ .claude/plugins/zero-project/skills/ AGENTS.md \
  2>/dev/null
```

Expected: zero hits. Plans under `docs/superpowers/plans/` are historical; do not modify them.

- [ ] **Step 5: Commit**

```bash
git add .claude/plugins/zero-project/skills/android-ui-inspector/SKILL.md \
        .claude/plugins/zero-project/skills/lets-do/SKILL.md \
        docs/agents/execution-workflow.md
git commit -m "docs: update live docs to new emulator script names + install-app.sh"
```

---

### Task 13: Verification

- [ ] **Step 1: Python compile-check all new scripts**

```bash
python3 -m py_compile scripts/lib/emulator.py scripts/emulator/acquire scripts/emulator/release scripts/emulator/start scripts/ui/adb
```

Expected: no output.

- [ ] **Step 2: Bash syntax-check the new bash scripts**

```bash
bash -n scripts/guard-adb.sh scripts/install-app.sh
```

Expected: no output.

- [ ] **Step 3: Hook regression tests (allow + deny matrix)**

Run each of these and confirm the expected outcome (paste actual outputs into the PR body):

| Command piped to `./scripts/guard-adb.sh` | Expected |
|---|---|
| `{"tool_input":{"command":"adb devices"}}` | exit 0, no output |
| `{"tool_input":{"command":"adb version"}}` | exit 0, no output |
| `{"tool_input":{"command":"./scripts/ui/adb shell foo"}}` | exit 0, no output |
| `{"tool_input":{"command":"adb shell foo"}}` | exit 0, deny JSON mentioning `scripts/ui/adb` |
| `{"tool_input":{"command":"adb -s emulator-5554 shell foo"}}` | exit 0, deny JSON |
| `{"tool_input":{"command":"./gradlew :app:installDebug"}}` | exit 0, deny JSON mentioning `install-app.sh` |
| `{"tool_input":{"command":"./gradlew :app:assembleDebug"}}` | exit 0, no output |
| `{"tool_input":{"command":"./scripts/install-app.sh"}}` | exit 0, no output |

- [ ] **Step 4: Wrapper regression tests**

In this worktree:

```bash
rm -f .emulator-serial
./scripts/ui/adb devices            # server-level: should work
./scripts/ui/adb shell echo hi      # pinned: should fail with remediation
echo "exit=$?"                       # should print a non-zero exit code
```

- [ ] **Step 5: Lint + tests (no app code changed; should be green)**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Expected: tests pass; no lint errors.

- [ ] **Step 6: Final commit if anything changed during verification**

If verification surfaces no fixes needed, skip. Otherwise commit the fix and re-run from Step 1.

---

## Self-Review

- **Spec coverage:** Every component in the spec maps to a task — lib (1), start (2), acquire (3), release (4), adb wrapper (5), install-app.sh (6), guard-adb.sh (7), settings wiring (8), UI script updates (9), deletions (10), AGENTS.md (11), other doc updates (12), verification (13).
- **No placeholders:** Every step shows the actual code or command to run, including exact expected output where useful.
- **Type/name consistency:** `repo_lock()`, `worktree_root()`, `adb_devices()`, `read_serial_file()`, `write_serial_file()`, `remove_serial_file()`, `git_worktrees()` — all named the same way in the lib and across consumers.
- **Order safety:** Lib first (Task 1) so consumers can import. UI script updates (Task 9) precede `.sh` deletions (Task 10) so we don't have a window where UI scripts reference a deleted file. Settings wiring (Task 8) goes in after the hook script exists (Task 7) so the hook is ready before it's registered.

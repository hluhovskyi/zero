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

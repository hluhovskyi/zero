#!/usr/bin/env bash
# Install the agent pre-push master guard into a worktree.
# Usage: install-pre-push-hook.sh <worktree-path>
set -euo pipefail

WORKTREE="${1:?usage: install-pre-push-hook.sh <worktree-path>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE="$SCRIPT_DIR/pre-push-master-guard"

[[ -d "$WORKTREE" ]] || { echo "worktree not found: $WORKTREE" >&2; exit 1; }

# Resolve the worktree's hooks dir via git (handles linked worktrees correctly).
HOOKS_DIR="$(cd "$WORKTREE" && git rev-parse --git-path hooks)"
mkdir -p "$HOOKS_DIR"

cp "$SOURCE" "$HOOKS_DIR/pre-push"
chmod +x "$HOOKS_DIR/pre-push"

echo "installed pre-push guard at $HOOKS_DIR/pre-push"

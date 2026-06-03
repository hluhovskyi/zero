#!/usr/bin/env bash
# Spawn a sandboxed `claude -p /agent-pr-verify <N>` session for one PR.
# Usage: spawn-verify-session.sh <pr-number> <worktree-path>
# Exits with the inner session's exit code so the watcher can distinguish
#   0  = fix confirmed
#   2  = bug still present (counts against cap)
#   75 = emulator unavailable (retry-later)
#   anything else = unexpected; treat as a real failure.
set -uo pipefail

PR="${1:?usage: spawn-verify-session.sh <pr-number> <worktree-path>}"
WORKTREE="${2:?usage: spawn-verify-session.sh <pr-number> <worktree-path>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.agent-state"
mkdir -p "$STATE_DIR"

VERDICT_FILE="$STATE_DIR/verify-$PR.verdict"
LOG_FILE="$STATE_DIR/verify-$PR.log"
: >"$VERDICT_FILE"
: >"$LOG_FILE"

if [[ ! -d "$WORKTREE" ]]; then
  echo "spawn-verify-session: worktree not found: $WORKTREE" >&2
  exit 1
fi

CLAUDE_BIN="${CLAUDE_BIN:-claude}"

(
  cd "$WORKTREE"
  "$CLAUDE_BIN" -p \
    --no-session-persistence \
    --permission-mode acceptEdits \
    --max-budget-usd 4 \
    --output-format text \
    --disallowedTools \
      "Bash(git push*)" \
      "Bash(git commit*)" \
      "Bash(gh pr ready*)" \
      "Bash(gh pr merge*)" \
      "Bash(gh pr edit*)" \
      "Bash(gh pr close*)" \
      "Bash(gh pr review*)" \
      "Bash(gh api*)" \
      "Bash(gh issue edit*)" \
      "Bash(rm -rf /*)" \
      "Bash(rm -rf ~*)" \
    -- \
    "/agent-pr-verify --pr $PR"
) >"$VERDICT_FILE" 2>"$LOG_FILE"
EXIT=$?

echo "spawn-verify-session: pr=$PR exit=$EXIT verdict=$VERDICT_FILE log=$LOG_FILE" >&2
exit "$EXIT"

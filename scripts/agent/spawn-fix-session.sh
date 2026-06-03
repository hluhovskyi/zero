#!/usr/bin/env bash
# Spawn a sandboxed `claude -p /agent-pr-fix <N>` session to repair CI on a PR.
# Usage: spawn-fix-session.sh <pr-number> <worktree-path> <ci-log-tail-path>
# Exits with the inner session's exit code:
#   0  = one fix commit pushed
#   non-zero = no fix produced; watcher counts this against the 3-attempt cap.
set -uo pipefail

PR="${1:?usage: spawn-fix-session.sh <pr-number> <worktree-path> <ci-log-tail-path>}"
WORKTREE="${2:?usage: spawn-fix-session.sh <pr-number> <worktree-path> <ci-log-tail-path>}"
CI_LOG="${3:?usage: spawn-fix-session.sh <pr-number> <worktree-path> <ci-log-tail-path>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.agent-state"
mkdir -p "$STATE_DIR"

OUT_FILE="$STATE_DIR/fix-$PR.out"
LOG_FILE="$STATE_DIR/fix-$PR.log"
: >"$OUT_FILE"
: >"$LOG_FILE"

if [[ ! -d "$WORKTREE" ]]; then
  echo "spawn-fix-session: worktree not found: $WORKTREE" >&2
  exit 1
fi
if [[ ! -f "$CI_LOG" ]]; then
  echo "spawn-fix-session: ci log not found: $CI_LOG" >&2
  exit 1
fi

CLAUDE_BIN="${CLAUDE_BIN:-claude}"

START_SHA="$(git -C "$WORKTREE" rev-parse HEAD 2>/dev/null || echo "")"

(
  cd "$WORKTREE"
  "$CLAUDE_BIN" -p \
    --no-session-persistence \
    --permission-mode acceptEdits \
    --max-budget-usd 4 \
    --output-format text \
    --disallowedTools \
      "Bash(git push --force*)" \
      "Bash(git push --force-with-lease*)" \
      "Bash(gh pr ready*)" \
      "Bash(gh pr merge*)" \
      "Bash(gh pr edit*)" \
      "Bash(gh pr close*)" \
      "Bash(gh pr review*)" \
      "Bash(gh api*)" \
      "Bash(gh repo delete*)" \
      "Bash(gh repo edit*)" \
      "Bash(gh release delete*)" \
      "Bash(rm -rf /*)" \
      "Bash(rm -rf ~*)" \
    -- \
    "/agent-pr-fix --pr $PR --ci-log $CI_LOG"
) >"$OUT_FILE" 2>"$LOG_FILE"
EXIT=$?

END_SHA="$(git -C "$WORKTREE" rev-parse HEAD 2>/dev/null || echo "")"
echo "spawn-fix-session: pr=$PR exit=$EXIT head=${START_SHA}->${END_SHA}" >&2

# If the inner session said "ok" but produced no new commit, treat as no-fix.
if [[ "$EXIT" -eq 0 && "$START_SHA" == "$END_SHA" ]]; then
  echo "spawn-fix-session: claimed success but HEAD did not advance — treating as failure" >&2
  exit 1
fi

exit "$EXIT"

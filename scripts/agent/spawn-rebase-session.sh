#!/usr/bin/env bash
# Spawn a sandboxed `claude -p /agent-pr-rebase <N>` session to resolve a
# `git merge origin/master` conflict on a PR branch.
# Usage: spawn-rebase-session.sh <pr-number> <worktree-path>
# Pre-condition: caller has already attempted `git merge` and left the worktree
# in a conflicted state (`git status` shows UU paths).
# Exits with the inner session's exit code:
#   0  = conflicts resolved, compiles, pushed
#   2  = conflicts too structural (e.g. target code was deleted); watcher → agent-stale
#   non-zero (other) = unexpected failure; watcher counts vs 3-attempt cap.
set -uo pipefail

PR="${1:?usage: spawn-rebase-session.sh <pr-number> <worktree-path>}"
WORKTREE="${2:?usage: spawn-rebase-session.sh <pr-number> <worktree-path>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.agent-state"
mkdir -p "$STATE_DIR"

OUT_FILE="$STATE_DIR/rebase-$PR.out"
LOG_FILE="$STATE_DIR/rebase-$PR.log"
: >"$OUT_FILE"
: >"$LOG_FILE"

if [[ ! -d "$WORKTREE" ]]; then
  echo "spawn-rebase-session: worktree not found: $WORKTREE" >&2
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
      "Bash(rm -rf /*)" \
      "Bash(rm -rf ~*)" \
    -- \
    "/agent-pr-rebase --pr $PR"
) >"$OUT_FILE" 2>"$LOG_FILE"
EXIT=$?

END_SHA="$(git -C "$WORKTREE" rev-parse HEAD 2>/dev/null || echo "")"
echo "spawn-rebase-session: pr=$PR exit=$EXIT head=${START_SHA}->${END_SHA}" >&2

if [[ "$EXIT" -eq 0 && "$START_SHA" == "$END_SHA" ]]; then
  echo "spawn-rebase-session: claimed success but HEAD did not advance — treating as failure" >&2
  exit 1
fi

exit "$EXIT"

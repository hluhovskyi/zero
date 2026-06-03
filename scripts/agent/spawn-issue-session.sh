#!/usr/bin/env bash
# Spawn a sandboxed Claude Code session to handle one issue.
# Usage: spawn-issue-session.sh <issue-number>
set -euo pipefail

N="${1:?usage: spawn-issue-session.sh <issue-number>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.agent-state"
mkdir -p "$STATE_DIR"
OUTCOME_FILE="$STATE_DIR/issue-$N.outcome"
LOG_FILE="$STATE_DIR/issue-$N.log"
RESULT_JSON="$STATE_DIR/issue-$N.result.json"
rm -f "$OUTCOME_FILE"

WORKTREE="$REPO_ROOT/.claude/worktrees/issue-$N"

# Clean any prior worktree at this path
if [[ -d "$WORKTREE" ]]; then
  git -C "$REPO_ROOT" worktree remove --force "$WORKTREE" || true
fi
git -C "$REPO_ROOT" branch -D "issue-$N" 2>/dev/null || true
git -C "$REPO_ROOT" worktree add "$WORKTREE" -b "issue-$N" origin/master

# Install the pre-push master guard
"$SCRIPT_DIR/install-pre-push-hook.sh" "$WORKTREE"

# Reset emulator app state (best-effort; ok to fail if no emulator)
if [[ -f "$REPO_ROOT/.emulator-serial" ]]; then
  "$REPO_ROOT/scripts/ui/adb.sh" shell pm clear com.zero.app || true
fi

# Spawn. Capture both the JSON result and a tail of stderr for diagnostics.
set +e
(
  cd "$WORKTREE"
  # NOTE: --disallowedTools is variadic and will swallow positional args that follow it.
  # Use `--` to terminate the variadic list before the prompt, otherwise the prompt is
  # parsed as another tool pattern and claude exits with "no prompt provided".
  claude -p \
    --no-session-persistence \
    --permission-mode acceptEdits \
    --max-budget-usd 8 \
    --output-format json \
    --disallowedTools "Bash(git push --force*)" "Bash(git push --force-with-lease*)" "Bash(gh pr merge*)" "Bash(gh pr ready*)" "Bash(gh pr review*)" "Bash(gh api*)" "Bash(gh repo delete*)" "Bash(gh repo edit*)" "Bash(gh release delete*)" "Bash(rm -rf /*)" "Bash(rm -rf ~*)" \
    -- \
    "/agent-do --issue $N"
) >"$RESULT_JSON" 2>"$LOG_FILE"
EXIT=$?
set -e

# Parse the JSON result to classify the outcome.
IS_ERROR="$(jq -r '.is_error // false' "$RESULT_JSON" 2>/dev/null || echo true)"
SUBTYPE="$(jq -r '.subtype // ""' "$RESULT_JSON" 2>/dev/null || echo "")"

# Did /agent-do open a PR?
PR_NUM="$(gh pr list --head "issue-$N" --json number --jq '.[0].number // empty' 2>/dev/null || echo "")"

if [[ "$EXIT" -ne 0 || "$IS_ERROR" == "true" ]]; then
  echo "error" > "$OUTCOME_FILE"
  TAIL_LOG="$(tail -n 50 "$LOG_FILE" 2>/dev/null || echo "(no log)")"
  gh issue comment "$N" --body "agent: crashed (exit=$EXIT subtype=$SUBTYPE). Last 50 lines of stderr:

\`\`\`
$TAIL_LOG
\`\`\`"
  exit 1
fi

if [[ -z "$PR_NUM" ]]; then
  echo "blocked" > "$OUTCOME_FILE"
  REASON="$(jq -r '.result // "no PR was opened"' "$RESULT_JSON")"
  gh issue comment "$N" --body "agent: blocked. $REASON"
  exit 0
fi

echo "completed" > "$OUTCOME_FILE"
gh issue comment "$N" --body "agent: draft PR opened → #$PR_NUM"

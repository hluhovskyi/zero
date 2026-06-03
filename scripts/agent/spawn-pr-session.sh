#!/usr/bin/env bash
# Unified spawn wrapper for the three per-PR sub-sessions:
#   repair-fix    — fix a failing CI run (needs ci-log)
#   repair-rebase — resolve a `git merge origin/master` conflict
#   verify        — observation-only emulator verification
#
# Usage:
#   spawn-pr-session.sh repair-fix    <pr> <worktree> <ci-log>
#   spawn-pr-session.sh repair-rebase <pr> <worktree>
#   spawn-pr-session.sh verify        <pr> <worktree>
#
# Exit codes (forwarded from the inner session):
#   0      — work done (commit pushed, or fix confirmed)
#   1      — generic failure; watcher counts vs 3-attempt cap
#   2      — rebase: conflict too structural; verify: bug still present
#   75     — emulator unavailable (verify only)
#   other  — unexpected; watcher logs agent-blocked
#
# Both repair kinds enforce "claude exit 0 must advance HEAD" so the model
# can't claim success without producing a commit.
set -uo pipefail

KIND="${1:?usage: spawn-pr-session.sh <repair-fix|repair-rebase|verify> <pr> <worktree> [extra]}"
PR="${2:?missing <pr>}"
WORKTREE="${3:?missing <worktree>}"
EXTRA="${4:-}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.agent-state"
mkdir -p "$STATE_DIR"

OUT_FILE="$STATE_DIR/$KIND-$PR.out"
LOG_FILE="$STATE_DIR/$KIND-$PR.log"
: >"$OUT_FILE"
: >"$LOG_FILE"

if [[ ! -d "$WORKTREE" ]]; then
  echo "spawn-pr-session: worktree not found: $WORKTREE" >&2
  exit 1
fi

# Universal denies — every sub-session is sandboxed against forging the merge
# gate (gh pr review / gh api / gh pr merge) or scribbling outside.
COMMON_DENIES=(
  "Bash(gh pr ready*)"
  "Bash(gh pr merge*)"
  "Bash(gh pr edit*)"
  "Bash(gh pr close*)"
  "Bash(gh pr review*)"
  "Bash(gh api*)"
  "Bash(gh repo delete*)"
  "Bash(gh repo edit*)"
  "Bash(rm -rf /*)"
  "Bash(rm -rf ~*)"
)

case "$KIND" in
  repair-fix)
    if [[ -z "$EXTRA" || ! -f "$EXTRA" ]]; then
      echo "spawn-pr-session: repair-fix requires <ci-log> path, got: '$EXTRA'" >&2
      exit 1
    fi
    PROMPT="/agent-pr-repair --kind fix --pr $PR --ci-log $EXTRA"
    REQUIRES_NEW_COMMIT=1
    KIND_DENIES=(
      "Bash(git push --force*)"
      "Bash(git push --force-with-lease*)"
      "Bash(gh release delete*)"
    )
    ;;
  repair-rebase)
    PROMPT="/agent-pr-repair --kind rebase --pr $PR"
    REQUIRES_NEW_COMMIT=1
    KIND_DENIES=(
      "Bash(git push --force*)"
      "Bash(git push --force-with-lease*)"
    )
    ;;
  verify)
    PROMPT="/agent-pr-verify --pr $PR"
    REQUIRES_NEW_COMMIT=0
    # Verify is observation-only — block ALL push + commit so it can't mutate
    # the branch even by accident, plus issue edits.
    KIND_DENIES=(
      "Bash(git push*)"
      "Bash(git commit*)"
      "Bash(gh issue edit*)"
    )
    ;;
  *)
    echo "spawn-pr-session: unknown kind '$KIND'" >&2
    exit 1
    ;;
esac

CLAUDE_BIN="${CLAUDE_BIN:-claude}"
START_SHA="$(git -C "$WORKTREE" rev-parse HEAD 2>/dev/null || echo "")"

(
  cd "$WORKTREE"
  "$CLAUDE_BIN" -p \
    --no-session-persistence \
    --permission-mode acceptEdits \
    --max-budget-usd 4 \
    --output-format text \
    --disallowedTools "${COMMON_DENIES[@]}" "${KIND_DENIES[@]}" \
    -- \
    "$PROMPT"
) >"$OUT_FILE" 2>"$LOG_FILE"
EXIT=$?

END_SHA="$(git -C "$WORKTREE" rev-parse HEAD 2>/dev/null || echo "")"
echo "spawn-pr-session: kind=$KIND pr=$PR exit=$EXIT head=${START_SHA}->${END_SHA}" >&2

# Detect "claimed success without producing a commit" — fix/rebase only.
if [[ "$REQUIRES_NEW_COMMIT" -eq 1 && "$EXIT" -eq 0 && "$START_SHA" == "$END_SHA" ]]; then
  echo "spawn-pr-session: claimed success but HEAD did not advance — treating as failure" >&2
  exit 1
fi

exit "$EXIT"

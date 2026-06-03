#!/usr/bin/env bash
# Poll GitHub for eligible agent-approved issues. Pick the oldest, run it.
# Exits 0 whether or not an issue was processed; non-zero only on internal error.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/poll-helpers.sh"

ME="hluhovskyi"
APPROVED="agent-approved"
IN_PROGRESS="agent-in-progress"
BLOCKED="agent-blocked"
ERROR="agent-error"
COMPLETED="agent-completed"

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"

# List approved issues, oldest first.
NUMS=()
while IFS= read -r n; do
  [[ -n "$n" ]] && NUMS+=("$n")
done < <(gh issue list \
  --label "$APPROVED" \
  --author "$ME" \
  --state open \
  --json number \
  --jq '.[].number' \
  | sort -n)

if [[ ${#NUMS[@]} -eq 0 ]]; then
  echo "no eligible issues"
  exit 0
fi

for N in "${NUMS[@]}"; do
  echo "considering issue #$N"

  # Defense in depth: re-verify author via API
  if ! gh issue view "$N" --json author | issue_author_is_me "$ME"; then
    echo "  skip: author mismatch"
    continue
  fi

  # Verify last labeling of agent-approved was by me
  if ! gh api "repos/$REPO/issues/$N/events" --paginate | last_labeler_is_me "$ME" "$APPROVED"; then
    echo "  skip: $APPROVED was not added by $ME"
    gh issue edit "$N" --remove-label "$APPROVED" >/dev/null
    gh issue comment "$N" --body "agent: $APPROVED was not added by $ME. Removed label and skipped." >/dev/null
    continue
  fi

  # Acquire the lock by swapping labels.
  gh issue edit "$N" --remove-label "$APPROVED" --add-label "$IN_PROGRESS" >/dev/null
  echo "  lock acquired (agent-in-progress)"

  # Spawn the per-issue session and capture outcome.
  if "$SCRIPT_DIR/spawn-issue-session.sh" "$N"; then
    OUTCOME="success"
  else
    OUTCOME="failure"
  fi
  echo "  spawn result: $OUTCOME"

  # Determine final state. spawn-issue-session.sh writes a state file at .agent-state/issue-<N>.outcome
  STATE_FILE="$SCRIPT_DIR/../../.agent-state/issue-$N.outcome"
  if [[ -f "$STATE_FILE" ]]; then
    FINAL="$(cat "$STATE_FILE")"
  elif [[ "$OUTCOME" == "success" ]]; then
    FINAL="completed"
  else
    FINAL="error"
  fi

  case "$FINAL" in
    completed) gh issue edit "$N" --remove-label "$IN_PROGRESS" --add-label "$COMPLETED" >/dev/null ;;
    blocked)   gh issue edit "$N" --remove-label "$IN_PROGRESS" --add-label "$BLOCKED"   >/dev/null ;;
    error|*)   gh issue edit "$N" --remove-label "$IN_PROGRESS" --add-label "$ERROR"     >/dev/null ;;
  esac
  echo "  final label: agent-$FINAL"

  # One issue per tick.
  break
done

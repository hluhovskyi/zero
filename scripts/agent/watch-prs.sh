#!/usr/bin/env bash
# Watcher loop for agent-opened draft PRs that you (hluhovskyi) have approved.
# Strict single-gate model: the watcher is INVISIBLE to unapproved PRs.
# One PR per tick. Exits 0 even when nothing happened; non-zero only on internal error.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$REPO_ROOT/.agent-state"
mkdir -p "$STATE_DIR"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/pr-classify.sh"

ME="${AGENT_WATCH_USER:-hluhovskyi}"
REPO="${AGENT_WATCH_REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
MAX_ATTEMPTS="${AGENT_WATCH_MAX_ATTEMPTS:-3}"
MAX_ACQUIRE_MISSES="${AGENT_WATCH_MAX_ACQUIRE_MISSES:-10}"

# Pre-flight: ensure the worktree for a PR exists; recreate if needed.
ensure_worktree() {
  local pr_branch="$1"
  local worktree="$REPO_ROOT/.claude/worktrees/$pr_branch"
  if [[ -d "$worktree/.git" || -f "$worktree/.git" ]]; then
    echo "$worktree"
    return 0
  fi
  git -C "$REPO_ROOT" worktree add "$worktree" "$pr_branch" >/dev/null 2>&1 || return 1
  "$SCRIPT_DIR/install-pre-push-hook.sh" "$worktree" >/dev/null 2>&1 || true
  echo "$worktree"
}

# Verify that the LAST event setting the agent-merge label was the expected
# user. Mirrors `last_labeler_is_me` in poll-helpers.sh. Returns 0 if so.
# Empty events / network failure → return 1 (fail-closed; never trust a label
# we can't verify).
verify_agent_merge_actor() {
  local pr="$1" expected="$2"
  local actor
  actor="$(gh api "repos/$REPO/issues/$pr/events" --paginate 2>/dev/null \
    | jq -r '[.[] | select(.event == "labeled" and .label.name == "agent-merge")] | last | .actor.login // empty' 2>/dev/null)"
  [[ -n "$actor" && "$actor" == "$expected" ]]
}

# Strip the agent-merge label after the watcher pushes new commits. Forces the
# human to re-apply approval after seeing the new HEAD. (Defense paired with
# the SHA-bound review check in pr_has_approval.)
revoke_label_after_push() {
  local pr="$1"
  gh pr edit "$pr" -R "$REPO" --remove-label agent-merge >/dev/null 2>&1 || true
  gh pr comment "$pr" -R "$REPO" --body "agent-pr-watch: pushed new commits — \`agent-merge\` label cleared. Re-apply (or re-review) once you've seen the new HEAD." >/dev/null 2>&1 || true
}

# Append "<epoch> <outcome>" to .agent-state/pr-<N>.attempts.
record_attempt() {
  local pr="$1" outcome="$2"
  echo "$(date +%s) $outcome" >>"$STATE_DIR/pr-$pr.attempts"
}

# Count consecutive failures at the end of the attempts file. Resets on any "success".
recent_failures() {
  local pr="$1"
  local file="$STATE_DIR/pr-$pr.attempts"
  [[ -f "$file" ]] || { echo 0; return; }
  tac "$file" 2>/dev/null | awk '
    {
      if ($2 == "success") { print count; exit }
      count++
    }
    END { print count }
  '
}

# Mark PR as blocked with a comment and stop processing it.
block_pr() {
  local pr="$1" reason="$2"
  gh pr edit "$pr" -R "$REPO" --add-label agent-blocked >/dev/null 2>&1 || true
  gh pr comment "$pr" -R "$REPO" --body "agent-pr-watch: blocked. $reason" >/dev/null 2>&1 || true
  echo "  → agent-blocked: $reason"
}

# Same shape for agent-stale.
stale_pr() {
  local pr="$1"
  gh pr edit "$pr" -R "$REPO" --add-label agent-stale >/dev/null 2>&1 || true
  gh pr comment "$pr" -R "$REPO" --body "agent-pr-watch: this branch is too old to safely rebase against current master. Please close the PR and re-spawn from the issue." >/dev/null 2>&1 || true
  echo "  → agent-stale"
}

# Track acquire-misses so we only spam one comment per PR.
record_acquire_miss() {
  local pr="$1"
  local file="$STATE_DIR/pr-$pr.acquire-misses"
  local n=0
  [[ -f "$file" ]] && n="$(cat "$file")"
  n=$((n + 1))
  echo "$n" >"$file"
  if [[ "$n" -eq "$MAX_ACQUIRE_MISSES" ]]; then
    gh pr comment "$pr" -R "$REPO" --body "agent-pr-watch: waiting for emulator slot (have missed acquire $MAX_ACQUIRE_MISSES times). Will keep trying silently." >/dev/null 2>&1 || true
  fi
}

# Reset acquire-miss counter when work proceeds.
reset_acquire_miss() {
  rm -f "$STATE_DIR/pr-$1.acquire-misses"
}

# Cleanup hygiene: nuke `.gradle-home` before `worktree remove` to dodge
# "Directory not empty" failures.
cleanup_pr_worktree() {
  local pr_branch="$1"
  local worktree="$REPO_ROOT/.claude/worktrees/$pr_branch"
  rm -rf "$worktree/.gradle-home" 2>/dev/null || true
  git -C "$REPO_ROOT" worktree remove "$worktree" --force 2>/dev/null || true
  git -C "$REPO_ROOT" worktree prune 2>/dev/null || true
  git -C "$REPO_ROOT" branch -D "$pr_branch" 2>/dev/null || true
}

# Dispatch on classified state. Returns the human-readable outcome on stdout.
dispatch_pr() {
  local pr="$1" pr_branch="$2" state="$3" worktree="$4" pr_json_file="$5"

  case "$state" in
    behind-clean)
      ( cd "$worktree" && git fetch origin master --quiet && git merge origin/master --no-edit --quiet && git push --quiet ) \
        && { reset_acquire_miss "$pr"; record_attempt "$pr" success; revoke_label_after_push "$pr"; echo "rebased (clean)"; return 0; } \
        || { record_attempt "$pr" failure; echo "rebase-clean failed"; return 1; }
      ;;

    behind-dirty)
      # Pre-stage the conflicted merge so the rebase session has something to fix.
      ( cd "$worktree" && git fetch origin master --quiet && git merge origin/master --no-edit ) >/dev/null 2>&1
      "$SCRIPT_DIR/spawn-rebase-session.sh" "$pr" "$worktree" >/dev/null 2>&1
      local rc=$?
      if [[ "$rc" -eq 0 ]]; then
        reset_acquire_miss "$pr"; record_attempt "$pr" success; revoke_label_after_push "$pr"
        echo "rebased (LLM)"; return 0
      elif [[ "$rc" -eq 2 ]]; then
        stale_pr "$pr"
        echo "stale (rebase too structural)"; return 1
      else
        record_attempt "$pr" failure
        local fails; fails="$(recent_failures "$pr")"
        if [[ "$fails" -ge "$MAX_ATTEMPTS" ]]; then
          block_pr "$pr" "rebase-session failed $fails times in a row"
        fi
        echo "rebase-session failed (rc=$rc, fails=$fails)"; return 1
      fi
      ;;

    ci-failing)
      # Fetch the failing job log tail.
      local ci_log="$STATE_DIR/ci-tail-$pr.log"
      local run_id
      run_id="$(jq -r '
        [.statusCheckRollup[]? | select(.status == "COMPLETED" and .conclusion == "FAILURE")][0].detailsUrl
        | capture("/runs/(?<id>[0-9]+)")?.id // empty
      ' "$pr_json_file")"
      if [[ -n "$run_id" ]]; then
        gh run view "$run_id" -R "$REPO" --log-failed 2>/dev/null | tail -200 >"$ci_log" || echo "(no log)" >"$ci_log"
      else
        echo "(no run id available)" >"$ci_log"
      fi

      "$SCRIPT_DIR/spawn-fix-session.sh" "$pr" "$worktree" "$ci_log" >/dev/null 2>&1
      local rc=$?
      if [[ "$rc" -eq 0 ]]; then
        reset_acquire_miss "$pr"; record_attempt "$pr" success; revoke_label_after_push "$pr"
        echo "ci-fixed"; return 0
      else
        record_attempt "$pr" failure
        local fails; fails="$(recent_failures "$pr")"
        if [[ "$fails" -ge "$MAX_ATTEMPTS" ]]; then
          block_pr "$pr" "fix-session failed $fails times in a row"
        fi
        echo "ci-fix failed (rc=$rc, fails=$fails)"; return 1
      fi
      ;;

    needs-verify)
      "$SCRIPT_DIR/spawn-verify-session.sh" "$pr" "$worktree" >/dev/null 2>&1
      local rc=$?
      case "$rc" in
        0)
          reset_acquire_miss "$pr"
          local head_sha; head_sha="$(jq -r '.headRefOid' "$pr_json_file")"
          printf '%s %s\n' "$head_sha" "$(date +%s)" >"$STATE_DIR/pr-$pr.verified"
          # Post the verdict + screenshot as a PR comment.
          local verdict="$STATE_DIR/verify-$pr.verdict"
          if [[ -s "$verdict" ]]; then
            gh pr comment "$pr" -R "$REPO" --body-file "$verdict" >/dev/null 2>&1 || true
          fi
          record_attempt "$pr" success
          echo "verified"; return 0
          ;;
        75)
          record_acquire_miss "$pr"
          echo "emu-busy (retry next tick)"; return 0
          ;;
        2)
          record_attempt "$pr" failure
          local fails; fails="$(recent_failures "$pr")"
          if [[ "$fails" -ge "$MAX_ATTEMPTS" ]]; then
            block_pr "$pr" "verify said bug still present $fails times in a row"
          fi
          echo "verify failed: bug still present (fails=$fails)"; return 1
          ;;
        *)
          record_attempt "$pr" failure
          echo "verify crashed (rc=$rc)"; return 1
          ;;
      esac
      ;;

    ready-to-merge)
      gh pr ready "$pr" -R "$REPO" >/dev/null 2>&1 || true
      gh pr merge "$pr" -R "$REPO" --squash --auto >/dev/null 2>&1 || true
      # Cleanup is deferred — branch may not be merged yet (CI may still be running).
      # The next tick will see the PR as MERGED and call cleanup_pr_worktree() from the listing filter.
      echo "ready+auto-merge enabled"
      return 0
      ;;

    stale)
      stale_pr "$pr"
      return 1
      ;;

    unknown)
      echo "unknown state (CI in progress or unexpected combination)"
      return 0
      ;;

    *)
      echo "no dispatch for state '$state'"
      return 1
      ;;
  esac
}

main() {
  # List candidate draft PRs. We over-fetch fields so the classifier has everything.
  local list_json
  list_json="$(gh pr list \
    -R "$REPO" \
    --state open \
    --draft \
    --search "head:issue- author:@me" \
    --json number,title,headRefName,headRefOid,createdAt,mergeStateStatus,statusCheckRollup,labels,reviews,files \
    --limit 50)"

  # Cleanup pass: any merged PRs we left worktrees for. (Worktree path lives in `headRefName`.)
  while IFS= read -r merged_branch; do
    [[ -n "$merged_branch" ]] && cleanup_pr_worktree "$merged_branch"
  done < <(gh pr list -R "$REPO" --state merged --search "head:issue- author:@me" --json headRefName,mergedAt --limit 20 \
            | jq -r --argjson cutoff "$(date -v-1d +%s 2>/dev/null || date -d '1 day ago' +%s)" \
                '.[] | select((.mergedAt | fromdateiso8601) > $cutoff) | .headRefName' 2>/dev/null)

  # Filter to approved PRs only (single-gate). Defer to pr_has_approval (sourced
  # from pr-classify.sh) so the gate logic lives in exactly one place —
  # otherwise drift between this jq and pr_has_approval is a security regression
  # waiting to happen (e.g. the "latest review wins" rule needs to be applied
  # identically here and in the per-PR classifier).
  local approved_json sorted_json
  sorted_json="$(echo "$list_json" | jq 'sort_by(.createdAt)')"
  approved_json="$(echo "$sorted_json" | jq -c '.[]' | while IFS= read -r pr_obj; do
    if echo "$pr_obj" | pr_has_approval "$ME"; then
      echo "$pr_obj"
    fi
  done | jq -s '.')"

  local count
  count="$(echo "$approved_json" | jq 'length')"
  if [[ "$count" -eq 0 ]]; then
    echo "no approved PRs to handle"
    return 0
  fi

  # Pick the oldest. Persist its JSON to a file so dispatch can re-read fields.
  local pr_json_file="$STATE_DIR/current-pr.json"
  echo "$approved_json" | jq '.[0]' >"$pr_json_file"
  local pr pr_branch
  pr="$(jq -r '.number' "$pr_json_file")"
  pr_branch="$(jq -r '.headRefName' "$pr_json_file")"
  echo "considering PR #$pr ($pr_branch)"

  # Defense-in-depth on the label path: an APPROVED review's author is part of
  # the JSON, but a label's setter is not. Verify the actor via the events API
  # before treating an agent-merge label as a gate signal.
  local has_label has_review_at_head
  has_label="$(jq -r '[.labels[]?.name] | any(. == "agent-merge")' "$pr_json_file")"
  has_review_at_head="$(jq -r --arg me "$ME" '
    .headRefOid as $head
    | [.reviews[]? | select(.state == "APPROVED" and .author.login == $me and .commit_id == $head)] | length > 0
  ' "$pr_json_file")"
  if [[ "$has_label" == "true" && "$has_review_at_head" != "true" ]]; then
    if ! verify_agent_merge_actor "$pr" "$ME"; then
      echo "  agent-merge was not applied by $ME — removing label and skipping"
      gh pr edit "$pr" -R "$REPO" --remove-label agent-merge >/dev/null 2>&1 || true
      gh pr comment "$pr" -R "$REPO" --body "agent-pr-watch: \`agent-merge\` was not applied by \`$ME\`. Label removed; PR will not be processed." >/dev/null 2>&1 || true
      return 0
    fi
  fi

  local worktree
  worktree="$(ensure_worktree "$pr_branch")"
  if [[ -z "$worktree" ]]; then
    echo "  could not create worktree for $pr_branch"
    return 1
  fi

  local state
  state="$(classify_pr_state "$STATE_DIR" <"$pr_json_file")"
  echo "  state: $state"
  dispatch_pr "$pr" "$pr_branch" "$state" "$worktree" "$pr_json_file"
}

main "$@"

#!/usr/bin/env bash
# Polls a PR until CI passes and the PR is merged.
# Usage: wait-for-ci.sh <pr_number> [repo]
# Exit 0 if merged, exit 1 if CI fails, PR is closed, or the merge is blocked
# by an actionable mergeStateStatus (BEHIND, DIRTY, BLOCKED without auto-merge,
# UNSTABLE).
set -euo pipefail

PR="${1:?Usage: wait-for-ci.sh <pr_number> [repo]}"
REPO_FLAG="${2:+--repo $2}"

# Phase 1: wait for CI checks to resolve
while true; do
  output=$(gh pr checks "$PR" $REPO_FLAG 2>&1 || true)

  if echo "$output" | grep -q "^.*	pending"; then
    echo "CI pending — checking again in 15s..."
    sleep 15
    continue
  fi

  if echo "$output" | grep -qE "^.*	(fail|error)"; then
    echo "$output"
    echo ""
    echo "CI FAILED on PR #$PR"
    exit 1
  fi

  if echo "$output" | grep -q "no checks reported"; then
    state=$(gh pr view "$PR" $REPO_FLAG --json state --jq '.state')
    if [ "$state" = "MERGED" ]; then echo "PR #$PR merged (no CI checks)!"; exit 0; fi
    if [ "$state" = "CLOSED" ]; then echo "PR #$PR was closed without merging."; exit 1; fi
    echo "No checks yet — checking again in 15s..."
    sleep 15
    continue
  fi

  echo "$output"
  echo ""
  echo "All CI checks passed on PR #$PR — waiting for merge..."
  break
done

# Phase 2: wait for the PR to be merged (handles --auto and manual merges).
# Also detect non-mergeable states so the caller isn't stuck in a silent poll
# loop. Auto-merge will not fire while mergeStateStatus is BEHIND or DIRTY;
# polling forever is the wrong default.
while true; do
  pr_json=$(gh pr view "$PR" $REPO_FLAG --json state,mergeStateStatus,mergeable,autoMergeRequest)
  state=$(echo "$pr_json" | python3 -c 'import sys,json;print(json.load(sys.stdin)["state"])')
  merge_state=$(echo "$pr_json" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("mergeStateStatus") or "")')
  auto_merge=$(echo "$pr_json" | python3 -c 'import sys,json;print("yes" if json.load(sys.stdin).get("autoMergeRequest") else "no")')

  if [ "$state" = "MERGED" ]; then
    echo "PR #$PR merged!"
    exit 0
  fi
  if [ "$state" = "CLOSED" ]; then
    echo "PR #$PR was closed without merging."
    exit 1
  fi

  case "$merge_state" in
    BEHIND)
      echo "PR #$PR is BEHIND base branch — branch protection requires it to be up to date."
      echo "Update the branch (merge base into PR branch and push), then re-run."
      exit 1
      ;;
    DIRTY)
      echo "PR #$PR has merge conflicts (DIRTY) — resolve and push, then re-run."
      exit 1
      ;;
    BLOCKED)
      if [ "$auto_merge" = "yes" ]; then
        echo "PR state: OPEN (BLOCKED, auto-merge armed) — checking again in 15s..."
      else
        echo "PR #$PR is BLOCKED — required reviews or status checks not satisfied, and auto-merge is not enabled."
        exit 1
      fi
      ;;
    UNSTABLE)
      echo "PR #$PR is UNSTABLE — a non-required check failed. Merge is still possible but worth checking before proceeding."
      exit 1
      ;;
    *)
      echo "PR state: $state (mergeStateStatus=$merge_state) — checking again in 15s..."
      ;;
  esac

  sleep 15
done

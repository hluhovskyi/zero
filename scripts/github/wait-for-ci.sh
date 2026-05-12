#!/usr/bin/env bash
# Polls a PR until CI passes and the PR is merged.
# Usage: wait-for-ci.sh <pr_number> [repo]
# Exit 0 if merged, exit 1 if CI fails or PR is closed without merging.
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
    echo "No checks yet — checking again in 15s..."
    sleep 15
    continue
  fi

  echo "$output"
  echo ""
  echo "All CI checks passed on PR #$PR — waiting for merge..."
  break
done

# Phase 2: wait for the PR to be merged (handles --auto and manual merges)
while true; do
  state=$(gh pr view "$PR" $REPO_FLAG --json state --jq '.state')
  if [ "$state" = "MERGED" ]; then
    echo "PR #$PR merged!"
    exit 0
  fi
  if [ "$state" = "CLOSED" ]; then
    echo "PR #$PR was closed without merging."
    exit 1
  fi
  echo "PR state: $state — checking again in 15s..."
  sleep 15
done

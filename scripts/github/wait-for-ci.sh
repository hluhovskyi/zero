#!/usr/bin/env bash
# Polls GitHub Actions checks on a PR until they all resolve.
# Usage: wait-for-ci.sh <pr_number> [repo]
# Exit 0 if all pass, exit 1 if any fail.
set -euo pipefail

PR="${1:?Usage: wait-for-ci.sh <pr_number> [repo]}"
REPO_FLAG="${2:+--repo $2}"

while true; do
  output=$(gh pr checks "$PR" $REPO_FLAG 2>&1 || true)

  if echo "$output" | grep -q "^.*	pending"; then
    echo "CI pending — checking again in 15s..."
    sleep 15
    continue
  fi

  echo "$output"

  if echo "$output" | grep -qE "^.*	(fail|error)"; then
    echo ""
    echo "CI FAILED on PR #$PR"
    exit 1
  fi

  echo ""
  echo "All CI checks passed on PR #$PR"
  exit 0
done

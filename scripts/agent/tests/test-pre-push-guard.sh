#!/usr/bin/env bash
# Test that pre-push-master-guard blocks pushes to master and main, allows other refs.
set -euo pipefail

HOOK="$(cd "$(dirname "$0")/.." && pwd)/pre-push-master-guard"

[[ -x "$HOOK" ]] || { echo "FAIL: $HOOK not executable"; exit 1; }

run_hook() {
  # pre-push hooks get refs on stdin: "<local-ref> <local-sha> <remote-ref> <remote-sha>"
  local remote_ref="$1"
  printf "refs/heads/feature dummy %s dummy\n" "$remote_ref" | "$HOOK" origin git@example.com:foo.git
}

if run_hook "refs/heads/master" 2>/dev/null; then
  echo "FAIL: master push was allowed"
  exit 1
fi
echo "PASS: master push blocked"

if run_hook "refs/heads/main" 2>/dev/null; then
  echo "FAIL: main push was allowed"
  exit 1
fi
echo "PASS: main push blocked"

if ! run_hook "refs/heads/feature-branch"; then
  echo "FAIL: feature-branch push was blocked"
  exit 1
fi
echo "PASS: feature-branch push allowed"

echo "All pre-push guard tests passed."

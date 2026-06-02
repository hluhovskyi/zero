#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURES="$(cd "$(dirname "$0")/fixtures" && pwd)"
# shellcheck disable=SC1091
source "$DIR/poll-helpers.sh"

# Test 1: self-labeled JSON → returns 0
if ! last_labeler_is_me hluhovskyi agent-approved < "$FIXTURES/events-self-labeled.json"; then
  echo "FAIL: self-labeled should pass"
  exit 1
fi
echo "PASS: self-labeled accepted"

# Test 2: stranger-labeled JSON → returns non-zero
if last_labeler_is_me hluhovskyi agent-approved < "$FIXTURES/events-stranger-labeled.json"; then
  echo "FAIL: stranger-labeled should be rejected"
  exit 1
fi
echo "PASS: stranger-labeled rejected"

# Test 3: issue_author_is_me — own
if ! echo '{"author": {"login": "hluhovskyi"}}' | issue_author_is_me hluhovskyi; then
  echo "FAIL: own issue rejected"
  exit 1
fi
echo "PASS: own author accepted"

# Test 4: issue_author_is_me — stranger
if echo '{"author": {"login": "stranger42"}}' | issue_author_is_me hluhovskyi; then
  echo "FAIL: stranger author accepted"
  exit 1
fi
echo "PASS: stranger author rejected"

echo "All poll-helpers tests passed."

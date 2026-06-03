#!/usr/bin/env bash
# Unit tests for scripts/agent/pr-classify.sh
# Pure-function tests; no network, no git, no gh calls.
# NOTE: NOT using `set -e` because we assert on rc from functions that legitimately
# return non-zero.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FIXTURES="$SCRIPT_DIR/fixtures"
# shellcheck source=../pr-classify.sh
source "$SCRIPT_DIR/../pr-classify.sh"

TMP_STATE="$(mktemp -d)"
trap 'rm -rf "$TMP_STATE"' EXIT

PASS=0
FAIL=0

assert() {
  local name="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    PASS=$((PASS + 1))
    echo "  ✓ $name"
  else
    FAIL=$((FAIL + 1))
    echo "  ✗ $name — expected '$expected', got '$actual'"
  fi
}

assert_rc() {
  local name="$1" expected_rc="$2" actual_rc="$3"
  if [[ "$expected_rc" == "$actual_rc" ]]; then
    PASS=$((PASS + 1))
    echo "  ✓ $name"
  else
    FAIL=$((FAIL + 1))
    echo "  ✗ $name — expected rc=$expected_rc, got rc=$actual_rc"
  fi
}

echo "=== pr_has_approval ==="
pr_has_approval hluhovskyi <"$FIXTURES/pr-no-approval.json"        ; assert_rc "no-approval rejected" 1 $?
pr_has_approval hluhovskyi <"$FIXTURES/pr-behind-clean.json"       ; assert_rc "agent-merge label accepted" 0 $?
pr_has_approval hluhovskyi <"$FIXTURES/pr-behind-dirty.json"       ; assert_rc "APPROVED review at HEAD accepted" 0 $?
pr_has_approval hluhovskyi <"$FIXTURES/pr-stale-review.json"       ; assert_rc "APPROVED review at stale SHA rejected" 1 $?
pr_has_approval hluhovskyi <"$FIXTURES/pr-withdrawn-approval.json" ; assert_rc "REQUEST_CHANGES after APPROVED retracts gate" 1 $?

echo "=== pr_is_doc_only ==="
pr_is_doc_only <"$FIXTURES/pr-no-approval.json"     ; assert_rc "code-only rejected" 1 $?
pr_is_doc_only <"$FIXTURES/pr-ready-to-merge.json"  ; assert_rc "docs/*.md accepted" 0 $?
pr_is_doc_only <"$FIXTURES/pr-root-claudemd.json"   ; assert_rc "root CLAUDE.md is NOT doc-only" 1 $?

echo "=== pr_was_verified_at_head ==="
pr_was_verified_at_head "$TMP_STATE" <"$FIXTURES/pr-needs-verify.json"; assert_rc "no state file → not verified" 1 $?
echo "needverify0000000000000000000000000000000 0" >"$TMP_STATE/pr-104.verified"
pr_was_verified_at_head "$TMP_STATE" <"$FIXTURES/pr-needs-verify.json"; assert_rc "matching SHA → verified" 0 $?
echo "wrongsha 0" >"$TMP_STATE/pr-104.verified"
pr_was_verified_at_head "$TMP_STATE" <"$FIXTURES/pr-needs-verify.json"; assert_rc "stale SHA → not verified" 1 $?

echo "=== classify_pr_state ==="
state="$(classify_pr_state "$TMP_STATE" <"$FIXTURES/pr-behind-clean.json")"
assert "behind-clean" "behind-clean" "$state"
state="$(classify_pr_state "$TMP_STATE" <"$FIXTURES/pr-behind-dirty.json")"
assert "behind-dirty" "behind-dirty" "$state"
state="$(classify_pr_state "$TMP_STATE" <"$FIXTURES/pr-ci-failing.json")"
assert "ci-failing" "ci-failing" "$state"
state="$(classify_pr_state "$TMP_STATE" <"$FIXTURES/pr-ready-to-merge.json")"
assert "doc-only → ready-to-merge" "ready-to-merge" "$state"
# fresh state dir, so 104 is not verified at HEAD
rm -f "$TMP_STATE/pr-104.verified"
state="$(classify_pr_state "$TMP_STATE" <"$FIXTURES/pr-needs-verify.json")"
assert "needs-verify when no state file" "needs-verify" "$state"
echo "needverify0000000000000000000000000000000 0" >"$TMP_STATE/pr-104.verified"
state="$(classify_pr_state "$TMP_STATE" <"$FIXTURES/pr-needs-verify.json")"
assert "ready-to-merge when verified at HEAD" "ready-to-merge" "$state"

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]

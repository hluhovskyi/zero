#!/usr/bin/env bash
# Integration tests for scripts/agent/watch-prs.sh — focused on the things the
# pure-function classifier (test-pr-classify.sh) can't cover: the approval
# filter, the actor verification fork, and the post-push label revoke. Every
# state-machine arm is already tested via the classifier; we only verify the
# watcher actually filters, dispatches, and writes the gate-revoking side
# effects.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$SCRIPT_DIR/../watch-prs.sh"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASS=0
FAIL=0
assert_contains() {
  local name="$1" needle="$2" haystack="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    PASS=$((PASS + 1)); echo "  ✓ $name"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $name — did not find '$needle'"
    printf '%s\n' "$haystack" | sed 's/^/    /'
  fi
}

MOCK_BIN="$TMP/bin"
mkdir -p "$MOCK_BIN"
export GH_LOG="$TMP/gh.log"
export GIT_LOG="$TMP/git.log"

DEFAULT_EVENTS="$TMP/events-me.json"
echo '[{"event":"labeled","label":{"name":"agent-merge"},"actor":{"login":"hluhovskyi"}}]' >"$DEFAULT_EVENTS"
export GH_EVENTS_FIXTURE="$DEFAULT_EVENTS"

cat >"$MOCK_BIN/gh" <<'GH'
#!/usr/bin/env bash
echo "$@" >>"$GH_LOG"
case "$1 $2" in
  "pr list")
    if [[ "$*" == *"--state merged"* ]]; then echo "[]"
    else cat "${GH_LIST_FIXTURE:-/dev/null}"
    fi ;;
  "repo view") echo "hluhovskyi/zero" ;;
  "api "*)
    if [[ "$*" == *"/events"* ]]; then cat "${GH_EVENTS_FIXTURE:-/dev/null}"
    else echo "[]"
    fi ;;
  *) exit 0 ;;
esac
GH
cat >"$MOCK_BIN/git" <<'GIT'
#!/usr/bin/env bash
echo "$@" >>"$GIT_LOG"
case "$1" in
  worktree) case "$2" in add|remove|prune) exit 0 ;; esac ;;
  branch|fetch|push|merge) exit 0 ;;
esac
/usr/bin/env -i HOME="$HOME" PATH=/usr/bin:/bin /usr/bin/git "$@" 2>/dev/null || true
GIT
# Stub spawn wrapper — exits 0 by default; tests can override via SPAWN_EXIT.
cat >"$MOCK_BIN/spawn-pr-session.sh" <<'SP'
#!/usr/bin/env bash
echo "$@" >>"${SPAWN_LOG:-/dev/null}"
exit "${SPAWN_EXIT:-0}"
SP
cat >"$MOCK_BIN/install-pre-push-hook.sh" <<'IPP'
#!/usr/bin/env bash
exit 0
IPP
chmod +x "$MOCK_BIN"/*

FAKE_REPO="$TMP/repo"
mkdir -p "$FAKE_REPO/.claude/worktrees" "$FAKE_REPO/scripts/agent"
git init -q "$FAKE_REPO"
cp "$SCRIPT" "$FAKE_REPO/scripts/agent/watch-prs.sh"
cp "$REPO_ROOT/scripts/agent/pr-classify.sh" "$FAKE_REPO/scripts/agent/pr-classify.sh"
for s in spawn-pr-session.sh install-pre-push-hook.sh; do
  ln -sf "$MOCK_BIN/$s" "$FAKE_REPO/scripts/agent/$s"
done
chmod +x "$FAKE_REPO/scripts/agent/"*.sh

run_watcher() {
  PATH="$MOCK_BIN:$PATH" \
    GH_LIST_FIXTURE="$1" \
    GH_EVENTS_FIXTURE="${GH_EVENTS_FIXTURE:-}" \
    AGENT_WATCH_USER="hluhovskyi" \
    AGENT_WATCH_REPO="hluhovskyi/zero" \
    bash "$FAKE_REPO/scripts/agent/watch-prs.sh" 2>&1
}

# 1. Empty list → no-op.
echo "[]" >"$TMP/list-empty.json"
assert_contains "empty list → no-op" "no approved PRs" "$(run_watcher "$TMP/list-empty.json")"

# 2. Unapproved PR → invisible (no label, no review).
echo '[{"number":101,"title":"x","headRefName":"issue-101","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-unapproved.json"
assert_contains "unapproved PR is invisible" "no approved PRs" "$(run_watcher "$TMP/list-unapproved.json")"

# 3. Stranger-applied label → label removed, PR skipped.
echo '[{"number":110,"title":"x","headRefName":"issue-110","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-stranger.json"
echo '[{"event":"labeled","label":{"name":"agent-merge"},"actor":{"login":"someone-else"}}]' >"$TMP/events-stranger.json"
export GH_EVENTS_FIXTURE="$TMP/events-stranger.json"
assert_contains "stranger-applied label rejected" "was not applied by hluhovskyi" "$(run_watcher "$TMP/list-stranger.json")"
export GH_EVENTS_FIXTURE="$DEFAULT_EVENTS"

# 4. Doc-only happy path: approved + ready-to-merge → gh pr ready + gh pr merge --auto.
echo '[{"number":103,"title":"x","headRefName":"issue-103","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"docs/foo.md"}]}]' >"$TMP/list-ready.json"
: >"$GH_LOG"
out="$(run_watcher "$TMP/list-ready.json")"
assert_contains "ready-to-merge dispatch" "ready+auto-merge enabled" "$out"
if grep -q "pr ready 103" "$GH_LOG" && grep -q "pr merge 103" "$GH_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ ready-to-merge calls gh pr ready + gh pr merge --auto"
else
  FAIL=$((FAIL + 1)); echo "  ✗ gh pr ready/merge not called"
fi

# 5. Successful behind-clean push → label revoked (the central post-push gate-binding).
WORKTREE_112="$FAKE_REPO/.claude/worktrees/issue-112"
git init -q "$WORKTREE_112"
echo '[{"number":112,"title":"x","headRefName":"issue-112","headRefOid":"sha112","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"BEHIND","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-behind.json"
: >"$GH_LOG"
run_watcher "$TMP/list-behind.json" >/dev/null 2>&1
if grep -q "pr edit 112 .*--remove-label agent-merge" "$GH_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ label revoked after successful watcher push"
else
  FAIL=$((FAIL + 1)); echo "  ✗ label NOT revoked (gh log follows)"
  cat "$GH_LOG" | sed 's/^/    /'
fi

# 6. needs-verify dispatch: spawn-pr-session.sh verify is invoked.
export SPAWN_LOG="$TMP/spawn.log"
echo '[{"number":104,"title":"x","headRefName":"issue-104","headRefOid":"sha","createdAt":"2026-06-02T12:00:00Z","mergeStateStatus":"CLEAN","statusCheckRollup":[{"status":"COMPLETED","conclusion":"SUCCESS"}],"labels":[{"name":"agent-merge"}],"reviews":[],"files":[{"path":"a.kt"}]}]' >"$TMP/list-verify.json"
: >"$SPAWN_LOG"
run_watcher "$TMP/list-verify.json" >/dev/null 2>&1
if grep -q "^verify 104" "$SPAWN_LOG"; then
  PASS=$((PASS + 1)); echo "  ✓ needs-verify dispatch calls spawn-pr-session verify"
else
  FAIL=$((FAIL + 1)); echo "  ✗ verify dispatch not invoked"
fi

echo
echo "=== summary: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
